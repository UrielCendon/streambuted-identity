package streambuted.identity.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to the identity.events exchange with routing key "user.logged-in"
 * whenever a user starts a new authenticated session.
 *
 * @param eventId    Unique identifier for idempotent processing by consumers.
 * @param userId     UUID of the authenticated user account.
 * @param occurredAt UTC timestamp of the login.
 */
public record UserLoggedInEvent(
    UUID eventId,
    UUID userId,
    Instant occurredAt
) {
    /** Factory method that auto-generates a random eventId and timestamp. */
    public static UserLoggedInEvent of(UUID userId) {
        return new UserLoggedInEvent(
            UUID.randomUUID(),
            userId,
            Instant.now()
        );
    }
}
