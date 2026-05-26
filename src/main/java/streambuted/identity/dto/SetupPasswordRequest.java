package streambuted.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetupPasswordRequest(

    @NotBlank(message = "La contrasena es obligatoria.")
    @Size(min = 8, max = 15, message = "La contrasena debe tener entre 8 y 15 caracteres.")
    String password,

    @NotBlank(message = "La confirmacion de contrasena es obligatoria.")
    @Size(min = 8, max = 15, message = "La confirmacion de contrasena debe tener entre 8 y 15 caracteres.")
    String confirmPassword
) {}
