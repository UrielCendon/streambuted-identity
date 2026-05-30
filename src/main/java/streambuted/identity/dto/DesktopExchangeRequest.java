package streambuted.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DesktopExchangeRequest(

    @NotBlank(message = "El codigo de autenticacion desktop es obligatorio.")
    @Size(max = 512, message = "El codigo de autenticacion desktop es demasiado largo.")
    String code,

    @NotBlank(message = "El state de autenticacion desktop es obligatorio.")
    @Size(max = 512, message = "El state de autenticacion desktop es demasiado largo.")
    String state
) {}
