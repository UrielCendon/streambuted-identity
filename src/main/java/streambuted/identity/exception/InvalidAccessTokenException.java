package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class InvalidAccessTokenException extends IdentityException {

    public InvalidAccessTokenException() {
        super("El token JWT es invalido o expiro.", HttpStatus.UNAUTHORIZED);
    }
}
