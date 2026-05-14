package streambuted.identity.dto;

import java.util.UUID;

public record RegistrationVerificationResponse(
    UUID attemptId,
    String email,
    String status,
    long expiresInSeconds,
    String message
) {}
