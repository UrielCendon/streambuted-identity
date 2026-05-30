package streambuted.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartPasswordResetRequest(
    @NotBlank(message = "El correo es obligatorio.")
    @Email(message = "El correo debe tener un formato válido.")
    @Size(max = 320, message = "El correo no debe superar 320 caracteres.")
    String email
) {}
