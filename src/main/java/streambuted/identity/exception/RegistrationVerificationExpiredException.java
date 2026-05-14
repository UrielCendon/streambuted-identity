package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class RegistrationVerificationExpiredException extends IdentityException {
    public RegistrationVerificationExpiredException() {
        super("Verification code has expired. Request a new code.", HttpStatus.GONE);
    }
}
