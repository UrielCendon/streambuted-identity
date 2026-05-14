package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class GoogleAuthenticationException extends IdentityException {
    public GoogleAuthenticationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
