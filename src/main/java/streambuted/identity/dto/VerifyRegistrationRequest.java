package streambuted.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VerifyRegistrationRequest(

    @NotNull(message = "Verification attempt id is required")
    UUID attemptId,

    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be a valid address")
    @Size(max = 320, message = "Email must not exceed 320 characters")
    String email,

    @NotBlank(message = "Verification code must not be blank")
    @Pattern(regexp = "\\d{6}", message = "Verification code must contain 6 digits")
    String code
) {}
