package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a refresh token is expired, revoked, or does not exist. */
public class InvalidRefreshTokenException extends IdentityException {
    public InvalidRefreshTokenException() {
        super("Refresh token is invalid, expired, or has been revoked", HttpStatus.UNAUTHORIZED);
    }
}
