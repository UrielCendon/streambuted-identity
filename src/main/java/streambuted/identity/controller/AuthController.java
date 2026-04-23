package streambuted.identity.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import streambuted.identity.dto.*;
import streambuted.identity.security.JwtProperties;
import streambuted.identity.service.AuthService;

import java.time.Duration;

/**
 * Handles public authentication endpoints under /api/v1/auth.
 * No JWT is required for any endpoint in this controller.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final Environment environment;

    @Value("${app.security.refresh-cookie.path:/api/v1/auth/refresh}")
    private String refreshCookiePath;

    /**
     * POST /api/v1/auth/register
     * Creates a new account with role LISTENER and returns a token pair.
     */
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.debug("Registration attempt for email={}", request.email());
        LoginResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/auth/login
     * Authenticates credentials and returns an access token in body.
     * Refresh token is returned via HttpOnly cookie.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletResponse servletResponse
    ) {
        log.debug("Login attempt for email={}", request.email());
        LoginResponse response = authService.login(request);
        attachRefreshTokenCookie(servletResponse, response.refreshToken());
        return ResponseEntity.ok(hideRefreshToken(response));
    }

    /**
     * POST /api/v1/auth/refresh
     * Exchanges refresh cookie for a new access token + rotated refresh cookie.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
        @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
        HttpServletResponse servletResponse
    ) {
        LoginResponse response = authService.refresh(refreshToken);
        attachRefreshTokenCookie(servletResponse, response.refreshToken());
        return ResponseEntity.ok(hideRefreshToken(response));
    }

    /**
     * POST /api/v1/auth/logout
     * Invalidates refresh token in persistence and clears browser cookie.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
        HttpServletResponse servletResponse
    ) {
        authService.logout(refreshToken);
        clearRefreshTokenCookie(servletResponse);
        return ResponseEntity.noContent().build();
    }

    private void attachRefreshTokenCookie(HttpServletResponse servletResponse, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
            .httpOnly(true)
            .secure(isProductionProfile())
            .sameSite("Strict")
            .path(refreshCookiePath)
            .maxAge(Duration.ofMillis(jwtProperties.getRefreshTokenExpiryMs()))
            .build();

        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse servletResponse) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(isProductionProfile())
            .sameSite("Strict")
            .path(refreshCookiePath)
            .maxAge(Duration.ZERO)
            .build();

        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private LoginResponse hideRefreshToken(LoginResponse response) {
        return new LoginResponse(
            response.accessToken(),
            null,
            response.role(),
            response.expiresIn()
        );
    }

    private boolean isProductionProfile() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }
}
