package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class PasswordResetAccountNotFoundException extends IdentityException {

    public PasswordResetAccountNotFoundException() {
        super("No existe ninguna cuenta asociada a ese correo.", HttpStatus.NOT_FOUND);
    }
}
