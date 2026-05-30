package streambuted.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import streambuted.identity.dto.DesktopHandoffCodeRequest;
import streambuted.identity.dto.DesktopHandoffCodeResponse;
import streambuted.identity.dto.DesktopExchangeRequest;
import streambuted.identity.dto.LoginRequest;
import streambuted.identity.dto.LoginResponse;
import streambuted.identity.exception.DesktopAuthDisabledException;
import streambuted.identity.security.AuthRateLimiter;
import streambuted.identity.security.JwtService;
import streambuted.identity.service.AuthService;
import streambuted.identity.service.DesktopAuthService;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DesktopAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("DesktopAuthController tests")
class DesktopAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private DesktopAuthService desktopAuthService;

    @MockBean
    private AuthRateLimiter authRateLimiter;

    @MockBean
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("desktop endpoints should be disabled behind the feature flag")
    void desktopEndpoints_returnNotFoundWhenDisabled() throws Exception {
        doThrow(new DesktopAuthDisabledException()).when(desktopAuthService).ensureEnabled();

        mockMvc.perform(post("/api/v1/auth/desktop/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("listener@example.com", "Secure1!"))))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/auth/desktop/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"refresh-token\"}"))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/auth/desktop/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"refresh-token\"}"))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/auth/desktop/handoff-codes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DesktopHandoffCodeRequest(
                    "abcdefghijklmnopqrstuvwxyz012345",
                    "streambuted://auth/callback"
                ))))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/auth/desktop/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DesktopExchangeRequest(
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ012345",
                    "abcdefghijklmnopqrstuvwxyz012345"
                ))))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("desktop login should return refresh token in JSON and not emit cookies")
    void desktopLogin_returnsRefreshTokenInJson() throws Exception {
        LoginRequest request = new LoginRequest("listener@example.com", "Secure1!");
        when(authService.login(ArgumentMatchers.any(LoginRequest.class)))
            .thenReturn(new LoginResponse("access-token", "refresh-token", "listener", 900));

        mockMvc.perform(post("/api/v1/auth/desktop/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("desktop handoff should require a resolved Bearer principal")
    void desktopHandoff_requiresBearerPrincipal() throws Exception {
        DesktopHandoffCodeRequest request = new DesktopHandoffCodeRequest(
            "desktop-state",
            "streambuted://auth/callback"
        );

        mockMvc.perform(post("/api/v1/auth/desktop/handoff-codes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("desktop handoff should create a one-time code for authenticated web sessions")
    void desktopHandoff_returnsOneTimeCode() throws Exception {
        UUID userId = UUID.randomUUID();
        DesktopHandoffCodeRequest request = new DesktopHandoffCodeRequest(
            "desktop-state",
            "streambuted://auth/callback"
        );
        when(desktopAuthService.createHandoffCode(
                ArgumentMatchers.eq(userId),
                ArgumentMatchers.any(DesktopHandoffCodeRequest.class)
            ))
            .thenReturn(new DesktopHandoffCodeResponse("handoff-code", "desktop-state", 300));

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(userId, null, List.of())
        );

        mockMvc.perform(post("/api/v1/auth/desktop/handoff-codes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("handoff-code"))
            .andExpect(jsonPath("$.state").value("desktop-state"))
            .andExpect(jsonPath("$.expiresIn").value(300));

        verify(authRateLimiter).checkDesktopHandoff(ArgumentMatchers.any(), ArgumentMatchers.eq(userId));
    }

    @Test
    @DisplayName("desktop exchange should rate limit and return a desktop session")
    void desktopExchange_returnsDesktopSession() throws Exception {
        DesktopExchangeRequest request = new DesktopExchangeRequest("handoff-code", "desktop-state");
        when(desktopAuthService.exchange(ArgumentMatchers.any(DesktopExchangeRequest.class)))
            .thenReturn(new LoginResponse("access-token", "refresh-token", "listener", 900));

        mockMvc.perform(post("/api/v1/auth/desktop/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"));

        verify(authRateLimiter).checkDesktopExchange(ArgumentMatchers.any());
    }
}
