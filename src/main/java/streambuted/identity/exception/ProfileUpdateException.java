package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a profile update request cannot be accepted safely. */
public class ProfileUpdateException extends IdentityException {

    public ProfileUpdateException(String message, HttpStatus httpStatus) {
        super(message, httpStatus);
    }

    public static ProfileUpdateException badRequest(String message) {
        return new ProfileUpdateException(message, HttpStatus.BAD_REQUEST);
    }

    public static ProfileUpdateException forbidden(String message) {
        return new ProfileUpdateException(message, HttpStatus.FORBIDDEN);
    }

    public static ProfileUpdateException unauthorized(String message) {
        return new ProfileUpdateException(message, HttpStatus.UNAUTHORIZED);
    }

    public static ProfileUpdateException serviceUnavailable(String message) {
        return new ProfileUpdateException(message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static ProfileUpdateException gatewayTimeout(String message) {
        return new ProfileUpdateException(message, HttpStatus.GATEWAY_TIMEOUT);
    }

    public static ProfileUpdateException badGateway(String message) {
        return new ProfileUpdateException(message, HttpStatus.BAD_GATEWAY);
    }
}
