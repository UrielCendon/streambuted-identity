package streambuted.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Administrative view of an account used by the moderation panel.
 */
public record AdminUserResponse(
    UUID id,
    String email,
    String username,
    String bio,
    String profileImageAssetId,
    String role,
    boolean isActive,
    boolean passwordSetupRequired,
    Instant createdAt,
    Instant bannedAt,
    Instant bannedUntil,
    String banReason,
    String banStatus
) {}
