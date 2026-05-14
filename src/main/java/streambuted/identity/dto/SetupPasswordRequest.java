package streambuted.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetupPasswordRequest(

    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    String password,

    @NotBlank(message = "Password confirmation must not be blank")
    @Size(min = 8, max = 128, message = "Password confirmation must be between 8 and 128 characters")
    String confirmPassword
) {}
