package streambuted.identity.domain;

/**
 * Processing state for the transactional outbox.
 */
public enum OutboxStatus {
    PENDING,
    PROCESSED,
    FAILED
}