package streambuted.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned by GET /api/v1/users/me.
 * Combines account + profile data into a single response object.
 */
public record UserProfileResponse(
    UUID id,
    String email,
    String username,
    String bio,
    String profileImageAssetId,
    String role,
    boolean isActive,
    Instant createdAt
) {}
