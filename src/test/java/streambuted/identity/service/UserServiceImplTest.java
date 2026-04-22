package streambuted.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import streambuted.identity.domain.*;
import streambuted.identity.dto.UserProfileResponse;
import streambuted.identity.exception.*;
import streambuted.identity.repository.*;

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
    @Spy private ObjectMapper            objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private UserServiceImpl userService;

    private UUID              accountId;
    private UserAccountEntity listenerAccount;
    private UserAccountEntity artistAccount;
    private UserProfileEntity profile;

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
                .hasMessageContaining(unknownId.toString());
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
}