package streambuted.identity.domain;

/**
 * Represents the access role assigned to a user account.
 * Role changes are irreversible through the public API (listener → artist only).
 * Admin accounts are provisioned directly in the database.
 */
public enum Role {
    LISTENER,
    ARTIST,
    ADMIN
}
