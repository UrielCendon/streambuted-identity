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
}
