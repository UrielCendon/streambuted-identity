package streambuted.identity.service;

import streambuted.identity.dto.*;
import streambuted.identity.service.oauth.GoogleOAuthMode;
import streambuted.identity.service.oauth.GoogleUserInfo;

import java.util.UUID;

/**
 * Contract for authentication operations.
 * All methods are stateless with respect to HTTP sessions.
 */
public interface AuthService {

    /**
     * Starts an email registration attempt and sends a verification code.
     * No account is created until verifyRegistration succeeds.
     */
    RegistrationVerificationResponse startRegistration(RegisterRequest request);

    /**
     * Invalidates the previous active code for an attempt and sends a fresh one.
     */
    RegistrationVerificationResponse resendRegistrationCode(ResendRegistrationCodeRequest request);

    /**
     * Completes registration only after the 6-digit verification code is valid.
     */
    LoginResponse verifyRegistration(VerifyRegistrationRequest request);

    /**
     * Cancels a pending registration verification attempt.
     */
    void cancelRegistration(CancelRegistrationVerificationRequest request);

    /**
     * Creates or resolves a StreamButed account from a verified Google profile.
     */
    GoogleAuthenticationResult authenticateWithGoogle(
        GoogleUserInfo googleUserInfo,
        GoogleOAuthMode mode
    );

    /**
     * Completes password setup for accounts created through Google registration.
     */
    void completeGooglePasswordSetup(UUID userId, SetupPasswordRequest request);

    /**
     * Authenticates a user and issues a JWT + refresh token pair.
     * Throws InvalidCredentialsException on bad credentials or inactive account.
     * Throws AccountBannedException when credentials are valid but the account is banned.
     */
    LoginResponse login(LoginRequest request);

    /**
     * Exchanges a valid refresh token for a new JWT + refresh token pair
     * (token rotation; the old refresh token is revoked).
     * Throws InvalidRefreshTokenException if the token is expired or revoked.
     */
    LoginResponse refresh(String refreshToken);

    /**
     * Invalidates a refresh token during logout.
     * This is idempotent to avoid leaking token existence.
     */
    void logout(String refreshToken);

    /**
     * Validates an access token against the current account state.
     */
    ValidatedTokenResponse validateAccessToken(String token);
}
