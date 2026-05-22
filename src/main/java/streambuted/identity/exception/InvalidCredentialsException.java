package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

/** Thrown when email/password combination does not match any active account. */
public class InvalidCredentialsException extends IdentityException {
    public InvalidCredentialsException() {
        super("El correo o la contraseña son incorrectos.", HttpStatus.UNAUTHORIZED);
    }
}
