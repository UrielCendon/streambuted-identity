package streambuted.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DesktopLogoutRequest(

    @NotBlank(message = "El refresh token desktop es obligatorio.")
    @Size(max = 512, message = "El refresh token desktop es demasiado largo.")
    String refreshToken
) {}
