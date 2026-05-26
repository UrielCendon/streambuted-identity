package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class RegistrationVerificationExpiredException extends IdentityException {
    public RegistrationVerificationExpiredException() {
        super("El codigo de verificacion expiro. Solicita uno nuevo.", HttpStatus.GONE);
    }
}
