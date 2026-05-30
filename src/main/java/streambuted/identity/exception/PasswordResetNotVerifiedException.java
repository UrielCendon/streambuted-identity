package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class PasswordResetNotVerifiedException extends IdentityException {

    public PasswordResetNotVerifiedException() {
        super("Primero debes verificar el código de recuperación.", HttpStatus.BAD_REQUEST);
    }
}
