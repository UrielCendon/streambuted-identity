package streambuted.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for POST /api/v1/auth/register.
 * All new accounts start as LISTENER — role cannot be selected at registration.
 */
public record RegisterRequest(

    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be a valid address")
    @Size(max = 320, message = "Email must not exceed 320 characters")
    String email,

    @NotBlank(message = "Username must not be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    String username,

    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    String password
) {}
