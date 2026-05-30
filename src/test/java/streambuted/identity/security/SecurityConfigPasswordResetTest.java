package streambuted.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import streambuted.identity.controller.AuthController;
import streambuted.identity.dto.RegistrationVerificationResponse;
import streambuted.identity.service.AuthService;
import streambuted.identity.service.oauth.GoogleOAuthService;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@DisplayName("SecurityConfig password reset routes")
class SecurityConfigPasswordResetTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtProperties jwtProperties;

    @MockBean
    private Environment environment;

    @MockBean
    private RsaJwtKeyProvider rsaJwtKeyProvider;

    @MockBean
    private GoogleOAuthService googleOAuthService;

    @MockBean
    private AuthRateLimiter authRateLimiter;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("allows starting password reset without a bearer token")
    void startPasswordReset_isPublic() throws Exception {
        when(authService.startPasswordReset(any())).thenReturn(
            new RegistrationVerificationResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "listener@example.com",
                "pending",
                900,
                "Recovery code sent."
            )
        );

        mockMvc.perform(post("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("email", "listener@example.com")
                )))
            .andExpect(status().isAccepted());
    }
}
