package streambuted.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("SecurityConfig CORS tests")
class SecurityConfigCorsTest {

    @Test
    @DisplayName("desktop handoff CORS should allow only verified web origins")
    void handoffCors_allowsOnlyConfiguredWebOrigins() {
        CorsConfigurationSource source = config(
            "https://migueleelg0106.me,https://www.migueleelg0106.me,app://streambuted",
            "https://migueleelg0106.me,https://www.migueleelg0106.me"
        ).corsConfigurationSource();

        CorsConfiguration handoffCors = source.getCorsConfiguration(request(
            "/api/v1/auth/desktop/handoff-codes"
        ));

        assertThat(handoffCors).isNotNull();
        assertThat(handoffCors.checkOrigin("https://migueleelg0106.me")).isEqualTo("https://migueleelg0106.me");
        assertThat(handoffCors.checkOrigin("https://www.migueleelg0106.me")).isEqualTo("https://www.migueleelg0106.me");
        assertThat(handoffCors.checkOrigin("app://streambuted")).isNull();
        assertThat(handoffCors.checkOrigin("https://evil.example")).isNull();
    }

    @Test
    @DisplayName("desktop main-process endpoints should not expose browser CORS")
    void desktopMainCors_deniesBrowserOrigins() {
        CorsConfigurationSource source = config(
            "https://migueleelg0106.me,app://streambuted",
            "https://migueleelg0106.me"
        ).corsConfigurationSource();

        CorsConfiguration loginCors = source.getCorsConfiguration(request("/api/v1/auth/desktop/login"));

        assertThat(loginCors).isNotNull();
        assertThat(loginCors.checkOrigin("https://migueleelg0106.me")).isNull();
        assertThat(loginCors.checkOrigin("app://streambuted")).isNull();
    }

    @Test
    @DisplayName("CORS configuration should reject wildcard or null origins")
    void corsConfiguration_rejectsUnsafeOrigins() {
        assertThatThrownBy(() -> config("*", "https://migueleelg0106.me").corsConfigurationSource())
            .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> config("https://migueleelg0106.me,null", "https://migueleelg0106.me")
            .corsConfigurationSource())
            .isInstanceOf(IllegalStateException.class);
    }

    private SecurityConfig config(String allowedOrigins, String handoffOrigins) {
        SecurityConfig config = new SecurityConfig(mock(JwtAuthenticationFilter.class), new ObjectMapper());
        ReflectionTestUtils.setField(config, "allowedOriginsProperty", allowedOrigins);
        ReflectionTestUtils.setField(config, "desktopAuthWebAllowedOriginsProperty", handoffOrigins);
        ReflectionTestUtils.setField(config, "electronRendererOrigin", "app://streambuted");
        return config;
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        request.addHeader("Origin", "https://migueleelg0106.me");
        return request;
    }
}
