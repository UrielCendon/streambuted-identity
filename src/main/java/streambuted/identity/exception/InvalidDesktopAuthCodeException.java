package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class InvalidDesktopAuthCodeException extends IdentityException {

    public InvalidDesktopAuthCodeException() {
        super("El codigo de autenticacion desktop no es valido o expiro.", HttpStatus.BAD_REQUEST);
    }
}
