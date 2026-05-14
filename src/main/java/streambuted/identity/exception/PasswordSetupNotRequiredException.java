package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class PasswordSetupNotRequiredException extends IdentityException {

    public PasswordSetupNotRequiredException() {
        super("This account does not require Google password setup.", HttpStatus.BAD_REQUEST);
    }
}
