package streambuted.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CompletePasswordResetRequest(
    @NotNull(message = "El id del intento es obligatorio.")
    UUID attemptId,

    @NotBlank(message = "El correo es obligatorio.")
    @Size(max = 320, message = "El correo no debe superar 320 caracteres.")
    String email,

    @NotBlank(message = "La contraseña es obligatoria.")
    @Size(min = 8, max = 15, message = "La contraseña debe tener entre 8 y 15 caracteres.")
    String password,

    @NotBlank(message = "La confirmación de contraseña es obligatoria.")
    @Size(min = 8, max = 15, message = "La confirmación de contraseña debe tener entre 8 y 15 caracteres.")
    String confirmPassword
) {}
