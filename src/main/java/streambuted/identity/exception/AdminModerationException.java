package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

/** Thrown when an administrative moderation action cannot be applied. */
public class AdminModerationException extends IdentityException {

    public AdminModerationException(String message, HttpStatus httpStatus) {
        super(message, httpStatus);
    }

    public static AdminModerationException badRequest(String message) {
        return new AdminModerationException(message, HttpStatus.BAD_REQUEST);
    }

    public static AdminModerationException forbidden(String message) {
        return new AdminModerationException(message, HttpStatus.FORBIDDEN);
    }
}
