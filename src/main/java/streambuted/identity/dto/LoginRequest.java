package streambuted.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload for POST /api/v1/auth/login.
 */
public record LoginRequest(

    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be a valid address")
    String email,

    @NotBlank(message = "Password must not be blank")
    String password
) {}
