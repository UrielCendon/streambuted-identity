package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class InvalidDesktopRedirectUriException extends IdentityException {

    public InvalidDesktopRedirectUriException() {
        super("El redirectUri de autenticacion desktop no esta permitido.", HttpStatus.BAD_REQUEST);
    }
}
