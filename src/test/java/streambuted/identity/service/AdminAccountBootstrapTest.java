package streambuted.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import streambuted.identity.domain.Role;
import streambuted.identity.domain.UserAccountEntity;
import streambuted.identity.repository.UserAccountRepository;
import streambuted.identity.repository.UserProfileRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAccountBootstrap Unit Tests")
class AdminAccountBootstrapTest {

    @Mock private UserAccountRepository accountRepository;
    @Mock private UserProfileRepository profileRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("should create admin account from environment-backed configuration")
    void provisionAdminAccount_createsAdmin() {
        AdminAccountBootstrap bootstrap = buildBootstrap(true, "Admin@StreamButed.com", "admin_user", "SecureAdmin1!");
        when(accountRepository.findByEmail("admin@streambuted.com")).thenReturn(Optional.empty());
        when(profileRepository.existsByUsername("admin_user")).thenReturn(false);
        when(passwordEncoder.encode("SecureAdmin1!")).thenReturn("$2a$12$adminhash");

        bootstrap.provisionAdminAccount();

        ArgumentCaptor<UserAccountEntity> captor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(accountRepository).save(captor.capture());

        UserAccountEntity savedAccount = captor.getValue();
        assertThat(savedAccount.getEmail()).isEqualTo("admin@streambuted.com");
        assertThat(savedAccount.getPasswordHash()).isEqualTo("$2a$12$adminhash");
        assertThat(savedAccount.getRole()).isEqualTo(Role.ADMIN);
        assertThat(savedAccount.isActive()).isTrue();
        assertThat(savedAccount.getProfile().getUsername()).isEqualTo("admin_user");
    }

    @Test
    @DisplayName("should promote existing configured account to admin")
    void provisionAdminAccount_promotesExistingAccount() {
        UserAccountEntity account = UserAccountEntity.builder()
            .id(UUID.randomUUID())
            .email("admin@streambuted.com")
            .passwordHash("$2a$12$hash")
            .role(Role.LISTENER)
            .isActive(false)
            .passwordSetupRequired(true)
            .build();
        AdminAccountBootstrap bootstrap = buildBootstrap(true, "admin@streambuted.com", "admin_user", "SecureAdmin1!");

        when(accountRepository.findByEmail("admin@streambuted.com")).thenReturn(Optional.of(account));
        when(profileRepository.findByAccountId(account.getId())).thenReturn(Optional.empty());
        when(profileRepository.existsByUsername("admin_user")).thenReturn(false);
        when(passwordEncoder.matches("SecureAdmin1!", "$2a$12$hash")).thenReturn(true);

        bootstrap.provisionAdminAccount();

        assertThat(account.getRole()).isEqualTo(Role.ADMIN);
        assertThat(account.isActive()).isTrue();
        assertThat(account.isPasswordSetupRequired()).isFalse();
        verify(accountRepository).save(account);
        verify(profileRepository).save(any());
    }

    @Test
    @DisplayName("should update password hash for an existing admin when bootstrap password changes")
    void provisionAdminAccount_updatesExistingPasswordHash() {
        UserAccountEntity account = UserAccountEntity.builder()
            .id(UUID.randomUUID())
            .email("admin@streambuted.com")
            .passwordHash("$2a$12$oldhash")
            .role(Role.ADMIN)
            .isActive(true)
            .passwordSetupRequired(false)
            .build();
        AdminAccountBootstrap bootstrap = buildBootstrap(true, "admin@streambuted.com", "admin_user", "SecureAdmin1!");

        when(accountRepository.findByEmail("admin@streambuted.com")).thenReturn(Optional.of(account));
        when(profileRepository.findByAccountId(account.getId())).thenReturn(Optional.ofNullable(account.getProfile()));
        when(passwordEncoder.matches("SecureAdmin1!", "$2a$12$oldhash")).thenReturn(false);
        when(passwordEncoder.encode("SecureAdmin1!")).thenReturn("$2a$12$newhash");

        bootstrap.provisionAdminAccount();

        assertThat(account.getPasswordHash()).isEqualTo("$2a$12$newhash");
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("should skip provisioning when bootstrap is disabled")
    void provisionAdminAccount_disabledSkipsWork() {
        AdminAccountBootstrap bootstrap = buildBootstrap(false, "", "", "");

        bootstrap.provisionAdminAccount();

        verify(accountRepository, never()).findByEmail(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("should fail fast when enabled without complete configuration")
    void provisionAdminAccount_missingConfigThrows() {
        AdminAccountBootstrap bootstrap = buildBootstrap(true, "admin@streambuted.com", "admin_user", "");

        assertThatThrownBy(bootstrap::provisionAdminAccount)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD");
    }

    @Test
    @DisplayName("should fail fast when bootstrap password has leading or trailing spaces")
    void provisionAdminAccount_passwordWithOuterSpacesThrows() {
        AdminAccountBootstrap bootstrap = buildBootstrap(true, "admin@streambuted.com", "admin_user", "SecureAdmin1! ");

        assertThatThrownBy(bootstrap::provisionAdminAccount)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("leading or trailing spaces");
    }

    @Test
    @DisplayName("should fail fast when bootstrap password exceeds the shared 15 character limit")
    void provisionAdminAccount_passwordTooLongThrows() {
        AdminAccountBootstrap bootstrap = buildBootstrap(true, "admin@streambuted.com", "admin_user", "R7!mV4xqA2pZ9sL0");

        assertThatThrownBy(bootstrap::provisionAdminAccount)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("between 8 and 15 characters");
    }

    private AdminAccountBootstrap buildBootstrap(
        boolean enabled,
        String email,
        String username,
        String password
    ) {
        AdminAccountBootstrap bootstrap = new AdminAccountBootstrap(
            accountRepository,
            profileRepository,
            passwordEncoder
        );
        ReflectionTestUtils.setField(bootstrap, "enabled", enabled);
        ReflectionTestUtils.setField(bootstrap, "configuredEmail", email);
        ReflectionTestUtils.setField(bootstrap, "configuredUsername", username);
        ReflectionTestUtils.setField(bootstrap, "configuredPassword", password);
        return bootstrap;
    }
}
