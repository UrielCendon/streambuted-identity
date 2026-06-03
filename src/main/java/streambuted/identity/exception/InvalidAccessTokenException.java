package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class InvalidAccessTokenException extends IdentityException {

    public InvalidAccessTokenException() {
        super("Tu sesion expiro. Inicia sesion nuevamente.", HttpStatus.UNAUTHORIZED);
    }
}
