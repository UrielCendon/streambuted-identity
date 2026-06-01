package streambuted.identity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Returned by POST /api/v1/auth/login and POST /api/v1/auth/refresh.
 *
 * @param accessToken  Short-lived JWT (30 min) to be sent as Bearer token.
 * @param refreshToken Opaque long-lived token (7 days) stored by the client.
 * @param role         Resolved role from the user account (lowercase string).
 * @param expiresIn    Remaining lifetime of the access token in seconds.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
    String accessToken,
    String refreshToken,
    String role,
    long expiresIn
) {}
