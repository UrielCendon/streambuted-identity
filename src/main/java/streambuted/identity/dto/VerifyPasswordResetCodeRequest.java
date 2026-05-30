package streambuted.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyPasswordResetCodeRequest(
    java.util.UUID attemptId,
    String email,
    @NotBlank(message = "El código es obligatorio.")
    @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos.")
    String code
) {}
