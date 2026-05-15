package streambuted.identity.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds JWT configuration from application.yml (prefix: jwt).
 * Values are injected from environment variables via ${...} placeholders
 * defined in the YAML. The secret itself is never hardcoded.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    /** Token issuer (iss claim). Should be stable across environments. */
    private String issuer;

    /** Optional RSA private key (PKCS#8) provided as PEM or base64-encoded DER. */
    private String rsaPrivateKeyPem;
    private String rsaPrivateKeyBase64;

    /** Optional RSA public key (X.509) provided as PEM or base64-encoded DER. */
    private String rsaPublicKeyPem;
    private String rsaPublicKeyBase64;

    /** Optional key id to use in JWT header and JWKS (kid). If blank, derived from public key. */
    private String keyId;

    /** Access token lifetime in milliseconds (default 15 min = 900_000 ms). */
    private long accessTokenExpiryMs;

    /** Refresh token lifetime in milliseconds (default 7 days = 604_800_000 ms). */
    private long refreshTokenExpiryMs;
}
