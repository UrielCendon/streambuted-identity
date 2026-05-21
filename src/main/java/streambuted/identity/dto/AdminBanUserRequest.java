package streambuted.identity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request used by administrators to temporarily or permanently ban an account.
 */
public record AdminBanUserRequest(
    @NotBlank(message = "banType is required.")
    String banType,

    @Min(value = 1, message = "durationAmount must be at least 1.")
    @Max(value = 3650, message = "durationAmount must not exceed 3650.")
    Integer durationAmount,

    String durationUnit,

    @Size(max = 500, message = "reason must not exceed 500 characters.")
    String reason
) {}
