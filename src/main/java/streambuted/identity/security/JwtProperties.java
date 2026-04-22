package streambuted.identity.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds JWT configuration from application.yml (prefix: jwt).
 * Values are injected from environment variables via ${...} placeholders
 * defined in the YAML — the secret itself is never hardcoded.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    /** HMAC-SHA512 signing secret — must be at least 512 bits (64 chars). */
    private String secret;

    /** Access token lifetime in milliseconds (default 15 min = 900_000 ms). */
    private long accessTokenExpiryMs;

    /** Refresh token lifetime in milliseconds (default 7 days = 604_800_000 ms). */
    private long refreshTokenExpiryMs;
}
