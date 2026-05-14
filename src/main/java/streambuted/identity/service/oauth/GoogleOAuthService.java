package streambuted.identity.service.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import streambuted.identity.config.GoogleOAuthProperties;
import streambuted.identity.exception.GoogleAuthenticationException;

@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private static final String GOOGLE_SCOPE = "openid email profile";

    private final GoogleOAuthProperties properties;
    private final GoogleOAuthClient googleOAuthClient;

    public String buildAuthorizationUrl(String state) {
        assertConfigured();

        return UriComponentsBuilder
            .fromHttpUrl(properties.getAuthorizationUrl())
            .queryParam("client_id", properties.getClientId())
            .queryParam("redirect_uri", properties.getCallbackUrl())
            .queryParam("response_type", "code")
            .queryParam("scope", GOOGLE_SCOPE)
            .queryParam("state", state)
            .queryParam("prompt", "select_account")
            .build()
            .toUriString();
    }

    public GoogleUserInfo exchangeCode(String code) {
        assertConfigured();

        if (code == null || code.isBlank()) {
            throw new GoogleAuthenticationException("Google authorization code is missing.");
        }

        return googleOAuthClient.exchangeCode(code, properties);
    }

    public void validateState(String requestState, String cookieState) {
        if (requestState == null || requestState.isBlank() ||
            cookieState == null || cookieState.isBlank() ||
            !requestState.equals(cookieState)) {
            throw new GoogleAuthenticationException("Google OAuth state is invalid.");
        }
    }

    public String buildFrontendRedirect(String status, String message) {
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromUriString(properties.getFrontendUrl())
            .queryParam("oauth", status);

        if (message != null && !message.isBlank()) {
            builder.queryParam("message", message);
        }

        return builder.build().toUriString();
    }

    private void assertConfigured() {
        if (properties.getClientId() == null || properties.getClientId().isBlank() ||
            properties.getClientSecret() == null || properties.getClientSecret().isBlank() ||
            properties.getCallbackUrl() == null || properties.getCallbackUrl().isBlank()) {
            throw new GoogleAuthenticationException("Google OAuth is not configured.");
        }
    }
}
