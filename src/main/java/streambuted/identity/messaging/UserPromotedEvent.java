package streambuted.identity.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to the identity.events exchange with routing key "user.promoted"
 * whenever a listener account is irreversibly promoted to artist.
 *
 * Downstream consumers (e.g. Catalog Service) can react to this event to
 * create artist profiles, provision storage buckets, etc.
 *
 * @param eventId     Unique identifier for idempotent processing by consumers.
 * @param userId      UUID of the promoted user account.
 * @param email       Email address of the promoted user.
 * @param username    Public username at the time of promotion.
 * @param previousRole Role before promotion (always "listener").
 * @param newRole     Role after promotion (always "artist").
 * @param promotedAt  UTC timestamp of the promotion.
 */
public record UserPromotedEvent(
    UUID    eventId,
    UUID    userId,
    String  email,
    String  username,
    String  previousRole,
    String  newRole,
    Instant promotedAt
) {
    /** Factory method that auto-generates a random eventId and timestamps. */
    public static UserPromotedEvent of(UUID userId, String email, String username) {
        return new UserPromotedEvent(
            UUID.randomUUID(),
            userId,
            email,
            username,
            "listener",
            "artist",
            Instant.now()
        );
    }
}
