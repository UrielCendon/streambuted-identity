package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class VerificationEmailDeliveryException extends IdentityException {

    public VerificationEmailDeliveryException() {
        super("Verification email could not be sent. Please try again later.", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
