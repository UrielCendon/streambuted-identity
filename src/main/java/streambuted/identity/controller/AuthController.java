package streambuted.identity.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import streambuted.identity.dto.*;
import streambuted.identity.exception.IdentityException;
import streambuted.identity.exception.InvalidAccessTokenException;
import streambuted.identity.security.JwtProperties;
import streambuted.identity.security.AuthRateLimiter;
import streambuted.identity.security.RsaJwtKeyProvider;
import streambuted.identity.service.AuthService;
import streambuted.identity.service.oauth.GoogleOAuthMode;
import streambuted.identity.service.oauth.GoogleOAuthService;
import streambuted.identity.service.oauth.GoogleUserInfo;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

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
    private static final String GOOGLE_STATE_COOKIE_NAME = "google_oauth_state";
    private static final String GOOGLE_MODE_COOKIE_NAME = "google_oauth_mode";
    private static final String GOOGLE_OAUTH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final Environment environment;
    private final RsaJwtKeyProvider rsaJwtKeyProvider;
    private final GoogleOAuthService googleOAuthService;
    private final AuthRateLimiter authRateLimiter;

    @Value("${app.security.refresh-cookie.path:${REFRESH_COOKIE_PATH:/api/v1/auth/refresh}}")
    private String refreshCookiePath;

    @Value("${app.security.refresh-cookie.secure:${REFRESH_COOKIE_SECURE:true}}")
    private boolean refreshCookieSecure;

    /**
     * POST /api/v1/auth/register
     * Starts registration and sends a 6-digit verification code by email.
     */
    @PostMapping("/register")
    public ResponseEntity<RegistrationVerificationResponse> register(
        @Valid @RequestBody RegisterRequest request,
        HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkRegistration(servletRequest, request.email());
        log.debug("Registration attempt for email={}", request.email());
        RegistrationVerificationResponse response = authService.startRegistration(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/register/resend")
    public ResponseEntity<RegistrationVerificationResponse> resendRegistrationCode(
        @Valid @RequestBody ResendRegistrationCodeRequest request,
        HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkVerification(servletRequest, request.attemptId(), request.email());
        RegistrationVerificationResponse response = authService.resendRegistrationCode(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register/verify")
    public ResponseEntity<LoginResponse> verifyRegistration(
        @Valid @RequestBody VerifyRegistrationRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        authRateLimiter.checkVerification(servletRequest, request.attemptId(), request.email());
        LoginResponse response = authService.verifyRegistration(request);
        attachRefreshTokenCookie(servletResponse, response.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(hideRefreshToken(response));
    }

    @PostMapping("/register/cancel")
    public ResponseEntity<Void> cancelRegistration(
        @Valid @RequestBody CancelRegistrationVerificationRequest request,
        HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkVerification(servletRequest, request.attemptId(), request.email());
        authService.cancelRegistration(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/auth/login
     * Authenticates credentials and returns an access token in body.
     * Refresh token is returned via HttpOnly cookie.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        authRateLimiter.checkLogin(servletRequest, request.email());
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

    @GetMapping("/validate")
    public ResponseEntity<ValidatedTokenResponse> validateAccessToken(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(authService.validateAccessToken(extractBearerToken(authorizationHeader)));
    }

    @GetMapping("/google")
    public ResponseEntity<Void> startGoogleOAuth(
        @RequestParam(name = "mode", required = false, defaultValue = "login") String mode
    ) {
        try {
            GoogleOAuthMode oauthMode = GoogleOAuthMode.fromValue(mode);
            String state = UUID.randomUUID().toString();
            String authorizationUrl = googleOAuthService.buildAuthorizationUrl(state);

            ResponseCookie stateCookie = ResponseCookie.from(GOOGLE_STATE_COOKIE_NAME, state)
                .httpOnly(true)
                .secure(secureCookies())
                .sameSite("Lax")
                .path(GOOGLE_OAUTH_COOKIE_PATH)
                .maxAge(Duration.ofMinutes(5))
                .build();

            ResponseCookie modeCookie = ResponseCookie.from(
                    GOOGLE_MODE_COOKIE_NAME,
                    oauthMode.name().toLowerCase()
                )
                .httpOnly(true)
                .secure(secureCookies())
                .sameSite("Lax")
                .path(GOOGLE_OAUTH_COOKIE_PATH)
                .maxAge(Duration.ofMinutes(5))
                .build();

            return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authorizationUrl)
                .header(HttpHeaders.SET_COOKIE, stateCookie.toString())
                .header(HttpHeaders.SET_COOKIE, modeCookie.toString())
                .build();
        } catch (IdentityException ex) {
            log.warn("Google OAuth start failed: {}", ex.getMessage());
            return redirectToFrontend("google-error", ex.getMessage());
        }
    }

    @GetMapping({"/google/callback", "/oauth/google/callback"})
    public ResponseEntity<Void> googleCallback(
        @RequestParam(name = "code", required = false) String code,
        @RequestParam(name = "state", required = false) String state,
        @RequestParam(name = "error", required = false) String error,
        @CookieValue(name = GOOGLE_STATE_COOKIE_NAME, required = false) String stateCookie,
        @CookieValue(name = GOOGLE_MODE_COOKIE_NAME, required = false) String modeCookie,
        HttpServletResponse servletResponse
    ) {
        clearGoogleStateCookie(servletResponse);
        clearGoogleModeCookie(servletResponse);

        if (error != null && !error.isBlank()) {
            return redirectToFrontend("google-error", "Google OAuth fue cancelado o rechazado.");
        }

        try {
            GoogleOAuthMode mode = GoogleOAuthMode.fromValue(modeCookie);
            googleOAuthService.validateState(state, stateCookie);
            GoogleUserInfo googleUserInfo = googleOAuthService.exchangeCode(code);
            GoogleAuthenticationResult result = authService.authenticateWithGoogle(googleUserInfo, mode);
            LoginResponse response = result.loginResponse();
            attachRefreshTokenCookie(servletResponse, response.refreshToken());
            return redirectToFrontend(
                result.passwordSetupRequired() ? "google-password-setup" : "google-success",
                result.passwordSetupRequired()
                    ? "Completa la configuracion de contrasena para finalizar el registro con Google."
                    : "Inicio de sesion con Google completado."
            );
        } catch (IdentityException ex) {
            log.warn("Google OAuth failed: {}", ex.getMessage());
            return redirectToFrontend("google-error", ex.getMessage());
        }
    }

    @PostMapping("/password/setup")
    public ResponseEntity<Void> completeGooglePasswordSetup(
        @AuthenticationPrincipal UUID userId,
        @Valid @RequestBody SetupPasswordRequest request
    ) {
        authService.completeGooglePasswordSetup(userId, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/auth/.well-known/jwks.json
     * Publishes the JSON Web Key Set (JWKS) used by other services to validate
     * access tokens locally (without a synchronous call per request).
     */
    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok(rsaJwtKeyProvider.jwks());
    }

    private void attachRefreshTokenCookie(HttpServletResponse servletResponse, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
            .httpOnly(true)
            .secure(secureCookies())
            .sameSite("Strict")
            .path(refreshCookiePath)
            .maxAge(Duration.ofMillis(jwtProperties.getRefreshTokenExpiryMs()))
            .build();

        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse servletResponse) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secureCookies())
            .sameSite("Strict")
            .path(refreshCookiePath)
            .maxAge(Duration.ZERO)
            .build();

        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new InvalidAccessTokenException();
        }

        String[] parts = authorizationHeader.trim().split("\\s+");
        if (parts.length != 2 || !"bearer".equalsIgnoreCase(parts[0]) || parts[1].isBlank()) {
            throw new InvalidAccessTokenException();
        }

        return parts[1];
    }

    private void clearGoogleStateCookie(HttpServletResponse servletResponse) {
        ResponseCookie cookie = ResponseCookie.from(GOOGLE_STATE_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secureCookies())
            .sameSite("Lax")
            .path(GOOGLE_OAUTH_COOKIE_PATH)
            .maxAge(Duration.ZERO)
            .build();

        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearGoogleModeCookie(HttpServletResponse servletResponse) {
        ResponseCookie cookie = ResponseCookie.from(GOOGLE_MODE_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secureCookies())
            .sameSite("Lax")
            .path(GOOGLE_OAUTH_COOKIE_PATH)
            .maxAge(Duration.ZERO)
            .build();

        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseEntity<Void> redirectToFrontend(String status, String message) {
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, googleOAuthService.buildFrontendRedirect(status, message))
            .build();
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

    private boolean secureCookies() {
        return refreshCookieSecure || isProductionProfile();
    }
}
