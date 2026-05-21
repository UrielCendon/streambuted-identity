package streambuted.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import streambuted.identity.service.oauth.GoogleOAuthMode;
import streambuted.identity.service.oauth.GoogleUserInfo;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
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
    @Mock private UserProfileRepository  profileRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder        passwordEncoder;
    @Mock private JwtService             jwtService;
    @Mock private JwtProperties          jwtProperties;
    @Mock private RegistrationVerificationService registrationVerificationService;
    @Mock private OutboxRepository outboxRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

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
            .passwordSetupRequired(false)
            .createdAt(Instant.parse("2026-05-10T00:00:00Z"))
            .build();

        profile = UserProfileEntity.builder()
            .id(UUID.randomUUID())
            .account(activeAccount)
            .username(VALID_USERNAME)
            .build();

        activeAccount.setProfile(profile);
    }

    @Nested
    @DisplayName("email registration verification")
    class RegistrationVerificationTests {

        @Test
        @DisplayName("should start registration by hashing password and sending code without creating account")
        void startRegistration_success() {
            RegisterRequest request = new RegisterRequest(VALID_EMAIL, VALID_USERNAME, VALID_PASSWORD);
            RegistrationVerificationResponse expected = new RegistrationVerificationResponse(
                UUID.randomUUID(),
                VALID_EMAIL,
                "pending",
                900L,
                "Verification code sent."
            );

            when(accountRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.empty());
            when(profileRepository.existsByUsername(VALID_USERNAME)).thenReturn(false);
            when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn(HASHED_PASSWORD);
            when(registrationVerificationService.startRegistration(any(), eq(HASHED_PASSWORD)))
                .thenReturn(expected);

            RegistrationVerificationResponse response = authService.startRegistration(request);

            assertThat(response).isEqualTo(expected);
            verify(registrationVerificationService).startRegistration(
                eq(new RegisterRequest(VALID_EMAIL, VALID_USERNAME, VALID_PASSWORD)),
                eq(HASHED_PASSWORD)
            );
            verify(accountRepository, never()).save(any());
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw EmailAlreadyExistsException when email is taken before code is sent")
        void startRegistration_duplicateEmail_throwsException() {
            RegisterRequest request = new RegisterRequest(VALID_EMAIL, VALID_USERNAME, VALID_PASSWORD);
            when(accountRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(activeAccount));

            assertThatThrownBy(() -> authService.startRegistration(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("Registration cannot be completed");

            verify(registrationVerificationService, never()).startRegistration(any(), any());
            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        @DisplayName("should complete registration only after verification service accepts the code")
        void verifyRegistration_success() {
            UUID attemptId = UUID.randomUUID();
            RegistrationVerificationEntity attempt = RegistrationVerificationEntity.builder()
                .id(attemptId)
                .email(VALID_EMAIL)
                .username(VALID_USERNAME)
                .passwordHash(HASHED_PASSWORD)
                .status(RegistrationVerificationStatus.VERIFIED)
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

            when(registrationVerificationService.verifyCode(any())).thenReturn(attempt);
            when(accountRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.empty());
            when(profileRepository.existsByUsername(VALID_USERNAME)).thenReturn(false);
            when(accountRepository.save(any(UserAccountEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken(any())).thenReturn(ACCESS_TOKEN);
            when(jwtProperties.getRefreshTokenExpiryMs()).thenReturn(604_800_000L);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.getAccessTokenExpirySeconds()).thenReturn(EXPIRY_SECONDS);

            LoginResponse response = authService.verifyRegistration(
                new VerifyRegistrationRequest(attemptId, VALID_EMAIL, "123456")
            );

            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.role()).isEqualTo("listener");
            verify(accountRepository).save(argThat(acc ->
                acc.getEmail().equals(VALID_EMAIL) &&
                acc.getPasswordHash().equals(HASHED_PASSWORD) &&
                acc.getRole() == Role.LISTENER &&
                acc.getProfile().getUsername().equals(VALID_USERNAME)
            ));
        }

        @Test
        @DisplayName("should not create account when verification code is invalid")
        void verifyRegistration_invalidCode_doesNotCreateAccount() {
            VerifyRegistrationRequest request = new VerifyRegistrationRequest(
                UUID.randomUUID(),
                VALID_EMAIL,
                "000000"
            );
            when(registrationVerificationService.verifyCode(request))
                .thenThrow(new InvalidRegistrationVerificationException("Verification code is incorrect."));

            assertThatThrownBy(() -> authService.verifyRegistration(request))
                .isInstanceOf(InvalidRegistrationVerificationException.class);

            verify(accountRepository, never()).save(any());
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("should cancel registration verification through verification service")
        void cancelRegistration_delegates() {
            CancelRegistrationVerificationRequest request = new CancelRegistrationVerificationRequest(
                UUID.randomUUID(),
                VALID_EMAIL
            );

            authService.cancelRegistration(request);

            verify(registrationVerificationService).cancel(request);
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
            when(passwordEncoder.matches(VALID_PASSWORD, HASHED_PASSWORD)).thenReturn(true);

            assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);

            verify(jwtService, never()).generateAccessToken(any());
        }

        @Test
        @DisplayName("should throw AccountBannedException when credentials are valid but account is banned")
        void login_bannedAccount_throwsAccountBannedException() {
            Instant bannedUntil = Instant.now().plusSeconds(3600);
            UserAccountEntity bannedAccount = UserAccountEntity.builder()
                .id(UUID.randomUUID())
                .email(VALID_EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .role(Role.LISTENER)
                .isActive(false)
                .bannedAt(Instant.now().minusSeconds(60))
                .bannedUntil(bannedUntil)
                .banReason("Moderation action")
                .build();

            LoginRequest request = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);
            when(accountRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(bannedAccount));
            when(passwordEncoder.matches(VALID_PASSWORD, HASHED_PASSWORD)).thenReturn(true);

            assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AccountBannedException.class)
                .satisfies(ex -> {
                    AccountBannedException banned = (AccountBannedException) ex;
                    assertThat(banned.getBanType()).isEqualTo("TEMPORARY");
                    assertThat(banned.getBannedUntil()).isEqualTo(bannedUntil);
                    assertThat(banned.getRemainingSeconds()).isPositive();
                });

            verify(jwtService, never()).generateAccessToken(any());
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
    @DisplayName("Google auth")
    class GoogleLoginTests {

        @Test
        @DisplayName("should reject weak password during email registration")
        void startRegistration_weakPassword_throwsException() {
            RegisterRequest request = new RegisterRequest(VALID_EMAIL, VALID_USERNAME, "password1!");

            assertThatThrownBy(() -> authService.startRegistration(request))
                .isInstanceOf(PasswordPolicyException.class)
                .hasMessageContaining("uppercase");
        }

        @Test
        @DisplayName("should log in existing user without creating duplicate account")
        void googleLogin_existingEmail_doesNotDuplicateUser() {
            GoogleUserInfo googleUser = new GoogleUserInfo("google-sub-1", VALID_EMAIL, "Test User");

            when(accountRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(activeAccount));
            when(accountRepository.save(activeAccount)).thenReturn(activeAccount);
            stubTokenPair(activeAccount);

            GoogleAuthenticationResult result = authService.authenticateWithGoogle(
                googleUser,
                GoogleOAuthMode.LOGIN
            );

            assertThat(result.loginResponse().accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.passwordSetupRequired()).isFalse();
            assertThat(activeAccount.getGoogleSubject()).isEqualTo("google-sub-1");
            verify(accountRepository).findByEmail(VALID_EMAIL);
            verify(accountRepository).save(activeAccount);
            verify(profileRepository, never()).existsByUsername(any());
        }

        @Test
        @DisplayName("should create listener account when Google register email does not exist")
        void googleRegister_newEmail_createsUser() {
            GoogleUserInfo googleUser = new GoogleUserInfo(
                "google-sub-2",
                "google@example.com",
                "Google User"
            );

            when(accountRepository.findByEmail("google@example.com")).thenReturn(Optional.empty());
            when(profileRepository.existsByUsername("google_user")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn(HASHED_PASSWORD);
            when(accountRepository.save(any(UserAccountEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken(any())).thenReturn(ACCESS_TOKEN);
            when(jwtProperties.getRefreshTokenExpiryMs()).thenReturn(604_800_000L);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.getAccessTokenExpirySeconds()).thenReturn(EXPIRY_SECONDS);

            GoogleAuthenticationResult result = authService.authenticateWithGoogle(
                googleUser,
                GoogleOAuthMode.REGISTER
            );

            assertThat(result.loginResponse().accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.passwordSetupRequired()).isFalse();
            verify(accountRepository).save(argThat(acc ->
                acc.getEmail().equals("google@example.com") &&
                acc.getGoogleSubject().equals("google-sub-2") &&
                acc.getRole() == Role.LISTENER &&
                !acc.isPasswordSetupRequired() &&
                acc.getProfile().getUsername().equals("google_user")
            ));
        }

        @Test
        @DisplayName("should clear legacy Google password setup flag during Google auth")
        void googleLogin_legacyPasswordSetupFlag_clearsFlag() {
            UserAccountEntity legacyGoogleAccount = UserAccountEntity.builder()
                .id(accountId)
                .email(VALID_EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .googleSubject("google-sub-legacy")
                .role(Role.LISTENER)
                .isActive(true)
                .passwordSetupRequired(true)
                .createdAt(Instant.parse("2026-05-10T00:00:00Z"))
                .build();
            legacyGoogleAccount.setProfile(profile);

            GoogleUserInfo googleUser = new GoogleUserInfo("google-sub-legacy", VALID_EMAIL, "Test User");

            when(accountRepository.findByGoogleSubject("google-sub-legacy"))
                .thenReturn(Optional.of(legacyGoogleAccount));
            when(accountRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(legacyGoogleAccount));
            when(accountRepository.save(legacyGoogleAccount)).thenReturn(legacyGoogleAccount);
            stubTokenPair(legacyGoogleAccount);

            GoogleAuthenticationResult result = authService.authenticateWithGoogle(
                googleUser,
                GoogleOAuthMode.LOGIN
            );

            assertThat(result.passwordSetupRequired()).isFalse();
            assertThat(legacyGoogleAccount.isPasswordSetupRequired()).isFalse();
            verify(accountRepository).save(legacyGoogleAccount);
        }

        @Test
        @DisplayName("should generate a safe alternative username on conflict")
        void googleRegister_usernameConflict_generatesAlternative() {
            GoogleUserInfo googleUser = new GoogleUserInfo(
                "google-sub-3",
                "google@example.com",
                "Google User"
            );

            when(accountRepository.findByEmail("google@example.com")).thenReturn(Optional.empty());
            when(profileRepository.existsByUsername("google_user")).thenReturn(true);
            when(profileRepository.existsByUsername("google_user-2")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn(HASHED_PASSWORD);
            when(accountRepository.save(any(UserAccountEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken(any())).thenReturn(ACCESS_TOKEN);
            when(jwtProperties.getRefreshTokenExpiryMs()).thenReturn(604_800_000L);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.getAccessTokenExpirySeconds()).thenReturn(EXPIRY_SECONDS);

            authService.authenticateWithGoogle(googleUser, GoogleOAuthMode.REGISTER);

            verify(accountRepository).save(argThat(acc ->
                acc.getProfile().getUsername().equals("google_user-2")
            ));
        }

        @Test
        @DisplayName("should not create an account when Google login is used for an unknown email")
        void googleLogin_unknownEmail_throwsException() {
            GoogleUserInfo googleUser = new GoogleUserInfo("google-sub-4", "google@example.com", "Google User");
            when(accountRepository.findByEmail("google@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.authenticateWithGoogle(googleUser, GoogleOAuthMode.LOGIN))
                .isInstanceOf(GoogleAuthenticationException.class)
                .hasMessageContaining("Register with Google first");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("should prefer the oldest equivalent Gmail account and preserve its username")
        void googleLogin_equivalentGmailAccount_preservesExistingUsername() {
            UserAccountEntity originalAccount = UserAccountEntity.builder()
                .id(UUID.randomUUID())
                .email("uriel.ito2@gmail.com")
                .passwordHash(HASHED_PASSWORD)
                .role(Role.LISTENER)
                .isActive(true)
                .createdAt(Instant.parse("2026-05-10T00:00:00Z"))
                .build();
            originalAccount.setProfile(UserProfileEntity.builder()
                .id(UUID.randomUUID())
                .account(originalAccount)
                .username("urielito2")
                .build());

            UserAccountEntity duplicateGoogleAccount = UserAccountEntity.builder()
                .id(UUID.randomUUID())
                .email("urielito2@gmail.com")
                .passwordHash(HASHED_PASSWORD)
                .googleSubject("google-sub-existing")
                .role(Role.LISTENER)
                .isActive(true)
                .createdAt(Instant.parse("2026-05-11T00:00:00Z"))
                .build();
            duplicateGoogleAccount.setProfile(UserProfileEntity.builder()
                .id(UUID.randomUUID())
                .account(duplicateGoogleAccount)
                .username("uriel_cendon_diaz")
                .build());

            GoogleUserInfo googleUser = new GoogleUserInfo(
                "google-sub-existing",
                "urielito2@gmail.com",
                "Uriel Cendon Diaz"
            );

            when(accountRepository.findByGoogleSubject("google-sub-existing"))
                .thenReturn(Optional.of(duplicateGoogleAccount));
            when(accountRepository.findByEmail("urielito2@gmail.com"))
                .thenReturn(Optional.of(duplicateGoogleAccount));
            when(accountRepository.findAllByEmailEndingWithIgnoreCase("@gmail.com"))
                .thenReturn(List.of(originalAccount, duplicateGoogleAccount));
            when(accountRepository.findAllByEmailEndingWithIgnoreCase("@googlemail.com"))
                .thenReturn(List.of());
            when(accountRepository.save(any(UserAccountEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            stubTokenPair(originalAccount);

            GoogleAuthenticationResult result = authService.authenticateWithGoogle(
                googleUser,
                GoogleOAuthMode.LOGIN
            );

            assertThat(result.passwordSetupRequired()).isFalse();
            assertThat(result.loginResponse().accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(originalAccount.getGoogleSubject()).isEqualTo("google-sub-existing");
            assertThat(duplicateGoogleAccount.getGoogleSubject()).isNull();
            assertThat(originalAccount.getProfile().getUsername()).isEqualTo("urielito2");
        }

        @Test
        @DisplayName("should complete Google password setup when confirmation and policy are valid")
        void completeGooglePasswordSetup_success() {
            UserAccountEntity googleAccount = UserAccountEntity.builder()
                .id(accountId)
                .email(VALID_EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .role(Role.LISTENER)
                .isActive(true)
                .passwordSetupRequired(true)
                .build();

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(googleAccount));
            when(passwordEncoder.encode("SecurePass1!")).thenReturn("$2a$12$newhash");

            authService.completeGooglePasswordSetup(
                accountId,
                new SetupPasswordRequest("SecurePass1!", "SecurePass1!")
            );

            assertThat(googleAccount.isPasswordSetupRequired()).isFalse();
            assertThat(googleAccount.getPasswordHash()).isEqualTo("$2a$12$newhash");
            verify(accountRepository).save(googleAccount);
        }

        @Test
        @DisplayName("should reject Google password setup when confirmation does not match")
        void completeGooglePasswordSetup_confirmationMismatch() {
            UserAccountEntity googleAccount = UserAccountEntity.builder()
                .id(accountId)
                .email(VALID_EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .role(Role.LISTENER)
                .isActive(true)
                .passwordSetupRequired(true)
                .build();

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(googleAccount));

            assertThatThrownBy(() -> authService.completeGooglePasswordSetup(
                accountId,
                new SetupPasswordRequest("SecurePass1!", "SecurePass2!")
            ))
                .isInstanceOf(PasswordPolicyException.class)
                .hasMessageContaining("confirmation");
        }
    }

    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        private RefreshTokenEntity validRefreshToken() {
            return RefreshTokenEntity.builder()
                .id(UUID.randomUUID())
                .account(activeAccount)
                .tokenValue(hashRefreshToken(REFRESH_TOKEN))
                .expiresAt(Instant.now().plusSeconds(3600))
                .isRevoked(false)
                .build();
        }

        @Test
        @DisplayName("should rotate tokens and return new pair on success")
        void refresh_success_rotatesToken() {
            RefreshTokenEntity stored = validRefreshToken();
            when(refreshTokenRepository.findByTokenValue(hashRefreshToken(REFRESH_TOKEN))).thenReturn(Optional.of(stored));
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
            when(refreshTokenRepository.findByTokenValue(hashRefreshToken("nonexistent-token")))
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
                .tokenValue(hashRefreshToken(REFRESH_TOKEN))
                .expiresAt(Instant.now().plusSeconds(3600))
                .isRevoked(true)
                .build();

            when(refreshTokenRepository.findByTokenValue(hashRefreshToken(REFRESH_TOKEN))).thenReturn(Optional.of(revoked));

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
                .tokenValue(hashRefreshToken(REFRESH_TOKEN))
                .expiresAt(Instant.now().minusSeconds(3600))
                .isRevoked(false)
                .build();

            when(refreshTokenRepository.findByTokenValue(hashRefreshToken(REFRESH_TOKEN))).thenReturn(Optional.of(expired));

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
            when(refreshTokenRepository.deleteByTokenValue(hashRefreshToken(REFRESH_TOKEN))).thenReturn(1L);

            authService.logout(REFRESH_TOKEN);

            verify(refreshTokenRepository).deleteByTokenValue(hashRefreshToken(REFRESH_TOKEN));
        }

        @Test
        @DisplayName("should be idempotent when token is null or blank")
        void logout_blankToken_isNoop() {
            authService.logout(null);
            authService.logout(" ");

            verify(refreshTokenRepository, never()).deleteByTokenValue(any());
        }
    }

    private void stubTokenPair(UserAccountEntity account) {
        when(jwtService.generateAccessToken(account)).thenReturn(ACCESS_TOKEN);
        when(jwtProperties.getRefreshTokenExpiryMs()).thenReturn(604_800_000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.getAccessTokenExpirySeconds()).thenReturn(EXPIRY_SECONDS);
    }

    private static String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
