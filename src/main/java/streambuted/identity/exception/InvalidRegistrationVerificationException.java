package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class InvalidRegistrationVerificationException extends IdentityException {
    public InvalidRegistrationVerificationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
