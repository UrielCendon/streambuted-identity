package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class PasswordPolicyException extends IdentityException {

    public PasswordPolicyException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
