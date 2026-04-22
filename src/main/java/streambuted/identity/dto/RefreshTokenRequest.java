package streambuted.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for POST /api/v1/auth/refresh.
 */
public record RefreshTokenRequest(

    @NotBlank(message = "Refresh token must not be blank")
    String refreshToken
) {}
