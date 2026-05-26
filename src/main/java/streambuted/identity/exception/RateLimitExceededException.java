package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends IdentityException {
    public RateLimitExceededException() {
        super("Demasiados intentos. Espera antes de volver a intentarlo.", HttpStatus.TOO_MANY_REQUESTS);
    }
}
