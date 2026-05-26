package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class PasswordSetupNotRequiredException extends IdentityException {

    public PasswordSetupNotRequiredException() {
        super("Esta cuenta no requiere configurar contrasena de Google.", HttpStatus.BAD_REQUEST);
    }
}
