package streambuted.identity.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import streambuted.identity.dto.*;
import streambuted.identity.security.AuthRateLimiter;
import streambuted.identity.service.AuthService;
import streambuted.identity.service.DesktopAuthService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/desktop")
@RequiredArgsConstructor
public class DesktopAuthController {

    private final AuthService authService;
    private final DesktopAuthService desktopAuthService;
    private final AuthRateLimiter authRateLimiter;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest
    ) {
        desktopAuthService.ensureEnabled();
        authRateLimiter.checkLogin(servletRequest, request.email());
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
        @Valid @RequestBody DesktopRefreshRequest request
    ) {
        desktopAuthService.ensureEnabled();
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @Valid @RequestBody DesktopLogoutRequest request
    ) {
        desktopAuthService.ensureEnabled();
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/handoff-codes")
    public ResponseEntity<DesktopHandoffCodeResponse> createHandoffCode(
        @AuthenticationPrincipal UUID userId,
        @Valid @RequestBody DesktopHandoffCodeRequest request,
        HttpServletRequest servletRequest
    ) {
        desktopAuthService.ensureEnabled();
        if (userId == null) {
            throw new AuthenticationCredentialsNotFoundException("Missing Bearer token.");
        }
        authRateLimiter.checkDesktopHandoff(servletRequest, userId);
        return ResponseEntity.ok(desktopAuthService.createHandoffCode(userId, request));
    }

    @PostMapping("/exchange")
    public ResponseEntity<LoginResponse> exchange(
        @Valid @RequestBody DesktopExchangeRequest request,
        HttpServletRequest servletRequest
    ) {
        desktopAuthService.ensureEnabled();
        authRateLimiter.checkDesktopExchange(servletRequest);
        return ResponseEntity.ok(desktopAuthService.exchange(request));
    }
}
