package streambuted.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for POST /api/v1/auth/login.
 */
public record LoginRequest(

    @NotBlank(message = "El correo es obligatorio.")
    @Email(message = "El correo debe tener un formato valido.")
    @Size(max = 320, message = "El correo no debe superar 320 caracteres.")
    String email,

    @NotBlank(message = "La contrasena es obligatoria.")
    @Size(min = 8, max = 15, message = "La contrasena debe tener entre 8 y 15 caracteres.")
    String password
) {}
