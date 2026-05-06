package streambuted.identity.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import streambuted.identity.domain.*;
import streambuted.identity.dto.*;
import streambuted.identity.exception.*;
import streambuted.identity.repository.*;
import streambuted.identity.security.JwtProperties;
import streambuted.identity.security.JwtService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthServiceImpl.
 *
 * Coverage targets:
 *  - register(): success, duplicate email
 *  - login(): success, wrong password, inactive account, unknown email
 *  - refresh(): success (token rotation), expired token, revoked token, unknown token
 *  - logout(): token invalidation by refresh token value
 *
 * All collaborators are mocked — no Spring context or database is started.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl Unit Tests")
class AuthServiceImplTest {

    @Mock private UserAccountRepository  accountRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder        passwordEncoder;
    @Mock private JwtService             jwtService;
    @Mock private JwtProperties          jwtProperties;

    @InjectMocks
    private AuthServiceImpl authService;

    private static final String VALID_EMAIL    = "test@example.com";
    private static final String VALID_USERNAME = "testuser";
    private static final String VALID_PASSWORD = "SecurePass1!";
    private static final String HASHED_PASSWORD = "$2a$12$hashed";
    private static final String ACCESS_TOKEN  = "eyJ.access.token";
    private static final String REFRESH_TOKEN = UUID.randomUUID().toString();
    private static final long   EXPIRY_SECONDS = 900L;

    private UUID accountId;
    private UserAccountEntity activeAccount;
    private UserProfileEntity profile;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();

        activeAccount = UserAccountEntity.builder()
            .id(accountId)
            .email(VALID_EMAIL)
            .passwordHash(HASHED_PASSWORD)
            .role(Role.LISTENER)
            .isActive(true)
            .build();

        profile = UserProfileEntity.builder()
            .id(UUID.randomUUID())
            .account(activeAccount)
            .username(VALID_USERNAME)
            .build();

        activeAccount.setProfile(profile);
    }

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("should create LISTENER account and return token pair on success")
        void register_success() {
            RegisterRequest request = new RegisterRequest(VALID_EMAIL, VALID_USERNAME, VALID_PASSWORD);

            when(accountRepository.existsByEmail(VALID_EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn(HASHED_PASSWORD);
            when(accountRepository.save(any(UserAccountEntity.class))).thenReturn(activeAccount);
            when(jwtService.generateAccessToken(any())).thenReturn(ACCESS_TOKEN);
            when(jwtProperties.getRefreshTokenExpiryMs()).thenReturn(604_800_000L);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.getAccessTokenExpirySeconds()).thenReturn(EXPIRY_SECONDS);

            // act
            LoginResponse response = authService.register(request);

            // assert
            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.role()).isEqualTo("listener");
            assertThat(response.expiresIn()).isEqualTo(EXPIRY_SECONDS);

            verify(accountRepository).existsByEmail(VALID_EMAIL);
            verify(passwordEncoder).encode(VALID_PASSWORD);
            verify(accountRepository).save(argThat(acc ->
                acc.getEmail().equals(VALID_EMAIL) &&
                acc.getRole() == Role.LISTENER &&
                acc.isActive() &&
                acc.getProfile() != null &&
                acc.getProfile().getProfileImageAssetId() == null
            ));
            verify(refreshTokenRepository).save(any(RefreshTokenEntity.class));
        }

        @Test
        @DisplayName("should throw EmailAlreadyExistsException when email is taken")
        void register_duplicateEmail_throwsException() {
            RegisterRequest request = new RegisterRequest(VALID_EMAIL, VALID_USERNAME, VALID_PASSWORD);
            when(accountRepository.existsByEmail(VALID_EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining(VALID_EMAIL);

            verify(accountRepository, never()).save(any());
            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        @DisplayName("should always assign LISTENER role regardless of other inputs")
        void register_alwaysAssignsListenerRole() {
            RegisterRequest request = new RegisterRequest(VALID_EMAIL, VALID_USERNAME, VALID_PASSWORD);
            when(accountRepository.existsByEmail(VALID_EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn(HASHED_PASSWORD);
            when(accountRepository.save(any())).thenReturn(activeAccount);
            when(jwtService.generateAccessToken(any())).thenReturn(ACCESS_TOKEN);
            when(jwtProperties.getRefreshTokenExpiryMs()).thenReturn(604_800_000L);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.getAccessTokenExpirySeconds()).thenReturn(EXPIRY_SECONDS);

            LoginResponse response = authService.register(request);

            assertThat(response.role()).isEqualTo("listener");
            verify(accountRepository).save(argThat(acc -> acc.getRole() == Role.LISTENER));
        }
    }

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("should return token pair when credentials are valid")
        void login_success() {
            LoginRequest request = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);

            when(accountRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(activeAccount));
            when(passwordEncoder.matches(VALID_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
            when(jwtService.generateAccessToken(activeAccount)).thenReturn(ACCESS_TOKEN);
            when(jwtProperties.getRefreshTokenExpiryMs()).thenReturn(604_800_000L);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.getAccessTokenExpirySeconds()).thenReturn(EXPIRY_SECONDS);

            LoginResponse response = authService.login(request);

            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.role()).isEqualTo("listener");
            assertThat(response.expiresIn()).isEqualTo(EXPIRY_SECONDS);
        }

        @Test
        @DisplayName("should throw InvalidCredentialsException when email does not exist")
        void login_unknownEmail_throwsException() {
            LoginRequest request = new LoginRequest("nobody@example.com", VALID_PASSWORD);
            when(accountRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);

            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        @DisplayName("should throw InvalidCredentialsException when password is wrong")
        void login_wrongPassword_throwsException() {
            LoginRequest request = new LoginRequest(VALID_EMAIL, "WrongPassword!");
            when(accountRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(activeAccount));
            when(passwordEncoder.matches("WrongPassword!", HASHED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);

            verify(jwtService, never()).generateAccessToken(any());
        }

        @Test
        @DisplayName("should throw InvalidCredentialsException when account is inactive")
        void login_inactiveAccount_throwsException() {
            UserAccountEntity inactiveAccount = UserAccountEntity.builder()
                .id(UUID.randomUUID())
                .email(VALID_EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .role(Role.LISTENER)
                .isActive(false)
                .build();

            LoginRequest request = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);
            when(accountRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(inactiveAccount));

            assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);

            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        @DisplayName("should not reveal whether email exists (same exception for both failure modes)")
        void login_sameExceptionForUnknownAndWrongPassword() {
            LoginRequest withUnknownEmail = new LoginRequest("ghost@example.com", VALID_PASSWORD);
            LoginRequest withWrongPass    = new LoginRequest(VALID_EMAIL, "BadPass!");

            when(accountRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
            when(accountRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(activeAccount));
            when(passwordEncoder.matches("BadPass!", HASHED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> authService.login(withUnknownEmail))
                .isInstanceOf(InvalidCredentialsException.class);
            assertThatThrownBy(() -> authService.login(withWrongPass))
                .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        private RefreshTokenEntity validRefreshToken() {
            return RefreshTokenEntity.builder()
                .id(UUID.randomUUID())
                .account(activeAccount)
                .tokenValue(REFRESH_TOKEN)
                .expiresAt(Instant.now().plusSeconds(3600))
                .isRevoked(false)
                .build();
        }

        @Test
        @DisplayName("should rotate tokens and return new pair on success")
        void refresh_success_rotatesToken() {
            RefreshTokenEntity stored = validRefreshToken();
            when(refreshTokenRepository.findByTokenValue(REFRESH_TOKEN)).thenReturn(Optional.of(stored));
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken(activeAccount)).thenReturn(ACCESS_TOKEN);
            when(jwtProperties.getRefreshTokenExpiryMs()).thenReturn(604_800_000L);
            when(jwtService.getAccessTokenExpirySeconds()).thenReturn(EXPIRY_SECONDS);

            LoginResponse response = authService.refresh(REFRESH_TOKEN);

            assertThat(stored.isRevoked()).isTrue();
            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.refreshToken()).isNotEqualTo(REFRESH_TOKEN); // rotated

            verify(refreshTokenRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("should throw InvalidRefreshTokenException when token does not exist")
        void refresh_unknownToken_throwsException() {
            when(refreshTokenRepository.findByTokenValue("nonexistent-token"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh("nonexistent-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("should throw InvalidRefreshTokenException when token is revoked")
        void refresh_revokedToken_throwsException() {
            RefreshTokenEntity revoked = RefreshTokenEntity.builder()
                .id(UUID.randomUUID())
                .account(activeAccount)
                .tokenValue(REFRESH_TOKEN)
                .expiresAt(Instant.now().plusSeconds(3600))
                .isRevoked(true)
                .build();

            when(refreshTokenRepository.findByTokenValue(REFRESH_TOKEN)).thenReturn(Optional.of(revoked));

            assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);

            verify(jwtService, never()).generateAccessToken(any());
        }

        @Test
        @DisplayName("should throw InvalidRefreshTokenException when token is expired")
        void refresh_expiredToken_throwsException() {
            RefreshTokenEntity expired = RefreshTokenEntity.builder()
                .id(UUID.randomUUID())
                .account(activeAccount)
                .tokenValue(REFRESH_TOKEN)
                .expiresAt(Instant.now().minusSeconds(3600))
                .isRevoked(false)
                .build();

            when(refreshTokenRepository.findByTokenValue(REFRESH_TOKEN)).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);

            verify(jwtService, never()).generateAccessToken(any());
        }

        @Test
        @DisplayName("should throw InvalidRefreshTokenException when token is blank")
        void refresh_blankToken_throwsException() {
            assertThatThrownBy(() -> authService.refresh("  "))
                .isInstanceOf(InvalidRefreshTokenException.class);

            verify(refreshTokenRepository, never()).findByTokenValue(any());
        }
    }

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("should delete refresh token row when token is provided")
        void logout_deletesToken() {
            when(refreshTokenRepository.deleteByTokenValue(REFRESH_TOKEN)).thenReturn(1L);

            authService.logout(REFRESH_TOKEN);

            verify(refreshTokenRepository).deleteByTokenValue(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("should be idempotent when token is null or blank")
        void logout_blankToken_isNoop() {
            authService.logout(null);
            authService.logout(" ");

            verify(refreshTokenRepository, never()).deleteByTokenValue(any());
        }
    }
}
