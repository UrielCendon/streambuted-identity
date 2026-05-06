package streambuted.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import streambuted.identity.domain.OutboxEntity;
import streambuted.identity.domain.OutboxStatus;
import streambuted.identity.domain.Role;
import streambuted.identity.domain.UserAccountEntity;
import streambuted.identity.domain.UserProfileEntity;
import streambuted.identity.dto.UpdateUserProfileRequest;
import streambuted.identity.dto.UserProfileResponse;
import streambuted.identity.exception.ProfileUpdateException;
import streambuted.identity.exception.RolePromotionException;
import streambuted.identity.exception.UserNotFoundException;
import streambuted.identity.media.MediaAssetClient;
import streambuted.identity.media.MediaAssetMetadata;
import streambuted.identity.messaging.UserPromotedEvent;
import streambuted.identity.repository.OutboxRepository;
import streambuted.identity.repository.UserAccountRepository;
import streambuted.identity.repository.UserProfileRepository;

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
            account.getCreatedAt()
        );
    }

    private JsonNode createPayload(UserPromotedEvent event) {
        try {
            return objectMapper.valueToTree(event);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to serialize outbox event", ex);
        }
    }
}
