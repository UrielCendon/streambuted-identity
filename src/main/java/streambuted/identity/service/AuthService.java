package streambuted.identity.service;

import streambuted.identity.dto.*;

/**
 * Contract for authentication operations.
 * All methods are stateless with respect to HTTP sessions.
 */
public interface AuthService {

    /**
     * Registers a new user account with role LISTENER.
     * Throws EmailAlreadyExistsException if the email is taken.
     */
    LoginResponse register(RegisterRequest request);

    /**
     * Authenticates a user and issues a JWT + refresh token pair.
     * Throws InvalidCredentialsException on bad credentials or inactive account.
     */
    LoginResponse login(LoginRequest request);

    /**
     * Exchanges a valid refresh token for a new JWT + refresh token pair
     * (token rotation — the old refresh token is revoked).
     * Throws InvalidRefreshTokenException if the token is expired or revoked.
     */
    LoginResponse refresh(RefreshTokenRequest request);
}
