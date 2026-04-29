package streambuted.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import streambuted.identity.dto.LoginRequest;
import streambuted.identity.dto.LoginResponse;
import streambuted.identity.security.JwtProperties;
import streambuted.identity.security.JwtService;
import streambuted.identity.service.AuthService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController Cookie Contract Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtProperties jwtProperties;

    @MockBean
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        when(jwtProperties.getRefreshTokenExpiryMs()).thenReturn(604_800_000L);
    }

    @Test
    @DisplayName("login should emit refresh token in HttpOnly cookie and omit it from JSON body")
    void login_setsRefreshCookieAndHidesTokenInBody() throws Exception {
        LoginRequest request = new LoginRequest("listener@streambuted.com", "SecurePass1!");
        LoginResponse serviceResponse = new LoginResponse(
            "access-token-value",
            "refresh-token-value",
            "listener",
            900L
        );

        when(authService.login(ArgumentMatchers.any(LoginRequest.class))).thenReturn(serviceResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access-token-value"))
            .andExpect(jsonPath("$.role").value("listener"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=refresh-token-value")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth/refresh")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=604800")));
    }

    @Test
    @DisplayName("refresh should read refresh token from cookie and rotate cookie")
    void refresh_readsCookieAndRotatesCookie() throws Exception {
        LoginResponse serviceResponse = new LoginResponse(
            "new-access-token",
            "new-refresh-token",
            "listener",
            900L
        );

        when(authService.refresh("incoming-refresh-token")).thenReturn(serviceResponse);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie("refresh_token", "incoming-refresh-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("new-access-token"))
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=new-refresh-token")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));

        verify(authService).refresh("incoming-refresh-token");
    }

    @Test
    @DisplayName("logout should clear refresh token cookie and invalidate persisted token")
    void logout_clearsCookieAndInvalidatesToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                .cookie(new Cookie("refresh_token", "refresh-token-value")))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth/refresh")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));

        verify(authService).logout("refresh-token-value");
    }
}
