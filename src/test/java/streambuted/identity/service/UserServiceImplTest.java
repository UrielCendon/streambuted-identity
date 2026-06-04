package streambuted.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import streambuted.identity.domain.*;
import streambuted.identity.dto.AdminUserListResponse;
import streambuted.identity.dto.UpdateUserProfileRequest;
import streambuted.identity.dto.UserProfileResponse;
import streambuted.identity.exception.*;
import streambuted.identity.media.MediaAssetClient;
import streambuted.identity.media.MediaAssetMetadata;
import streambuted.identity.repository.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserServiceImpl.
 *
 * Coverage targets:
 *  - getProfile(): success, user not found
 *  - promoteToArtist(): success (listener → artist, event published),
 *                       already artist, admin attempt, user not found
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl Unit Tests")
class UserServiceImplTest {

    @Mock private UserAccountRepository  accountRepository;
    @Mock private UserProfileRepository  profileRepository;
    @Mock private OutboxRepository       outboxRepository;
    @Mock private MediaAssetClient       mediaAssetClient;
    @Spy private ObjectMapper            objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private UserServiceImpl userService;

    private UUID              accountId;
    private UserAccountEntity listenerAccount;
    private UserAccountEntity artistAccount;
    private UserProfileEntity profile;

    private static final String AUTHORIZATION_HEADER = "Bearer access-token";

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();

        listenerAccount = UserAccountEntity.builder()
            .id(accountId)
            .email("listener@example.com")
            .passwordHash("$2a$12$hashed")
            .role(Role.LISTENER)
            .isActive(true)
            .build();

        artistAccount = UserAccountEntity.builder()
            .id(UUID.randomUUID())
            .email("artist@example.com")
            .passwordHash("$2a$12$hashed")
            .role(Role.ARTIST)
            .isActive(true)
            .build();

        profile = UserProfileEntity.builder()
            .id(UUID.randomUUID())
            .account(listenerAccount)
            .username("listeneruser")
            .bio("A music lover")
            .build();
        listenerAccount.setProfile(profile);
    }

    @Nested
    @DisplayName("getProfile()")
    class GetProfileTests {

        @Test
        @DisplayName("should return combined account+profile response on success")
        void getProfile_success() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(listenerAccount));
            when(profileRepository.findByAccountId(accountId)).thenReturn(Optional.of(profile));

            UserProfileResponse response = userService.getProfile(accountId);

            assertThat(response.id()).isEqualTo(accountId);
            assertThat(response.email()).isEqualTo("listener@example.com");
            assertThat(response.username()).isEqualTo("listeneruser");
            assertThat(response.bio()).isEqualTo("A music lover");
            assertThat(response.role()).isEqualTo("listener");
            assertThat(response.isActive()).isTrue();
        }

        @Test
        @DisplayName("should throw UserNotFoundException when account does not exist")
        void getProfile_unknownUser_throwsException() {
            UUID unknownId = UUID.randomUUID();
            when(accountRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getProfile(unknownId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("El usuario solicitado no existe");
        }
    }

    @Nested
    @DisplayName("listUsersForAdmin()")
    class ListUsersForAdminTests {

        @Test
        @DisplayName("should use the unfiltered repository query when search term is null")
        void listUsersForAdmin_nullSearch_usesUnfilteredQuery() {
            PageRequest pageRequest = PageRequest.of(0, 10);
            when(accountRepository.findAllForAdmin(pageRequest))
                .thenReturn(new PageImpl<>(List.of(listenerAccount), pageRequest, 1));

            AdminUserListResponse response = userService.listUsersForAdmin(10, 0, null);

            assertThat(response.data()).hasSize(1);
            assertThat(response.data().get(0).email()).isEqualTo("listener@example.com");
            verify(accountRepository).findAllForAdmin(pageRequest);
            verify(accountRepository, never()).searchAllForAdmin(anyString(), any());
        }

        @Test
        @DisplayName("should use the unfiltered repository query when search term is blank")
        void listUsersForAdmin_blankSearch_usesUnfilteredQuery() {
            PageRequest pageRequest = PageRequest.of(0, 10);
            when(accountRepository.findAllForAdmin(pageRequest))
                .thenReturn(new PageImpl<>(List.of(listenerAccount), pageRequest, 1));

            userService.listUsersForAdmin(10, 0, "   ");

            verify(accountRepository).findAllForAdmin(pageRequest);
            verify(accountRepository, never()).searchAllForAdmin(anyString(), any());
        }

        @Test
        @DisplayName("should trim search terms before using the filtered repository query")
        void listUsersForAdmin_searchTerm_usesFilteredQuery() {
            PageRequest pageRequest = PageRequest.of(2, 10);
            when(accountRepository.searchAllForAdmin("artist", pageRequest))
                .thenReturn(new PageImpl<>(List.of(listenerAccount), pageRequest, 1));

            userService.listUsersForAdmin(10, 20, " artist ");

            verify(accountRepository).searchAllForAdmin("artist", pageRequest);
            verify(accountRepository, never()).findAllForAdmin(any(PageRequest.class));
        }
    }

    @Nested
    @DisplayName("updateProfile()")
    class UpdateProfileTests {

        @Test
        @DisplayName("should leave profileImageAssetId unchanged when field is omitted")
        void updateProfile_withoutProfileImage_doesNotChangeExistingImage() {
            UUID existingAssetId = UUID.randomUUID();
            profile.setProfileImageAssetId(existingAssetId.toString());
            UpdateUserProfileRequest request = new UpdateUserProfileRequest();
            request.setBio("Updated bio");

            stubAccountAndProfile();

            UserProfileResponse response = userService.updateProfile(
                accountId,
                request,
                AUTHORIZATION_HEADER
            );

            assertThat(response.bio()).isEqualTo("Updated bio");
            assertThat(response.profileImageAssetId()).isEqualTo(existingAssetId.toString());
            verify(mediaAssetClient, never()).getAssetMetadata(any(), any());
            verify(profileRepository).save(profile);
        }

        @Test
        @DisplayName("should validate PROFILE_IMAGE asset before saving reference")
        void updateProfile_validProfileImage_savesReference() {
            UUID assetId = UUID.randomUUID();
            UpdateUserProfileRequest request = new UpdateUserProfileRequest();
            request.setProfileImageAssetId(assetId.toString());

            stubAccountAndProfile();
            when(mediaAssetClient.getAssetMetadata(assetId, AUTHORIZATION_HEADER))
                .thenReturn(profileImageMetadata(assetId, accountId));

            UserProfileResponse response = userService.updateProfile(
                accountId,
                request,
                AUTHORIZATION_HEADER
            );

            assertThat(response.profileImageAssetId()).isEqualTo(assetId.toString());
            verify(profileRepository).save(argThat(saved ->
                assetId.toString().equals(saved.getProfileImageAssetId())
            ));
        }

        @Test
        @DisplayName("should reject malformed profileImageAssetId")
        void updateProfile_invalidUuid_rejectsRequest() {
            UpdateUserProfileRequest request = new UpdateUserProfileRequest();
            request.setProfileImageAssetId("not-a-uuid");

            stubAccountAndProfile();

            assertProfileUpdateFailure(
                () -> userService.updateProfile(accountId, request, AUTHORIZATION_HEADER),
                HttpStatus.BAD_REQUEST
            );

            verify(mediaAssetClient, never()).getAssetMetadata(any(), any());
            verify(profileRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject non PROFILE_IMAGE assets")
        void updateProfile_audioAsset_rejectsRequest() {
            UUID assetId = UUID.randomUUID();
            UpdateUserProfileRequest request = new UpdateUserProfileRequest();
            request.setProfileImageAssetId(assetId.toString());

            stubAccountAndProfile();
            when(mediaAssetClient.getAssetMetadata(assetId, AUTHORIZATION_HEADER))
                .thenReturn(new MediaAssetMetadata(
                    assetId,
                    "AUDIO",
                    accountId,
                    "audio/mpeg",
                    2048L,
                    true
                ));

            assertProfileUpdateFailure(
                () -> userService.updateProfile(accountId, request, AUTHORIZATION_HEADER),
                HttpStatus.BAD_REQUEST
            );

            verify(profileRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject assets owned by another user")
        void updateProfile_otherOwner_rejectsRequest() {
            UUID assetId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            UpdateUserProfileRequest request = new UpdateUserProfileRequest();
            request.setProfileImageAssetId(assetId.toString());

            stubAccountAndProfile();
            when(mediaAssetClient.getAssetMetadata(assetId, AUTHORIZATION_HEADER))
                .thenReturn(profileImageMetadata(assetId, otherUserId));

            assertProfileUpdateFailure(
                () -> userService.updateProfile(accountId, request, AUTHORIZATION_HEADER),
                HttpStatus.FORBIDDEN
            );

            verify(profileRepository, never()).save(any());
        }

        @Test
        @DisplayName("should clear profileImageAssetId when field is explicit null")
        void updateProfile_nullProfileImage_clearsReference() {
            profile.setProfileImageAssetId(UUID.randomUUID().toString());
            UpdateUserProfileRequest request = new UpdateUserProfileRequest();
            request.setProfileImageAssetId(null);

            stubAccountAndProfile();

            UserProfileResponse response = userService.updateProfile(
                accountId,
                request,
                AUTHORIZATION_HEADER
            );

            assertThat(response.profileImageAssetId()).isNull();
            verify(mediaAssetClient, never()).getAssetMetadata(any(), any());
            verify(profileRepository).save(profile);
        }

        @Test
        @DisplayName("should surface controlled error when Media is unavailable")
        void updateProfile_mediaUnavailable_rejectsRequest() {
            UUID assetId = UUID.randomUUID();
            UpdateUserProfileRequest request = new UpdateUserProfileRequest();
            request.setProfileImageAssetId(assetId.toString());

            stubAccountAndProfile();
            when(mediaAssetClient.getAssetMetadata(assetId, AUTHORIZATION_HEADER))
                .thenThrow(ProfileUpdateException.serviceUnavailable(
                    "Media Service is temporarily unavailable for asset validation."
                ));

            assertProfileUpdateFailure(
                () -> userService.updateProfile(accountId, request, AUTHORIZATION_HEADER),
                HttpStatus.SERVICE_UNAVAILABLE
            );

            verify(profileRepository, never()).save(any());
        }

        @Test
        @DisplayName("should accept usernames up to 100 characters")
        void updateProfile_usernameWithOneHundredCharacters_succeeds() {
            String username = "a".repeat(100);
            UpdateUserProfileRequest request = new UpdateUserProfileRequest();
            request.setUsername(username);

            stubAccountAndProfile();
            when(profileRepository.existsByUsername(username)).thenReturn(false);

            UserProfileResponse response = userService.updateProfile(
                accountId,
                request,
                AUTHORIZATION_HEADER
            );

            assertThat(response.username()).isEqualTo(username);
            verify(profileRepository).save(profile);
        }

        @Test
        @DisplayName("should reject username already used by another profile")
        void updateProfile_duplicateUsername_rejectsRequest() {
            UpdateUserProfileRequest request = new UpdateUserProfileRequest();
            request.setUsername("taken-user");

            stubAccountAndProfile();
            when(profileRepository.existsByUsername("taken-user")).thenReturn(true);

            assertThatThrownBy(() -> userService.updateProfile(accountId, request, AUTHORIZATION_HEADER))
                .isInstanceOf(UsernameAlreadyExistsException.class)
                .hasMessageContaining("Ese nombre de usuario ya esta en uso");

            verify(profileRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("promoteToArtist()")
    class PromoteToArtistTests {

        @Test
        @DisplayName("should change role to ARTIST and publish UserPromotedEvent")
        void promote_success_changesRoleAndPublishesEvent() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(listenerAccount));
            when(accountRepository.save(any())).thenReturn(listenerAccount);
            when(profileRepository.findByAccountId(accountId)).thenReturn(Optional.of(profile));
            when(outboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            UserProfileResponse response = userService.promoteToArtist(accountId);

            assertThat(listenerAccount.getRole()).isEqualTo(Role.ARTIST);
            assertThat(response.role()).isEqualTo("artist");

            verify(outboxRepository).save(argThat(outbox ->
                outbox.getAggregateId().equals(accountId) &&
                outbox.getEventType().equals("USER_PROMOTED") &&
                outbox.getStatus() == OutboxStatus.PENDING &&
                outbox.getRetryCount() == 0 &&
                payloadHasExpectedFields(outbox.getPayload())
            ));
        }

        @Test
        @DisplayName("should persist the role change via accountRepository.save()")
        void promote_success_persistsRoleChange() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(listenerAccount));
            when(accountRepository.save(any())).thenReturn(listenerAccount);
            when(profileRepository.findByAccountId(accountId)).thenReturn(Optional.of(profile));
            when(outboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            userService.promoteToArtist(accountId);

            verify(accountRepository).save(argThat(acc -> acc.getRole() == Role.ARTIST));
        }

        @Test
        @DisplayName("should throw RolePromotionException when user is already an ARTIST")
        void promote_alreadyArtist_throwsException() {
            UUID artistId = artistAccount.getId();
            when(accountRepository.findById(artistId)).thenReturn(Optional.of(artistAccount));

            assertThatThrownBy(() -> userService.promoteToArtist(artistId))
                .isInstanceOf(RolePromotionException.class)
                .hasMessageContaining("ARTIST");

            verify(accountRepository, never()).save(any());
            verify(outboxRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw RolePromotionException when user is an ADMIN")
        void promote_adminAccount_throwsException() {
            UserAccountEntity adminAccount = UserAccountEntity.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .passwordHash("$2a$12$hashed")
                .role(Role.ADMIN)
                .isActive(true)
                .build();

            when(accountRepository.findById(adminAccount.getId()))
                .thenReturn(Optional.of(adminAccount));

            assertThatThrownBy(() -> userService.promoteToArtist(adminAccount.getId()))
                .isInstanceOf(RolePromotionException.class)
                .hasMessageContaining("ADMIN");

            verify(outboxRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw UserNotFoundException when account does not exist")
        void promote_unknownUser_throwsException() {
            UUID unknownId = UUID.randomUUID();
            when(accountRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.promoteToArtist(unknownId))
                .isInstanceOf(UserNotFoundException.class);

            verify(outboxRepository, never()).save(any());
        }

        @Test
        @DisplayName("should fail when outbox persistence fails")
        void promote_outboxSaveFails_throwsException() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(listenerAccount));
            when(accountRepository.save(any())).thenReturn(listenerAccount);
            when(profileRepository.findByAccountId(accountId)).thenReturn(Optional.of(profile));
            when(outboxRepository.save(any())).thenThrow(new RuntimeException("DB error"));

            assertThatThrownBy(() -> userService.promoteToArtist(accountId))
                .isInstanceOf(RuntimeException.class);

            verify(accountRepository).save(any());
        }

        private boolean payloadHasExpectedFields(JsonNode payload) {
            return payload != null
                && payload.get("userId").asText().equals(accountId.toString())
                && payload.get("previousRole").asText().equals("listener")
                && payload.get("newRole").asText().equals("artist")
                && payload.get("email").asText().equals("listener@example.com");
        }
    }

    private void stubAccountAndProfile() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(listenerAccount));
        when(profileRepository.findByAccountId(accountId)).thenReturn(Optional.of(profile));
    }

    private MediaAssetMetadata profileImageMetadata(UUID assetId, UUID ownerUserId) {
        return new MediaAssetMetadata(
            assetId,
            "PROFILE_IMAGE",
            ownerUserId,
            "image/png",
            1024L,
            true
        );
    }

    private void assertProfileUpdateFailure(
        ThrowingCallable callable,
        HttpStatus expectedStatus
    ) {
        assertThatThrownBy(callable)
            .isInstanceOf(ProfileUpdateException.class)
            .satisfies(error -> assertThat(((ProfileUpdateException) error).getHttpStatus())
                .isEqualTo(expectedStatus));
    }
}
