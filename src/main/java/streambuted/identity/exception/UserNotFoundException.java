package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a user lookup by id or email yields no result. */
public class UserNotFoundException extends IdentityException {
    public UserNotFoundException(String identifier) {
        super("El usuario solicitado no existe o ya no esta disponible.", HttpStatus.NOT_FOUND);
    }
}
