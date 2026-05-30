package streambuted.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DesktopHandoffCodeRequest(

    @NotBlank(message = "El state de autenticacion desktop es obligatorio.")
    @Size(max = 512, message = "El state de autenticacion desktop es demasiado largo.")
    String state,

    @NotBlank(message = "El redirectUri de autenticacion desktop es obligatorio.")
    @Size(max = 255, message = "El redirectUri de autenticacion desktop es demasiado largo.")
    String redirectUri
) {}
