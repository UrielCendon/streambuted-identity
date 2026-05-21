package streambuted.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import streambuted.identity.domain.OutboxEntity;
import streambuted.identity.domain.OutboxStatus;
import streambuted.identity.domain.Role;
import streambuted.identity.domain.UserAccountEntity;
import streambuted.identity.domain.UserProfileEntity;
import streambuted.identity.dto.AdminBanUserRequest;
import streambuted.identity.dto.AdminUserListResponse;
import streambuted.identity.dto.AdminUserResponse;
import streambuted.identity.dto.PaginationResponse;
import streambuted.identity.dto.UpdateUserProfileRequest;
import streambuted.identity.dto.UserProfileResponse;
import streambuted.identity.exception.AdminModerationException;
import streambuted.identity.exception.ProfileUpdateException;
import streambuted.identity.exception.RolePromotionException;
import streambuted.identity.exception.UserNotFoundException;
import streambuted.identity.media.MediaAssetClient;
import streambuted.identity.media.MediaAssetMetadata;
import streambuted.identity.messaging.UserPromotedEvent;
import streambuted.identity.repository.OutboxRepository;
import streambuted.identity.repository.RefreshTokenRepository;
import streambuted.identity.repository.UserAccountRepository;
import streambuted.identity.repository.UserProfileRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Handles profile reads, profile updates, and listener-to-artist promotion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserServiceImpl implements UserService {

    private static final String PROFILE_IMAGE_ASSET_TYPE = "PROFILE_IMAGE";
    private static final int USERNAME_MIN_LENGTH = 3;
    private static final int USERNAME_MAX_LENGTH = 50;
    private static final int BIO_MAX_LENGTH = 1000;

    private final UserAccountRepository accountRepository;
    private final UserProfileRepository profileRepository;
    private final OutboxRepository outboxRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ObjectMapper objectMapper;
    private final MediaAssetClient mediaAssetClient;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        UserAccountEntity account = accountRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        UserProfileEntity profile = profileRepository.findByAccountId(userId)
            .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        return mapToResponse(account, profile);
    }

    @Override
    public UserProfileResponse updateProfile(
        UUID userId,
        UpdateUserProfileRequest request,
        String authorizationHeader
    ) {
        UserAccountEntity account = accountRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        UserProfileEntity profile = profileRepository.findByAccountId(userId)
            .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        applyUsernameUpdate(profile, request);
        applyBioUpdate(profile, request);
        applyProfileImageUpdate(profile, userId, request, authorizationHeader);

        profileRepository.save(profile);
        return mapToResponse(account, profile);
    }

    @Override
    public UserProfileResponse promoteToArtist(UUID userId) {
        UserAccountEntity account = accountRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        if (account.getRole() != Role.LISTENER) {
            throw new RolePromotionException(
                "Role promotion is only available to accounts with the LISTENER role. "
                + "Current role: " + account.getRole().name()
            );
        }

        account.setRole(Role.ARTIST);
        accountRepository.save(account);

        UserProfileEntity profile = profileRepository.findByAccountId(userId)
            .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        UserPromotedEvent event = UserPromotedEvent.of(
            userId,
            account.getEmail(),
            profile.getUsername()
        );

        OutboxEntity outboxEvent = OutboxEntity.builder()
            .aggregateId(userId)
            .eventType("USER_PROMOTED")
            .payload(createPayload(event))
            .status(OutboxStatus.PENDING)
            .retryCount(0)
            .build();

        outboxRepository.save(outboxEvent);

        log.info("Promoted userId={} from LISTENER to ARTIST", userId);

        return mapToResponse(account, profile);
    }

    @Override
    public AdminUserListResponse listUsersForAdmin(int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);
        PageRequest pageRequest = PageRequest.of(safeOffset / safeLimit, safeLimit);

        Page<UserAccountEntity> page = accountRepository.findAllForAdmin(pageRequest);
        return new AdminUserListResponse(
            page.getContent().stream()
                .map(this::mapToAdminResponse)
                .toList(),
            new PaginationResponse(safeLimit, safeOffset, page.getTotalElements())
        );
    }

    @Override
    public AdminUserResponse banUser(
        UUID adminUserId,
        UUID targetUserId,
        AdminBanUserRequest request
    ) {
        if (adminUserId.equals(targetUserId)) {
            throw AdminModerationException.forbidden("Administrators cannot ban their own account.");
        }

        UserAccountEntity account = accountRepository.findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException(targetUserId.toString()));

        if (account.getRole() == Role.ADMIN) {
            throw AdminModerationException.forbidden("Administrator accounts cannot be banned from moderation.");
        }

        Instant now = Instant.now();
        Instant bannedUntil = resolveBannedUntil(request, now);

        account.setActive(false);
        account.setBannedAt(now);
        account.setBannedUntil(bannedUntil);
        account.setBanReason(normalizeReason(request.reason()));
        accountRepository.save(account);
        refreshTokenRepository.revokeAllByAccountId(targetUserId);

        log.info("Admin userId={} banned targetUserId={} until={}", adminUserId, targetUserId, bannedUntil);
        return mapToAdminResponse(account);
    }

    @Override
    public AdminUserResponse unbanUser(UUID adminUserId, UUID targetUserId) {
        if (adminUserId.equals(targetUserId)) {
            throw AdminModerationException.forbidden("Administrators cannot reactivate their own account here.");
        }

        UserAccountEntity account = accountRepository.findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException(targetUserId.toString()));

        account.setActive(true);
        account.setBannedAt(null);
        account.setBannedUntil(null);
        account.setBanReason(null);
        accountRepository.save(account);

        log.info("Admin userId={} reactivated targetUserId={}", adminUserId, targetUserId);
        return mapToAdminResponse(account);
    }

    private void applyUsernameUpdate(
        UserProfileEntity profile,
        UpdateUserProfileRequest request
    ) {
        if (!request.hasUsername()) {
            return;
        }

        String username = request.username();
        if (username == null || username.isBlank()) {
            throw ProfileUpdateException.badRequest("Username must not be blank.");
        }

        String normalizedUsername = username.trim();
        if (
            normalizedUsername.length() < USERNAME_MIN_LENGTH
                || normalizedUsername.length() > USERNAME_MAX_LENGTH
        ) {
            throw ProfileUpdateException.badRequest(
                "Username must be between 3 and 50 characters."
            );
        }

        profile.setUsername(normalizedUsername);
    }

    private void applyBioUpdate(
        UserProfileEntity profile,
        UpdateUserProfileRequest request
    ) {
        if (!request.hasBio()) {
            return;
        }

        String bio = request.bio();
        if (bio == null) {
            profile.setBio(null);
            return;
        }

        String normalizedBio = bio.trim();
        if (normalizedBio.length() > BIO_MAX_LENGTH) {
            throw ProfileUpdateException.badRequest(
                "Bio must not exceed 1000 characters."
            );
        }

        profile.setBio(normalizedBio.isEmpty() ? null : normalizedBio);
    }

    private void applyProfileImageUpdate(
        UserProfileEntity profile,
        UUID userId,
        UpdateUserProfileRequest request,
        String authorizationHeader
    ) {
        if (!request.hasProfileImageAssetId()) {
            return;
        }

        String profileImageAssetId = request.profileImageAssetId();
        if (profileImageAssetId == null) {
            profile.setProfileImageAssetId(null);
            return;
        }

        UUID assetId = parseProfileImageAssetId(profileImageAssetId);
        MediaAssetMetadata metadata = mediaAssetClient.getAssetMetadata(
            assetId,
            authorizationHeader
        );

        if (!metadata.exists() || !PROFILE_IMAGE_ASSET_TYPE.equals(metadata.assetType())) {
            throw ProfileUpdateException.badRequest(
                "profileImageAssetId must reference an existing PROFILE_IMAGE asset."
            );
        }

        if (!userId.equals(metadata.ownerUserId())) {
            throw ProfileUpdateException.forbidden(
                "The referenced media asset does not belong to the authenticated user."
            );
        }

        profile.setProfileImageAssetId(metadata.assetId().toString());
    }

    private UUID parseProfileImageAssetId(String profileImageAssetId) {
        try {
            return UUID.fromString(profileImageAssetId.trim());
        } catch (RuntimeException ex) {
            throw ProfileUpdateException.badRequest(
                "profileImageAssetId must be a valid UUID."
            );
        }
    }

    private UserProfileResponse mapToResponse(UserAccountEntity account, UserProfileEntity profile) {
        return new UserProfileResponse(
            account.getId(),
            account.getEmail(),
            profile.getUsername(),
            profile.getBio(),
            profile.getProfileImageAssetId(),
            account.getRole().name().toLowerCase(),
            account.isActive(),
            account.isPasswordSetupRequired(),
            account.getCreatedAt()
        );
    }

    private AdminUserResponse mapToAdminResponse(UserAccountEntity account) {
        UserProfileEntity profile = account.getProfile();
        if (profile == null) {
            profile = profileRepository.findByAccountId(account.getId())
                .orElseThrow(() -> new UserNotFoundException(account.getId().toString()));
        }

        return new AdminUserResponse(
            account.getId(),
            account.getEmail(),
            profile.getUsername(),
            profile.getBio(),
            profile.getProfileImageAssetId(),
            account.getRole().name().toLowerCase(),
            account.isActive(),
            account.isPasswordSetupRequired(),
            account.getCreatedAt(),
            account.getBannedAt(),
            account.getBannedUntil(),
            account.getBanReason(),
            resolveBanStatus(account)
        );
    }

    private Instant resolveBannedUntil(AdminBanUserRequest request, Instant now) {
        String banType = request.banType() == null ? "" : request.banType().trim().toUpperCase();
        if ("PERMANENT".equals(banType)) {
            return null;
        }

        if (!"TEMPORARY".equals(banType)) {
            throw AdminModerationException.badRequest("banType must be TEMPORARY or PERMANENT.");
        }

        if (request.durationAmount() == null) {
            throw AdminModerationException.badRequest("durationAmount is required for temporary bans.");
        }

        String unit = request.durationUnit() == null ? "DAYS" : request.durationUnit().trim().toUpperCase();
        return switch (unit) {
            case "HOURS" -> now.plus(request.durationAmount(), ChronoUnit.HOURS);
            case "DAYS" -> now.plus(request.durationAmount(), ChronoUnit.DAYS);
            case "WEEKS" -> now.plus(request.durationAmount() * 7L, ChronoUnit.DAYS);
            default -> throw AdminModerationException.badRequest("durationUnit must be HOURS, DAYS or WEEKS.");
        };
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }

        String trimmed = reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveBanStatus(UserAccountEntity account) {
        if (account.isActive()) {
            return "ACTIVE";
        }

        if (account.getBannedAt() == null) {
            return "INACTIVE";
        }

        Instant bannedUntil = account.getBannedUntil();
        if (bannedUntil == null) {
            return "PERMANENT";
        }

        return Instant.now().isAfter(bannedUntil) ? "EXPIRED" : "TEMPORARY";
    }

    private JsonNode createPayload(UserPromotedEvent event) {
        try {
            return objectMapper.valueToTree(event);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to serialize outbox event", ex);
        }
    }
}
