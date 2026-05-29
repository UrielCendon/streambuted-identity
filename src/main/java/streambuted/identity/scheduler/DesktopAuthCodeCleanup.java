package streambuted.identity.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import streambuted.identity.service.DesktopAuthService;

import java.time.Clock;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class DesktopAuthCodeCleanup {

    private static final Duration RETENTION = Duration.ofHours(1);

    private final DesktopAuthService desktopAuthService;
    private final Clock clock;

    @Scheduled(cron = "0 */15 * * * *")
    public void cleanupExpiredCodes() {
        try {
            desktopAuthService.cleanupExpiredOrUsed(clock.instant().minus(RETENTION));
        } catch (RuntimeException ex) {
            log.warn("Desktop auth code cleanup failed: {}", ex.getMessage());
        }
    }
}
