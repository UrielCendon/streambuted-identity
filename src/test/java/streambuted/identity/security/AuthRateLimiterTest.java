package streambuted.identity.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import streambuted.identity.exception.RateLimitExceededException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuthRateLimiter tests")
class AuthRateLimiterTest {

    private final AuthRateLimiter limiter = new AuthRateLimiter(
        Clock.fixed(Instant.parse("2026-05-17T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    @DisplayName("should block login attempts after the configured limit")
    void loginLimit_blocksAfterThreshold() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");

        for (int i = 0; i < 10; i++) {
            limiter.checkLogin(request, "listener@example.com");
        }

        assertThatThrownBy(() -> limiter.checkLogin(request, "listener@example.com"))
            .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("should block verification attempts after the configured limit")
    void verificationLimit_blocksAfterThreshold() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.20");
        UUID attemptId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            limiter.checkVerification(request, attemptId, "listener@example.com");
        }

        assertThatThrownBy(() -> limiter.checkVerification(request, attemptId, "listener@example.com"))
            .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("should block desktop handoff attempts after the configured limit")
    void desktopHandoffLimit_blocksAfterThreshold() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.30");
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            limiter.checkDesktopHandoff(request, userId);
        }

        assertThatThrownBy(() -> limiter.checkDesktopHandoff(request, userId))
            .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("should block desktop exchange attempts after the configured limit")
    void desktopExchangeLimit_blocksAfterThreshold() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.40");

        for (int i = 0; i < 10; i++) {
            limiter.checkDesktopExchange(request);
        }

        assertThatThrownBy(() -> limiter.checkDesktopExchange(request))
            .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("should ignore forwarded headers unless trusted proxy mode is enabled")
    void clientIp_ignoresForwardedHeadersByDefault() {
        AuthRateLimiter defaultLimiter = new AuthRateLimiter(
            Clock.fixed(Instant.parse("2026-05-17T00:00:00Z"), ZoneOffset.UTC),
            false
        );

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("198.51.100.10");
            request.addHeader("X-Forwarded-For", "203.0.113." + i);
            defaultLimiter.checkDesktopExchange(request);
        }

        MockHttpServletRequest blockedRequest = new MockHttpServletRequest();
        blockedRequest.setRemoteAddr("198.51.100.10");
        blockedRequest.addHeader("X-Forwarded-For", "203.0.113.99");

        assertThatThrownBy(() -> defaultLimiter.checkDesktopExchange(blockedRequest))
            .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("should use trusted real IP header only when trusted proxy mode is enabled")
    void clientIp_usesRealIpWhenTrustedProxyModeIsEnabled() {
        AuthRateLimiter trustedLimiter = new AuthRateLimiter(
            Clock.fixed(Instant.parse("2026-05-17T00:00:00Z"), ZoneOffset.UTC),
            true
        );

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("172.18.0." + i);
            request.addHeader("X-Real-IP", "203.0.113.50");
            trustedLimiter.checkDesktopExchange(request);
        }

        MockHttpServletRequest blockedRequest = new MockHttpServletRequest();
        blockedRequest.setRemoteAddr("172.18.0.99");
        blockedRequest.addHeader("X-Real-IP", "203.0.113.50");

        assertThatThrownBy(() -> trustedLimiter.checkDesktopExchange(blockedRequest))
            .isInstanceOf(RateLimitExceededException.class);
    }
}
