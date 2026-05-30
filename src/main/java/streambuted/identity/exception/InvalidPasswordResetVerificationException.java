package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class InvalidPasswordResetVerificationException extends IdentityException {

    public InvalidPasswordResetVerificationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
