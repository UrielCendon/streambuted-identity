package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class UsernameAlreadyExistsException extends IdentityException {
    public UsernameAlreadyExistsException(String username) {
        super("Username is already registered: " + username, HttpStatus.CONFLICT);
    }
}
