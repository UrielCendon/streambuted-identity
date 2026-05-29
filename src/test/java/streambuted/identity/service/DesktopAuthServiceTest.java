package streambuted.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import streambuted.identity.config.DesktopAuthProperties;
import streambuted.identity.domain.DesktopAuthCodeEntity;
import streambuted.identity.domain.Role;
import streambuted.identity.domain.UserAccountEntity;
import streambuted.identity.dto.DesktopExchangeRequest;
import streambuted.identity.dto.DesktopHandoffCodeRequest;
import streambuted.identity.dto.LoginResponse;
import streambuted.identity.exception.DesktopAuthDisabledException;
import streambuted.identity.exception.InvalidDesktopAuthCodeException;
import streambuted.identity.exception.InvalidDesktopRedirectUriException;
import streambuted.identity.repository.DesktopAuthCodeRepository;
import streambuted.identity.repository.UserAccountRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("DesktopAuthService tests")
class DesktopAuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-17T00:00:00Z");
    private static final String VALID_STATE = "abcdefghijklmnopqrstuvwxyz012345";
    private static final String VALID_CODE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ012345";
    private static final String REDIRECT_URI = "streambuted://auth/callback";

    private final DesktopAuthCodeRepository desktopAuthCodeRepository = mock(DesktopAuthCodeRepository.class);
    private final UserAccountRepository accountRepository = mock(UserAccountRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final DesktopAuthProperties properties = new DesktopAuthProperties();
    private DesktopAuthService service;
    private UserAccountEntity account;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setAllowedRedirectUri(REDIRECT_URI);
        properties.setCodeTtlSeconds(300);
        service = new DesktopAuthService(
            properties,
            desktopAuthCodeRepository,
            accountRepository,
            authService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        account = UserAccountEntity.builder()
            .id(UUID.randomUUID())
            .email("listener@example.com")
            .passwordHash("$2a$12$012345678901234567890u012345678901234567890123456789012")
            .role(Role.LISTENER)
            .build();
    }

    @Test
    @DisplayName("should reject desktop auth when feature flag is disabled")
    void ensureEnabled_rejectsWhenDisabled() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.ensureEnabled())
            .isInstanceOf(DesktopAuthDisabledException.class);
    }

    @Test
    @DisplayName("should reject handoff requests with invalid state")
    void createHandoffCode_rejectsInvalidState() {
        assertThatThrownBy(() -> service.createHandoffCode(
            account.getId(),
            new DesktopHandoffCodeRequest("bad state with spaces", REDIRECT_URI)
        )).isInstanceOf(InvalidDesktopAuthCodeException.class);

        verifyNoInteractions(accountRepository, desktopAuthCodeRepository);
    }

    @Test
    @DisplayName("should reject handoff requests with non-exact redirect URI")
    void createHandoffCode_rejectsInvalidRedirectUri() {
        assertThatThrownBy(() -> service.createHandoffCode(
            account.getId(),
            new DesktopHandoffCodeRequest(VALID_STATE, "streambuted://auth/other")
        )).isInstanceOf(InvalidDesktopRedirectUriException.class);

        verifyNoInteractions(accountRepository, desktopAuthCodeRepository);
    }

    @Test
    @DisplayName("should store only hashed code and state for handoff")
    void createHandoffCode_storesHashedCodeAndState() {
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        var response = service.createHandoffCode(
            account.getId(),
            new DesktopHandoffCodeRequest(VALID_STATE, REDIRECT_URI)
        );

        verify(desktopAuthCodeRepository).save(argThat(savedCode ->
            savedCode.getCodeHash() != null &&
                !savedCode.getCodeHash().equals(response.code()) &&
                savedCode.getStateHash().equals(hashValue(VALID_STATE)) &&
                !savedCode.getStateHash().equals(VALID_STATE) &&
                savedCode.getExpiresAt().equals(NOW.plusSeconds(300))
        ));
    }

    @Test
    @DisplayName("should reject exchange with incorrect state")
    void exchange_rejectsIncorrectState() {
        when(desktopAuthCodeRepository.findByCodeHash(hashValue(VALID_CODE)))
            .thenReturn(Optional.of(authCode(VALID_CODE, VALID_STATE, NOW.plusSeconds(300), null)));

        assertThatThrownBy(() -> service.exchange(new DesktopExchangeRequest(VALID_CODE, "abcdefghijklmnopqrstuvwxyz999999")))
            .isInstanceOf(InvalidDesktopAuthCodeException.class);

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("should reject expired handoff code")
    void exchange_rejectsExpiredCode() {
        when(desktopAuthCodeRepository.findByCodeHash(hashValue(VALID_CODE)))
            .thenReturn(Optional.of(authCode(VALID_CODE, VALID_STATE, NOW.minusSeconds(1), null)));

        assertThatThrownBy(() -> service.exchange(new DesktopExchangeRequest(VALID_CODE, VALID_STATE)))
            .isInstanceOf(InvalidDesktopAuthCodeException.class);

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("should reject reused handoff code")
    void exchange_rejectsReusedCode() {
        when(desktopAuthCodeRepository.findByCodeHash(hashValue(VALID_CODE)))
            .thenReturn(Optional.of(authCode(VALID_CODE, VALID_STATE, NOW.plusSeconds(300), NOW.minusSeconds(1))));

        assertThatThrownBy(() -> service.exchange(new DesktopExchangeRequest(VALID_CODE, VALID_STATE)))
            .isInstanceOf(InvalidDesktopAuthCodeException.class);

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("should mark handoff code as used before issuing a desktop session")
    void exchange_marksCodeUsed() {
        DesktopAuthCodeEntity authCode = authCode(VALID_CODE, VALID_STATE, NOW.plusSeconds(300), null);
        when(desktopAuthCodeRepository.findByCodeHash(hashValue(VALID_CODE))).thenReturn(Optional.of(authCode));
        when(authService.issueSessionForAccount(account.getId()))
            .thenReturn(new LoginResponse("access-token", "refresh-token", "listener", 900));

        LoginResponse response = service.exchange(new DesktopExchangeRequest(VALID_CODE, VALID_STATE));

        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(authCode.getUsedAt()).isEqualTo(NOW);
        verify(desktopAuthCodeRepository).save(authCode);
    }

    private DesktopAuthCodeEntity authCode(String code, String state, Instant expiresAt, Instant usedAt) {
        return DesktopAuthCodeEntity.builder()
            .account(account)
            .codeHash(hashValue(code))
            .stateHash(hashValue(state))
            .redirectUri(REDIRECT_URI)
            .expiresAt(expiresAt)
            .usedAt(usedAt)
            .build();
    }

    private static String hashValue(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
