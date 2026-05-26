package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

public class VerificationEmailDeliveryException extends IdentityException {

    public VerificationEmailDeliveryException() {
        super("No se pudo enviar el correo de verificacion. Intenta de nuevo mas tarde.", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
