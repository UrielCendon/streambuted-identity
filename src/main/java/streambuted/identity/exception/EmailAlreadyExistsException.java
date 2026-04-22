package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

// ─────────────────────────────────────────────────────────────────────────────
// Each exception maps to a concrete HTTP status code, allowing the
// GlobalExceptionHandler to translate them uniformly without per-type methods.
// ─────────────────────────────────────────────────────────────────────────────

/** Thrown when a registration email or username is already taken. */
public class EmailAlreadyExistsException extends IdentityException {
    public EmailAlreadyExistsException(String email) {
        super("Email is already registered: " + email, HttpStatus.CONFLICT);
    }
}
