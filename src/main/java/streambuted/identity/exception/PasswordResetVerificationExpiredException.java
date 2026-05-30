package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class PasswordResetVerificationExpiredException extends IdentityException {

    public PasswordResetVerificationExpiredException() {
        super("El código de recuperación expiró. Solicita uno nuevo.", HttpStatus.GONE);
    }
}
