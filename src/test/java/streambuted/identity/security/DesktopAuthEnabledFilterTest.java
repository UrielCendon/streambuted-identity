package streambuted.identity.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import streambuted.identity.config.DesktopAuthProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DesktopAuthEnabledFilter tests")
class DesktopAuthEnabledFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("should block all desktop auth endpoints before body validation when disabled")
    void disabledDesktopAuth_blocksAllDesktopEndpointsBeforeValidation() throws Exception {
        List<String> paths = List.of(
            "/api/v1/auth/desktop/login",
            "/api/v1/auth/desktop/refresh",
            "/api/v1/auth/desktop/logout",
            "/api/v1/auth/desktop/handoff-codes",
            "/api/v1/auth/desktop/exchange"
        );

        for (String path : paths) {
            MockHttpServletResponse response = executeFilter(path, false);

            assertThat(response.getStatus()).isEqualTo(404);
            JsonNode body = objectMapper.readTree(response.getContentAsString());
            assertThat(body.get("error").asText()).isEqualTo("DesktopAuthDisabledException");
            assertThat(body.get("message").asText()).isEqualTo("La autenticacion desktop no esta disponible.");
            assertThat(body.get("message").asText()).doesNotContain("contrasena", "password", "state", "refresh");
        }
    }

    @Test
    @DisplayName("should continue filter chain for desktop auth endpoints when enabled")
    void enabledDesktopAuth_continuesFilterChain() throws Exception {
        MockHttpServletResponse response = executeFilter("/api/v1/auth/desktop/login", true);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("continued");
    }

    @Test
    @DisplayName("should not affect normal web auth endpoints when disabled")
    void disabledDesktopAuth_doesNotAffectWebAuthEndpoints() throws Exception {
        MockHttpServletResponse response = executeFilter("/api/v1/auth/login", false);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("continued");
    }

    private MockHttpServletResponse executeFilter(String path, boolean enabled) throws Exception {
        DesktopAuthProperties properties = new DesktopAuthProperties();
        properties.setEnabled(enabled);
        DesktopAuthEnabledFilter filter = new DesktopAuthEnabledFilter(properties, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setContentType("application/json");
        request.setContent("{\"email\":\"test@test.com\",\"password\":\"bad\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType("text/plain");
            servletResponse.getWriter().write("continued");
        };

        filter.doFilter(request, response, chain);
        return response;
    }
}
