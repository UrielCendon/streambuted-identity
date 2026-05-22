package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class InvalidAccessTokenException extends IdentityException {

    public InvalidAccessTokenException() {
        super("Invalid or expired JWT token.", HttpStatus.UNAUTHORIZED);
    }
}
