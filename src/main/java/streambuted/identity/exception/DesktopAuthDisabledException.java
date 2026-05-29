package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class DesktopAuthDisabledException extends IdentityException {

    public DesktopAuthDisabledException() {
        super("La autenticacion desktop no esta disponible.", HttpStatus.NOT_FOUND);
    }
}
