package streambuted.identity.dto;

import java.time.Instant;

/**
 * Error envelope for login attempts made by banned accounts.
 * Keeps the standard error fields and adds ban metadata for the UI.
 */
public record AccountBannedErrorResponse(
    String error,
    String code,
    String message,
    int statusCode,
    Instant timestamp,
    String banType,
    Instant bannedUntil,
    long remainingSeconds
) {

    public static AccountBannedErrorResponse of(
        String message,
        String banType,
        Instant bannedUntil,
        long remainingSeconds
    ) {
        return new AccountBannedErrorResponse(
            "AccountBannedException",
            "ACCOUNT_BANNED",
            message,
            403,
            Instant.now(),
            banType,
            bannedUntil,
            remainingSeconds
        );
    }
}
