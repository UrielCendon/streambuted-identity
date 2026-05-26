package streambuted.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import streambuted.identity.domain.*;
import streambuted.identity.dto.*;
import streambuted.identity.exception.*;
import streambuted.identity.repository.*;
import streambuted.identity.security.JwtProperties;
import streambuted.identity.security.JwtService;
import streambuted.identity.messaging.UserLoggedInEvent;
import streambuted.identity.service.oauth.GoogleOAuthMode;
import streambuted.identity.service.oauth.GoogleUserInfo;

import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Handles user registration, login, and refresh-token rotation.
 *
 * Token rotation strategy: on every /auth/refresh call the old refresh token
 * is revoked and a new one is issued. This limits the blast radius if a
 * refresh token is stolen, without requiring server-side session state.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserAccountRepository accountRepository;
    private final UserProfileRepository  profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder        passwordEncoder;
    private final JwtService             jwtService;
    private final JwtProperties          jwtProperties;
    private final RegistrationVerificationService registrationVerificationService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    private static final Pattern NON_USERNAME_CHARS = Pattern.compile("[^a-z0-9._-]");
    private static final int USERNAME_MAX_LENGTH = 100;
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 15;
    private static final Pattern PASSWORD_UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern PASSWORD_DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern PASSWORD_SPECIAL = Pattern.compile(".*[^A-Za-z0-9].*");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Registration

    @Override
    public RegistrationVerificationResponse startRegistration(RegisterRequest request) {
        RegisterRequest normalizedRequest = normalizeRegisterRequest(request);
        validatePasswordPolicy(normalizedRequest.password());
        assertEmailAvailable(normalizedRequest.email());
        assertUsernameAvailable(normalizedRequest.username());

        String passwordHash = passwordEncoder.encode(normalizedRequest.password());
        return registrationVerificationService.startRegistration(normalizedRequest, passwordHash);
    }

    @Override
    public RegistrationVerificationResponse resendRegistrationCode(
        ResendRegistrationCodeRequest request
    ) {
        assertEmailAvailable(normalizeEmail(request.email()));
        return registrationVerificationService.resendCode(request);
    }

    @Override
    public LoginResponse verifyRegistration(VerifyRegistrationRequest request) {
        RegistrationVerificationEntity attempt = registrationVerificationService.verifyCode(request);
        assertEmailAvailable(attempt.getEmail());
        assertUsernameAvailable(attempt.getUsername());

        UserAccountEntity account = UserAccountEntity.builder()
            .email(attempt.getEmail())
            .passwordHash(attempt.getPasswordHash())
            .role(Role.LISTENER)
            .isActive(true)
            .build();

        UserProfileEntity profile = UserProfileEntity.builder()
            .account(account)
            .username(attempt.getUsername())
            .build();

        account.setProfile(profile);

        accountRepository.save(account);
        log.info("Verified registration for email={}, id={}", account.getEmail(), account.getId());

        LoginResponse response = buildTokenPair(account);
        enqueueUserLoggedInEvent(account.getId());
        return response;
    }

    @Override
    public void cancelRegistration(CancelRegistrationVerificationRequest request) {
        registrationVerificationService.cancel(request);
    }

    // Login

    @Override
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        UserAccountEntity account = resolvePrimaryAccountByEmail(normalizedEmail)
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        ensureAccountCanAuthenticate(account);

        log.info("Successful login for userId={}", account.getId());
        LoginResponse response = buildTokenPair(account);
        enqueueUserLoggedInEvent(account.getId());
        return response;
    }

    @Override
    public GoogleAuthenticationResult authenticateWithGoogle(
        GoogleUserInfo googleUserInfo,
        GoogleOAuthMode mode
    ) {
        String email = normalizeEmail(googleUserInfo.email());
        Optional<UserAccountEntity> accountBySubject = accountRepository.findByGoogleSubject(
            googleUserInfo.subject()
        );
        List<UserAccountEntity> equivalentAccounts = findEquivalentAccountsByEmail(email);

        UserAccountEntity account = resolveGoogleAccount(
            googleUserInfo,
            mode,
            email,
            accountBySubject,
            equivalentAccounts
        );
        account = allowGoogleSignInWithoutPasswordSetup(account);

        ensureAccountCanAuthenticate(account);

        log.info("Successful Google auth for userId={}", account.getId());
        LoginResponse response = buildTokenPair(account);
        enqueueUserLoggedInEvent(account.getId());
        return new GoogleAuthenticationResult(response, account.isPasswordSetupRequired());
    }

    @Override
    public void completeGooglePasswordSetup(UUID userId, SetupPasswordRequest request) {
        UserAccountEntity account = accountRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        if (!account.isPasswordSetupRequired()) {
            throw new PasswordSetupNotRequiredException();
        }

        validatePasswordPolicy(request.password());
        if (!request.password().equals(request.confirmPassword())) {
            throw new PasswordPolicyException("Las contrasenas no coinciden.");
        }

        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setPasswordSetupRequired(false);
        accountRepository.save(account);
    }

    // Refresh

    @Override
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        String tokenValue = hashRefreshToken(refreshToken.trim());
        RefreshTokenEntity storedToken = refreshTokenRepository
            .findByTokenValue(tokenValue)
            .orElseThrow(InvalidRefreshTokenException::new);

        if (!storedToken.isValid()) {
            throw new InvalidRefreshTokenException();
        }

        UserAccountEntity account = storedToken.getAccount();
        ensureAccountCanAuthenticate(account);

        // Revoke the used token to prevent replay attacks.
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        log.info("Refresh token rotated for userId={}", account.getId());
        return buildTokenPair(account);
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        String tokenValue = hashRefreshToken(refreshToken.trim());
        long deletedRows = refreshTokenRepository.deleteByTokenValue(tokenValue);
        log.info("Logout completed. Invalidated refresh token rows={}", deletedRows);
    }

    @Override
    public ValidatedTokenResponse validateAccessToken(String token) {
        UUID userId = jwtService.extractClaims(token)
            .map(Claims::getSubject)
            .map(UUID::fromString)
            .orElseThrow(InvalidAccessTokenException::new);

        UserAccountEntity account = accountRepository.findById(userId)
            .orElseThrow(InvalidAccessTokenException::new);

        ensureAccountCanAuthenticate(account);

        return new ValidatedTokenResponse(
            account.getId().toString(),
            account.getRole().name().toLowerCase(),
            account.getEmail(),
            account.isActive()
        );
    }

    // Private helpers

    private RegisterRequest normalizeRegisterRequest(RegisterRequest request) {
        return new RegisterRequest(
            normalizeEmail(request.email()),
            request.username().trim(),
            request.password()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void assertEmailAvailable(String email) {
        if (!findEquivalentAccountsByEmail(email).isEmpty()) {
            throw new EmailAlreadyExistsException(email);
        }
    }

    private void assertUsernameAvailable(String username) {
        if (profileRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }
    }

    private void ensureAccountCanAuthenticate(UserAccountEntity account) {
        if (account.isActive()) {
            return;
        }

        Instant bannedUntil = account.getBannedUntil();
        if (bannedUntil != null && !Instant.now().isBefore(bannedUntil)) {
            account.setActive(true);
            account.setBannedAt(null);
            account.setBannedUntil(null);
            account.setBanReason(null);
            accountRepository.save(account);
            return;
        }

        if (account.getBannedAt() != null) {
            throw new AccountBannedException(bannedUntil, account.getBanReason());
        }

        throw new InvalidCredentialsException();
    }

    private UserAccountEntity linkGoogleSubject(
        UserAccountEntity account,
        String googleSubject
    ) {
        if (googleSubject.equals(account.getGoogleSubject())) {
            return account;
        }

        if (account.getGoogleSubject() == null || account.getGoogleSubject().isBlank()) {
            account.setGoogleSubject(googleSubject);
            return accountRepository.save(account);
        }

        return account;
    }

    private UserAccountEntity createGoogleAccount(GoogleUserInfo googleUserInfo, String email) {
        String username = generateGoogleUsername(googleUserInfo.name(), email);
        String generatedPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());

        UserAccountEntity account = UserAccountEntity.builder()
            .email(email)
            .passwordHash(generatedPasswordHash)
            .googleSubject(googleUserInfo.subject())
            .role(Role.LISTENER)
            .isActive(true)
            .passwordSetupRequired(false)
            .build();

        UserProfileEntity profile = UserProfileEntity.builder()
            .account(account)
            .username(username)
            .build();

        account.setProfile(profile);
        return accountRepository.save(account);
    }

    private String generateGoogleUsername(String displayName, String email) {
        String base = displayName == null || displayName.isBlank()
            ? email.substring(0, email.indexOf('@'))
            : displayName;

        base = Normalizer.normalize(base, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim()
            .replaceAll("\\s+", "_");
        base = NON_USERNAME_CHARS.matcher(base).replaceAll("");

        if (base.length() < 3) {
            base = "user_" + base;
        }

        base = base.substring(0, Math.min(base.length(), USERNAME_MAX_LENGTH));

        if (!profileRepository.existsByUsername(base)) {
            return base;
        }

        for (int suffix = 2; suffix < 1_000; suffix++) {
            String suffixText = "-" + suffix;
            String prefix = base.substring(0, Math.min(base.length(), USERNAME_MAX_LENGTH - suffixText.length()));
            String candidate = prefix + suffixText;
            if (!profileRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }

        throw new UsernameAlreadyExistsException(base);
    }

    private void validatePasswordPolicy(String password) {
        if (password == null || password.isBlank()) {
            throw new PasswordPolicyException("La contrasena es obligatoria.");
        }

        if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            throw new PasswordPolicyException("La contrasena debe tener entre 8 y 15 caracteres.");
        }

        if (!PASSWORD_UPPERCASE.matcher(password).matches()) {
            throw new PasswordPolicyException("La contrasena debe incluir al menos una mayuscula.");
        }

        if (!PASSWORD_DIGIT.matcher(password).matches()) {
            throw new PasswordPolicyException("La contrasena debe incluir al menos un numero.");
        }

        if (!PASSWORD_SPECIAL.matcher(password).matches()) {
            throw new PasswordPolicyException("La contrasena debe incluir al menos un simbolo especial.");
        }
    }

    private UserAccountEntity resolveGoogleAccount(
        GoogleUserInfo googleUserInfo,
        GoogleOAuthMode mode,
        String normalizedEmail,
        Optional<UserAccountEntity> accountBySubject,
        List<UserAccountEntity> equivalentAccounts
    ) {
        Optional<UserAccountEntity> primaryAccount = selectPrimaryAccount(equivalentAccounts);

        if (primaryAccount.isPresent()) {
            UserAccountEntity primary = primaryAccount.get();
            accountBySubject
                .filter(existing -> !existing.getId().equals(primary.getId()))
                .ifPresent(this::detachGoogleSubject);
            return linkGoogleSubject(primary, googleUserInfo.subject());
        }

        if (accountBySubject.isPresent()) {
            return accountBySubject.get();
        }

        if (mode == GoogleOAuthMode.LOGIN) {
            throw new GoogleAuthenticationException(
                "No hay una cuenta vinculada a este correo. Registrate con Google primero."
            );
        }

        return createGoogleAccount(googleUserInfo, normalizedEmail);
    }

    private UserAccountEntity allowGoogleSignInWithoutPasswordSetup(UserAccountEntity account) {
        if (!account.isPasswordSetupRequired()) {
            return account;
        }

        account.setPasswordSetupRequired(false);
        return accountRepository.save(account);
    }

    private void detachGoogleSubject(UserAccountEntity account) {
        account.setGoogleSubject(null);
        accountRepository.save(account);
    }

    private Optional<UserAccountEntity> resolvePrimaryAccountByEmail(String normalizedEmail) {
        return selectPrimaryAccount(findEquivalentAccountsByEmail(normalizedEmail));
    }

    private Optional<UserAccountEntity> selectPrimaryAccount(List<UserAccountEntity> accounts) {
        return accounts.stream()
            .sorted(
                Comparator
                    .comparing(UserAccountEntity::isPasswordSetupRequired)
                    .thenComparing(
                        UserAccountEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                    )
                    .thenComparing(UserAccountEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()))
            )
            .findFirst();
    }

    private List<UserAccountEntity> findEquivalentAccountsByEmail(String normalizedEmail) {
        Optional<UserAccountEntity> exact = accountRepository.findByEmail(normalizedEmail);
        if (!isGmailAddress(normalizedEmail)) {
            return exact.stream().toList();
        }

        String canonicalEmail = canonicalizeEmail(normalizedEmail);
        List<UserAccountEntity> gmailAccounts = accountRepository.findAllByEmailEndingWithIgnoreCase(
            "@gmail.com"
        );
        List<UserAccountEntity> googlemailAccounts = accountRepository.findAllByEmailEndingWithIgnoreCase(
            "@googlemail.com"
        );

        return Stream.concat(
                Stream.concat(exact.stream(), gmailAccounts.stream()),
                googlemailAccounts.stream()
            )
            .filter(account -> canonicalizeEmail(normalizeEmail(account.getEmail())).equals(canonicalEmail))
            .distinct()
            .toList();
    }

    private boolean isGmailAddress(String normalizedEmail) {
        return normalizedEmail.endsWith("@gmail.com") || normalizedEmail.endsWith("@googlemail.com");
    }

    private String canonicalizeEmail(String normalizedEmail) {
        int atIndex = normalizedEmail.indexOf('@');
        if (atIndex < 0) {
            return normalizedEmail;
        }

        String localPart = normalizedEmail.substring(0, atIndex);
        if (!isGmailAddress(normalizedEmail)) {
            return normalizedEmail;
        }

        int plusIndex = localPart.indexOf('+');
        if (plusIndex >= 0) {
            localPart = localPart.substring(0, plusIndex);
        }

        localPart = localPart.replace(".", "");
        return localPart + "@gmail.com";
    }

    /**
     * Issues a fresh JWT + refresh token pair and persists the refresh token.
     * Called after both successful login and refresh operations.
     */
    private LoginResponse buildTokenPair(UserAccountEntity account) {
        String accessToken = jwtService.generateAccessToken(account);
        String refreshValue = generateRefreshToken();
        String refreshHash = hashRefreshToken(refreshValue);
        Instant expiresAt = Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiryMs());

        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
            .account(account)
            .tokenValue(refreshHash)
            .expiresAt(expiresAt)
            .isRevoked(false)
            .build();

        refreshTokenRepository.save(refreshToken);

        return new LoginResponse(
            accessToken,
            refreshValue,
            account.getRole().name().toLowerCase(),
            jwtService.getAccessTokenExpirySeconds()
        );
    }

    private static String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void enqueueUserLoggedInEvent(UUID userId) {
        if (userId == null) {
            log.warn("Skipping USER_LOGGED_IN outbox event because account id is not available yet.");
            return;
        }

        UserLoggedInEvent event = UserLoggedInEvent.of(userId);
        OutboxEntity outboxEvent = OutboxEntity.builder()
            .aggregateId(userId)
            .eventType("USER_LOGGED_IN")
            .payload(createPayload(event))
            .status(OutboxStatus.PENDING)
            .retryCount(0)
            .build();

        outboxRepository.save(outboxEvent);
    }

    private JsonNode createPayload(Object event) {
        try {
            return objectMapper.valueToTree(event);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to serialize outbox event", ex);
        }
    }
}
