package streambuted.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import streambuted.identity.domain.Role;
import streambuted.identity.domain.UserAccountEntity;
import streambuted.identity.domain.UserProfileEntity;
import streambuted.identity.repository.UserAccountRepository;
import streambuted.identity.repository.UserProfileRepository;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Provisions the first administrator from environment-backed configuration.
 * This keeps privileged credentials out of source code and lets deployments
 * rotate or disable the bootstrap path after the account exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAccountBootstrap {

    private static final Pattern PASSWORD_UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern PASSWORD_DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern PASSWORD_SPECIAL = Pattern.compile(".*[^A-Za-z0-9].*");

    private final UserAccountRepository accountRepository;
    private final UserProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.bootstrap.enabled:false}")
    private boolean enabled;

    @Value("${app.admin.bootstrap.email:}")
    private String configuredEmail;

    @Value("${app.admin.bootstrap.username:}")
    private String configuredUsername;

    @Value("${app.admin.bootstrap.password:}")
    private String configuredPassword;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void provisionAdminAccount() {
        if (!enabled) {
            return;
        }

        String email = normalizeEmail(configuredEmail);
        String username = configuredUsername == null ? "" : configuredUsername.trim();
        String password = configuredPassword == null ? "" : configuredPassword;
        validateBootstrapConfiguration(email, username, password);

        Optional<UserAccountEntity> existingAccount = accountRepository.findByEmail(email);
        if (existingAccount.isPresent()) {
            promoteExistingAccount(existingAccount.get(), username);
            return;
        }

        UserAccountEntity account = UserAccountEntity.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(password))
            .role(Role.ADMIN)
            .isActive(true)
            .passwordSetupRequired(false)
            .build();

        UserProfileEntity profile = UserProfileEntity.builder()
            .account(account)
            .username(resolveAvailableUsername(username))
            .build();
        account.setProfile(profile);

        accountRepository.save(account);
        log.info("Provisioned admin account from bootstrap configuration. email={}", email);
    }

    private void promoteExistingAccount(UserAccountEntity account, String username) {
        boolean changed = false;

        if (account.getRole() != Role.ADMIN) {
            account.setRole(Role.ADMIN);
            changed = true;
        }

        if (!account.isActive()) {
            account.setActive(true);
            changed = true;
        }

        if (account.isPasswordSetupRequired()) {
            account.setPasswordSetupRequired(false);
            changed = true;
        }

        if (account.getBannedAt() != null || account.getBannedUntil() != null || account.getBanReason() != null) {
            account.setBannedAt(null);
            account.setBannedUntil(null);
            account.setBanReason(null);
            changed = true;
        }

        if (changed) {
            accountRepository.save(account);
        }

        ensureProfile(account, username);
        log.info("Verified admin account from bootstrap configuration. email={}", account.getEmail());
    }

    private void ensureProfile(UserAccountEntity account, String username) {
        profileRepository.findByAccountId(account.getId())
            .orElseGet(() -> {
                String profileUsername = resolveAvailableUsername(username);
                UserProfileEntity profile = UserProfileEntity.builder()
                    .account(account)
                    .username(profileUsername)
                    .build();
                return profileRepository.save(profile);
            });
    }

    private String resolveAvailableUsername(String preferredUsername) {
        if (!profileRepository.existsByUsername(preferredUsername)) {
            return preferredUsername;
        }

        for (int suffix = 2; suffix < 1_000; suffix++) {
            String suffixText = "-" + suffix;
            String prefix = preferredUsername.substring(
                0,
                Math.min(preferredUsername.length(), 50 - suffixText.length())
            );
            String candidate = prefix + suffixText;
            if (!profileRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Unable to allocate an admin profile username.");
    }

    private void validateBootstrapConfiguration(String email, String username, String password) {
        if (email.isBlank() || username.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                "ADMIN_BOOTSTRAP_EMAIL, ADMIN_BOOTSTRAP_USERNAME and ADMIN_BOOTSTRAP_PASSWORD are required when admin bootstrap is enabled."
            );
        }

        if (email.length() > 320 || !email.contains("@")) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_EMAIL must be a valid email address.");
        }

        if (username.length() < 3 || username.length() > 50) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_USERNAME must be between 3 and 50 characters.");
        }

        if (password.length() < 8 || password.length() > 128) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_PASSWORD must be between 8 and 128 characters.");
        }

        if (!PASSWORD_UPPERCASE.matcher(password).matches()
            || !PASSWORD_DIGIT.matcher(password).matches()
            || !PASSWORD_SPECIAL.matcher(password).matches()) {
            throw new IllegalStateException(
                "ADMIN_BOOTSTRAP_PASSWORD must include uppercase, number and special characters."
            );
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
