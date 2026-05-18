package streambuted.identity.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Getter
@Setter
@Component
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app.google.oauth")
public class GoogleOAuthProperties {

    private final Environment environment;

    private String clientId = "";
    private String clientSecret = "";
    private String callbackUrl = "http://localhost/api/v1/auth/oauth/google/callback";
    private String frontendUrl = "http://localhost:5173";
    private String authorizationUrl = "https://accounts.google.com/o/oauth2/v2/auth";
    private String tokenUrl = "https://oauth2.googleapis.com/token";
    private String tokenInfoUrl = "https://oauth2.googleapis.com/tokeninfo";

    @PostConstruct
    void applyEnvironmentFallbacks() {
        clientId = resolve(clientId, "app.google.oauth.client-id", "GOOGLE_CLIENT_ID");
        clientSecret = resolve(clientSecret, "app.google.oauth.client-secret", "GOOGLE_CLIENT_SECRET");
        callbackUrl = resolve(callbackUrl, "app.google.oauth.callback-url", "GOOGLE_CALLBACK_URL");
        frontendUrl = resolve(frontendUrl, "app.google.oauth.frontend-url", "FRONTEND_URL");
        authorizationUrl = resolve(
            authorizationUrl,
            "app.google.oauth.authorization-url",
            "GOOGLE_AUTHORIZATION_URL"
        );
        tokenUrl = resolve(tokenUrl, "app.google.oauth.token-url", "GOOGLE_TOKEN_URL");
        tokenInfoUrl = resolve(tokenInfoUrl, "app.google.oauth.token-info-url", "GOOGLE_TOKEN_INFO_URL");
    }

    private String resolve(String currentValue, String propertyKey, String environmentKey) {
        if (StringUtils.hasText(currentValue)) {
            return currentValue;
        }

        String propertyValue = environment.getProperty(propertyKey);
        if (StringUtils.hasText(propertyValue)) {
            return propertyValue;
        }

        String environmentValue = environment.getProperty(environmentKey);
        if (StringUtils.hasText(environmentValue)) {
            return environmentValue;
        }

        return currentValue;
    }
}
