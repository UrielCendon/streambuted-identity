package streambuted.identity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request used by administrators to temporarily or permanently ban an account.
 */
public record AdminBanUserRequest(
    @NotBlank(message = "banType es obligatorio.")
    String banType,

    @Min(value = 1, message = "durationAmount debe ser al menos 1.")
    @Max(value = 3650, message = "durationAmount no debe superar 3650.")
    Integer durationAmount,

    String durationUnit,

    @Size(max = 500, message = "reason no debe superar 500 caracteres.")
    String reason
) {}
