package streambuted.identity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Custom exception hierarchy.

/**
 * Base class for all domain-level exceptions in the Identity Service.
 * Subclasses carry an HTTP status so the GlobalExceptionHandler can
 * map them without needing per-type handler methods.
 */
public abstract class IdentityException extends RuntimeException {

    private final HttpStatus httpStatus;

    protected IdentityException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
