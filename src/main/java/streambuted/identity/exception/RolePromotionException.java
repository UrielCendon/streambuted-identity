package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the promote endpoint is called by a user whose current role
 * does not allow promotion (e.g. already an artist or admin).
 */
public class RolePromotionException extends IdentityException {
    public RolePromotionException(String detail) {
        super(detail, HttpStatus.CONFLICT);
    }
}
