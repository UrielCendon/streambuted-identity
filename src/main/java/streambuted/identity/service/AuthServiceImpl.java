package streambuted.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import streambuted.identity.domain.*;
import streambuted.identity.dto.*;
import streambuted.identity.exception.*;
import streambuted.identity.repository.*;
import streambuted.identity.security.JwtProperties;
import streambuted.identity.security.JwtService;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles user registration, login, and refresh-token rotation.
 *
 * Token rotation strategy: on every /auth/refresh call the old refresh token
 * is revoked and a new one is issued. This limits the blast radius if a
 * refresh token is stolen, without requiring server-side session state.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserAccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder        passwordEncoder;
    private final JwtService             jwtService;
    private final JwtProperties          jwtProperties;

    // ── Registration ──────────────────────────────────────────────────────────

    @Override
    public LoginResponse register(RegisterRequest request) {
        if (accountRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        // Build and persist the account entity
        UserAccountEntity account = UserAccountEntity.builder()
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .role(Role.LISTENER)
            .isActive(true)
            .build();

        // Build the profile with the provided username
        UserProfileEntity profile = UserProfileEntity.builder()
            .account(account)
            .username(request.username())
            .build();

        account.setProfile(profile);

        accountRepository.save(account);
        log.info("Registered new user: email={}, id={}", account.getEmail(), account.getId());

        return buildTokenPair(account);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Override
    public LoginResponse login(LoginRequest request) {
        UserAccountEntity account = accountRepository.findByEmail(request.email())
            .orElseThrow(InvalidCredentialsException::new);

        if (!account.isActive()) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        log.info("Successful login for userId={}", account.getId());
        return buildTokenPair(account);
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Override
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        String tokenValue = refreshToken.trim();
        RefreshTokenEntity storedToken = refreshTokenRepository
            .findByTokenValue(tokenValue)
            .orElseThrow(InvalidRefreshTokenException::new);

        if (!storedToken.isValid()) {
            throw new InvalidRefreshTokenException();
        }

        // Revoke the used token — prevents replay attacks (token rotation)
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        UserAccountEntity account = storedToken.getAccount();
        log.info("Refresh token rotated for userId={}", account.getId());
        return buildTokenPair(account);
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        String tokenValue = refreshToken.trim();
        long deletedRows = refreshTokenRepository.deleteByTokenValue(tokenValue);
        log.info("Logout completed. Invalidated refresh token rows={}", deletedRows);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Issues a fresh JWT + refresh token pair and persists the refresh token.
     * Called after both successful login and refresh operations.
     */
    private LoginResponse buildTokenPair(UserAccountEntity account) {
        String accessToken   = jwtService.generateAccessToken(account);
        String refreshValue  = UUID.randomUUID().toString();
        Instant expiresAt    = Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiryMs());

        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
            .account(account)
            .tokenValue(refreshValue)
            .expiresAt(expiresAt)
            .isRevoked(false)
            .build();

        refreshTokenRepository.save(refreshToken);

        return new LoginResponse(
            accessToken,
            refreshValue,
            account.getRole().name().toLowerCase(),
            jwtService.getAccessTokenExpirySeconds()
        );
    }
}
