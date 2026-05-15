package streambuted.identity.service;

import streambuted.identity.dto.UserProfileResponse;
import streambuted.identity.dto.UpdateUserProfileRequest;

import java.util.UUID;

/**
 * Contract for user profile and account management operations.
 */
public interface UserService {

    /**
     * Returns the full profile for the authenticated user.
     * Throws UserNotFoundException if no account exists for the given id.
     */
    UserProfileResponse getProfile(UUID userId);

    /**
     * Updates profile fields for the authenticated user.
     * Validates profileImageAssetId against Media before storing the reference.
     */
    UserProfileResponse updateProfile(
        UUID userId,
        UpdateUserProfileRequest request,
        String authorizationHeader
    );

    /**
     * Promotes a LISTENER account to ARTIST. This action is irreversible.
     * Publishes a UserPromotedEvent to RabbitMQ upon success.
     * Throws RolePromotionException if the user is not a LISTENER.
     */
    UserProfileResponse promoteToArtist(UUID userId);
}
