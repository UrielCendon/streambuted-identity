package streambuted.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for POST /api/v1/auth/refresh.
 */
public record RefreshTokenRequest(

    @NotBlank(message = "El refresh token es obligatorio.")
    String refreshToken
) {}
