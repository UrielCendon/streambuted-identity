package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class UsernameAlreadyExistsException extends IdentityException {
    public UsernameAlreadyExistsException(String username) {
        super("Registration cannot be completed with the provided data.", HttpStatus.CONFLICT);
    }
}
