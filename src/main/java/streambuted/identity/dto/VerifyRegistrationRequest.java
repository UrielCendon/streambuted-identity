package streambuted.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VerifyRegistrationRequest(

    @NotNull(message = "El identificador del intento de verificacion es obligatorio.")
    UUID attemptId,

    @NotBlank(message = "El correo es obligatorio.")
    @Email(message = "El correo debe tener un formato valido.")
    @Size(max = 320, message = "El correo no debe superar 320 caracteres.")
    String email,

    @NotBlank(message = "El codigo de verificacion es obligatorio.")
    @Pattern(regexp = "\\d{6}", message = "El codigo de verificacion debe tener 6 digitos.")
    String code
) {}
