package streambuted.identity.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GoogleOAuthProperties")
class GoogleOAuthPropertiesTest {

    @Test
    @DisplayName("should fall back to environment variables when values are blank")
    void applyEnvironmentFallbacks_usesEnvironmentVariables() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("GOOGLE_CLIENT_ID", "client-id")
            .withProperty("GOOGLE_CLIENT_SECRET", "client-secret")
            .withProperty("GOOGLE_CALLBACK_URL", "http://localhost/api/v1/auth/google/callback")
            .withProperty("FRONTEND_URL", "http://localhost:5173");

        GoogleOAuthProperties properties = new GoogleOAuthProperties(environment);
        properties.setClientId("");
        properties.setClientSecret("");
        properties.setCallbackUrl("");
        properties.setFrontendUrl("");

        properties.applyEnvironmentFallbacks();

        assertThat(properties.getClientId()).isEqualTo("client-id");
        assertThat(properties.getClientSecret()).isEqualTo("client-secret");
        assertThat(properties.getCallbackUrl()).isEqualTo("http://localhost/api/v1/auth/google/callback");
        assertThat(properties.getFrontendUrl()).isEqualTo("http://localhost:5173");
    }

    @Test
    @DisplayName("should preserve explicitly configured values")
    void applyEnvironmentFallbacks_preservesConfiguredValues() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("GOOGLE_CLIENT_ID", "other-client-id")
            .withProperty("GOOGLE_CLIENT_SECRET", "other-client-secret");

        GoogleOAuthProperties properties = new GoogleOAuthProperties(environment);
        properties.setClientId("configured-client-id");
        properties.setClientSecret("configured-client-secret");
        properties.setCallbackUrl("http://configured/callback");
        properties.setFrontendUrl("http://configured");

        properties.applyEnvironmentFallbacks();

        assertThat(properties.getClientId()).isEqualTo("configured-client-id");
        assertThat(properties.getClientSecret()).isEqualTo("configured-client-secret");
        assertThat(properties.getCallbackUrl()).isEqualTo("http://configured/callback");
        assertThat(properties.getFrontendUrl()).isEqualTo("http://configured");
    }
}
