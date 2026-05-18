package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends IdentityException {
    public RateLimitExceededException() {
        super("Too many attempts. Please wait before trying again.", HttpStatus.TOO_MANY_REQUESTS);
    }
}
