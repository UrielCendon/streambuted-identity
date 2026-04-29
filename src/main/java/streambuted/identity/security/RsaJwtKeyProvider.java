package streambuted.identity.security;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Provides the RSA key material used to sign JWTs (private key) and to expose
 * public keys via JWKS for local token validation by other microservices.
 */
@Component
@Slf4j
public class RsaJwtKeyProvider {

    @Getter
    private final PrivateKey privateKey;

    @Getter
    private final RSAPublicKey publicKey;

    @Getter
    private final String keyId;

    public RsaJwtKeyProvider(JwtProperties jwtProperties) {
        Objects.requireNonNull(jwtProperties, "jwtProperties");

        KeyPair keyPair = loadOrGenerateKeyPair(jwtProperties);
        this.privateKey = keyPair.getPrivate();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();

        if (jwtProperties.getKeyId() != null && !jwtProperties.getKeyId().isBlank()) {
            this.keyId = jwtProperties.getKeyId().trim();
        } else {
            this.keyId = computeRsaJwkThumbprintKid(this.publicKey);
        }
    }

    public Map<String, Object> jwk() {
        return Map.of(
            "kty", "RSA",
            "use", "sig",
            "alg", "RS256",
            "kid", keyId,
            "n", base64UrlUnsigned(publicKey.getModulus()),
            "e", base64UrlUnsigned(publicKey.getPublicExponent())
        );
    }

    public Map<String, Object> jwks() {
        return Map.of("keys", new Object[] { jwk() });
    }

    // ── Loading / Generation ───────────────────────────────────────────────

    private static KeyPair loadOrGenerateKeyPair(JwtProperties jwtProperties) {
        try {
            KeyPair loaded = tryLoadConfiguredKeyPair(jwtProperties);
            if (loaded != null) {
                return loaded;
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load configured RSA keys for JWT", ex);
        }

        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair generated = kpg.generateKeyPair();
            log.warn("JWT RSA keys not configured; generated ephemeral RSA key pair. Tokens will be invalid after restart.");
            return generated;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key pair for JWT", ex);
        }
    }

    private static KeyPair tryLoadConfiguredKeyPair(JwtProperties jwtProperties) throws Exception {
        byte[] privateDer = firstNonBlankDer(
            jwtProperties.getRsaPrivateKeyBase64(),
            jwtProperties.getRsaPrivateKeyPem(),
            "PRIVATE KEY"
        );

        if (privateDer == null) {
            return null;
        }

        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privateDer));

        byte[] publicDer = firstNonBlankDer(
            jwtProperties.getRsaPublicKeyBase64(),
            jwtProperties.getRsaPublicKeyPem(),
            "PUBLIC KEY"
        );

        PublicKey publicKey;
        if (publicDer != null) {
            publicKey = kf.generatePublic(new X509EncodedKeySpec(publicDer));
        } else if (privateKey instanceof RSAPrivateCrtKey rsaPrivateCrtKey) {
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(
                rsaPrivateCrtKey.getModulus(),
                rsaPrivateCrtKey.getPublicExponent()
            );
            publicKey = kf.generatePublic(publicKeySpec);
        } else {
            throw new IllegalStateException("JWT RSA public key is missing and could not be derived from the private key.");
        }

        return new KeyPair(publicKey, privateKey);
    }

    private static byte[] firstNonBlankDer(String base64Value, String pemValue, String pemType) {
        if (base64Value != null && !base64Value.isBlank()) {
            return Base64.getDecoder().decode(base64Value.trim());
        }
        if (pemValue != null && !pemValue.isBlank()) {
            return decodePemToDer(pemValue, pemType);
        }
        return null;
    }

    private static byte[] decodePemToDer(String pem, String pemType) {
        String header = "-----BEGIN " + pemType + "-----";
        String footer = "-----END " + pemType + "-----";

        String normalized = pem.replace("\\r", "").trim();
        int headerIndex = normalized.indexOf(header);
        int footerIndex = normalized.indexOf(footer);
        if (headerIndex >= 0 && footerIndex > headerIndex) {
            normalized = normalized.substring(headerIndex + header.length(), footerIndex);
        }

        String base64 = normalized.replaceAll("\\s+", "");
        return Base64.getDecoder().decode(base64);
    }

    // ── JWKS helpers ───────────────────────────────────────────────────────

    private static String base64UrlUnsigned(BigInteger bigInteger) {
        byte[] bytes = bigInteger.toByteArray();
        // Ensure unsigned big-endian representation (strip leading 0 if present)
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] tmp = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, tmp, 0, tmp.length);
            bytes = tmp;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Computes RFC 7638 JWK thumbprint for RSA keys and uses it as kid.
     * Canonical JSON: {"e":"...","kty":"RSA","n":"..."}
     */
    private static String computeRsaJwkThumbprintKid(RSAPublicKey publicKey) {
        try {
            String e = base64UrlUnsigned(publicKey.getPublicExponent());
            String n = base64UrlUnsigned(publicKey.getModulus());
            String canonical = "{\"e\":\"" + e + "\",\"kty\":\"RSA\",\"n\":\"" + n + "\"}";

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute RSA JWK thumbprint kid", ex);
        }
    }
}
