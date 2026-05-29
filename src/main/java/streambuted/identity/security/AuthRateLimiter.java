package streambuted.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import streambuted.identity.exception.RateLimitExceededException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimiter {

    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(1);
    private static final int LOGIN_LIMIT = 10;
    private static final Duration REGISTRATION_WINDOW = Duration.ofMinutes(15);
    private static final int REGISTRATION_LIMIT = 5;
    private static final Duration VERIFICATION_WINDOW = Duration.ofMinutes(15);
    private static final int VERIFICATION_LIMIT = 5;
    private static final Duration DESKTOP_HANDOFF_WINDOW = Duration.ofMinutes(1);
    private static final int DESKTOP_HANDOFF_LIMIT = 5;
    private static final Duration DESKTOP_EXCHANGE_WINDOW = Duration.ofMinutes(1);
    private static final int DESKTOP_EXCHANGE_LIMIT = 10;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final boolean trustForwardedHeaders;

    public AuthRateLimiter(
        @Value("${app.security.trust-forwarded-headers:false}") boolean trustForwardedHeaders
    ) {
        this(Clock.systemUTC(), trustForwardedHeaders);
    }

    AuthRateLimiter(Clock clock) {
        this(clock, false);
    }

    AuthRateLimiter(Clock clock, boolean trustForwardedHeaders) {
        this.clock = clock;
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public void checkLogin(HttpServletRequest request, String email) {
        consume("login", clientFingerprint(request, normalize(email)), LOGIN_LIMIT, LOGIN_WINDOW);
    }

    public void checkRegistration(HttpServletRequest request, String email) {
        consume("registration", clientFingerprint(request, normalize(email)), REGISTRATION_LIMIT, REGISTRATION_WINDOW);
    }

    public void checkVerification(HttpServletRequest request, UUID attemptId, String email) {
        String subject = attemptId == null ? normalize(email) : attemptId.toString();
        consume("verification", clientFingerprint(request, subject), VERIFICATION_LIMIT, VERIFICATION_WINDOW);
    }

    public void checkDesktopHandoff(HttpServletRequest request, UUID userId) {
        String subject = userId == null ? "anonymous" : userId.toString();
        consume("desktop-handoff", clientFingerprint(request, subject), DESKTOP_HANDOFF_LIMIT, DESKTOP_HANDOFF_WINDOW);
    }

    public void checkDesktopExchange(HttpServletRequest request) {
        consume("desktop-exchange", clientFingerprint(request, "exchange"), DESKTOP_EXCHANGE_LIMIT, DESKTOP_EXCHANGE_WINDOW);
    }

    private void consume(String scope, String fingerprint, int limit, Duration window) {
        String key = scope + ":" + fingerprint;
        Instant now = clock.instant();
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(now, 0));

        synchronized (bucket) {
            if (!now.isBefore(bucket.windowStart.plus(window))) {
                bucket.windowStart = now;
                bucket.count = 0;
            }

            bucket.count++;
            if (bucket.count > limit) {
                throw new RateLimitExceededException();
            }
        }
    }

    private String clientFingerprint(HttpServletRequest request, String subject) {
        return sha256(clientIp(request) + "|" + subject);
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String realIp = sanitizeHeaderIp(request.getHeader("X-Real-IP"));
            if (realIp != null) {
                return realIp;
            }

            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String firstForwardedIp = sanitizeHeaderIp(forwardedFor.split(",", 2)[0]);
                if (firstForwardedIp != null) {
                    return firstForwardedIp;
                }
            }
        }

        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private String sanitizeHeaderIp(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        if (
            trimmedValue.isBlank() ||
            trimmedValue.length() > 64 ||
            trimmedValue.chars().anyMatch(character -> character <= 31 || character == 127)
        ) {
            return null;
        }

        return trimmedValue;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static final class Bucket {
        private Instant windowStart;
        private int count;

        private Bucket(Instant windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
