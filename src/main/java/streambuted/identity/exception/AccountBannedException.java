package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;

public class AccountBannedException extends IdentityException {

    private final Instant bannedUntil;
    private final String banReason;

    public AccountBannedException(Instant bannedUntil, String banReason) {
        super(buildMessage(bannedUntil), HttpStatus.FORBIDDEN);
        this.bannedUntil = bannedUntil;
        this.banReason = banReason;
    }

    public String getBanType() {
        return bannedUntil == null ? "PERMANENT" : "TEMPORARY";
    }

    public Instant getBannedUntil() {
        return bannedUntil;
    }

    public String getBanReason() {
        return banReason;
    }

    public long getRemainingSeconds() {
        if (bannedUntil == null) {
            return 0L;
        }

        return Math.max(0L, Duration.between(Instant.now(), bannedUntil).getSeconds());
    }

    private static String buildMessage(Instant bannedUntil) {
        if (bannedUntil == null) {
            return "La cuenta se encuentra suspendida permanentemente.";
        }

        return "La cuenta se encuentra suspendida. Se reactivará en "
            + formatRemainingTime(remainingSecondsUntil(bannedUntil))
            + ".";
    }

    private static long remainingSecondsUntil(Instant bannedUntil) {
        return Math.max(0L, Duration.between(Instant.now(), bannedUntil).getSeconds());
    }

    private static String formatRemainingTime(long seconds) {
        long minutes = Math.max(1L, (long) Math.ceil(seconds / 60.0));
        if (minutes < 60L) {
            return minutes == 1L ? "1 minuto" : minutes + " minutos";
        }

        long hours = minutes / 60L;
        long remainingMinutes = minutes % 60L;
        if (hours < 24L) {
            String hourText = hours == 1L ? "1 hora" : hours + " horas";
            if (remainingMinutes == 0L) {
                return hourText;
            }
            String minuteText = remainingMinutes == 1L ? "1 minuto" : remainingMinutes + " minutos";
            return hourText + " y " + minuteText;
        }

        long days = hours / 24L;
        long remainingHours = hours % 24L;
        String dayText = days == 1L ? "1 dia" : days + " dias";
        if (remainingHours == 0L) {
            return dayText;
        }
        String hourText = remainingHours == 1L ? "1 hora" : remainingHours + " horas";
        return dayText + " y " + hourText;
    }
}
