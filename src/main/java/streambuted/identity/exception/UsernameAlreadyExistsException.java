package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class UsernameAlreadyExistsException extends IdentityException {
    public UsernameAlreadyExistsException(String username) {
        super("Ese nombre de usuario ya esta en uso. Elige otro.", HttpStatus.CONFLICT);
    }
}
