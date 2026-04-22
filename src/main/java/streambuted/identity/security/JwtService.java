package streambuted.identity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import streambuted.identity.domain.UserAccountEntity;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles JWT issuance, parsing and validation.
 *
 * Algorithm : HMAC-SHA512 (HS512)
 * Access TTL : 15 min (configurable via jwt.access-token-expiry-ms)
 * Claims     : sub (userId), email, role
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private static final String CLAIM_ROLE  = "role";
    private static final String CLAIM_EMAIL = "email";

    private final JwtProperties jwtProperties;

    // ── Key derivation ────────────────────────────────────────────────────────

    /** Derives the signing key from the configured secret string. */
    private SecretKey signingKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ── Token issuance ────────────────────────────────────────────────────────

    /**
     * Issues a signed access JWT for the given user account.
     *
     * @param account the authenticated user account
     * @return signed JWT compact string
     */
    public String generateAccessToken(UserAccountEntity account) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtProperties.getAccessTokenExpiryMs());

        return Jwts.builder()
            .subject(account.getId().toString())
            .id(UUID.randomUUID().toString())
            .claim(CLAIM_EMAIL, account.getEmail())
            .claim(CLAIM_ROLE, account.getRole().name().toLowerCase())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey())
            .compact();
    }

    // ── Claims extraction ─────────────────────────────────────────────────────

    /**
     * Parses a JWT and returns its claims, or empty if the token is invalid.
     * Specific JWT parsing failures are caught and mapped to an empty result.
     */
    public Optional<Claims> extractClaims(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return Optional.of(claims);
        } catch (ExpiredJwtException | UnsupportedJwtException | MalformedJwtException | SignatureException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extracts the userId (subject) from a validated token.
     *
     * @throws IllegalArgumentException if token is invalid
     */
    public UUID extractUserId(String token) {
        return extractClaims(token)
            .map(claims -> UUID.fromString(claims.getSubject()))
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired JWT"));
    }

    /** Returns true only when the token is well-formed, signed, and not expired. */
    public boolean isTokenValid(String token) {
        return extractClaims(token).isPresent();
    }

    /** Access token lifetime in seconds — forwarded to the client as expiresIn. */
    public long getAccessTokenExpirySeconds() {
        return jwtProperties.getAccessTokenExpiryMs() / 1000;
    }
}
