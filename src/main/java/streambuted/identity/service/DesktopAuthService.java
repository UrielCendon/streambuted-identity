package streambuted.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import streambuted.identity.config.DesktopAuthProperties;
import streambuted.identity.domain.DesktopAuthCodeEntity;
import streambuted.identity.domain.UserAccountEntity;
import streambuted.identity.dto.DesktopExchangeRequest;
import streambuted.identity.dto.DesktopHandoffCodeRequest;
import streambuted.identity.dto.DesktopHandoffCodeResponse;
import streambuted.identity.dto.LoginResponse;
import streambuted.identity.exception.DesktopAuthDisabledException;
import streambuted.identity.exception.InvalidDesktopAuthCodeException;
import streambuted.identity.exception.InvalidDesktopRedirectUriException;
import streambuted.identity.exception.UserNotFoundException;
import streambuted.identity.repository.DesktopAuthCodeRepository;
import streambuted.identity.repository.UserAccountRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DesktopAuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int OPAQUE_VALUE_MIN_LENGTH = 16;
    private static final int OPAQUE_VALUE_MAX_LENGTH = 512;
    private static final String URL_SAFE_TOKEN_PATTERN = "^[A-Za-z0-9_-]+$";

    private final DesktopAuthProperties properties;
    private final DesktopAuthCodeRepository desktopAuthCodeRepository;
    private final UserAccountRepository accountRepository;
    private final AuthService authService;
    private final Clock clock;

    public DesktopHandoffCodeResponse createHandoffCode(
        UUID userId,
        DesktopHandoffCodeRequest request
    ) {
        ensureEnabled();
        String state = requireUrlSafeToken(request.state());
        String redirectUri = requireText(request.redirectUri());
        if (!properties.getAllowedRedirectUri().equals(redirectUri)) {
            throw new InvalidDesktopRedirectUriException();
        }

        UserAccountEntity account = accountRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId.toString()));
        String code = generateOpaqueValue();
        Instant expiresAt = clock.instant().plusSeconds(properties.getCodeTtlSeconds());

        DesktopAuthCodeEntity authCode = DesktopAuthCodeEntity.builder()
            .account(account)
            .codeHash(hashValue(code))
            .stateHash(hashValue(state))
            .redirectUri(redirectUri)
            .expiresAt(expiresAt)
            .build();

        desktopAuthCodeRepository.save(authCode);
        return new DesktopHandoffCodeResponse(code, state, properties.getCodeTtlSeconds());
    }

    public LoginResponse exchange(DesktopExchangeRequest request) {
        ensureEnabled();
        String codeHash = hashValue(requireUrlSafeToken(request.code()));
        String stateHash = hashValue(requireUrlSafeToken(request.state()));
        Instant now = clock.instant();

        DesktopAuthCodeEntity authCode = desktopAuthCodeRepository
            .findByCodeHash(codeHash)
            .orElseThrow(InvalidDesktopAuthCodeException::new);

        if (!authCode.isUsable(now) || !authCode.getStateHash().equals(stateHash)) {
            throw new InvalidDesktopAuthCodeException();
        }

        authCode.setUsedAt(now);
        desktopAuthCodeRepository.save(authCode);
        return authService.issueSessionForAccount(authCode.getAccount().getId());
    }

    public void cleanupExpiredOrUsed(Instant threshold) {
        desktopAuthCodeRepository.deleteExpiredOrUsedBefore(threshold);
    }

    public void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new DesktopAuthDisabledException();
        }
    }

    private String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidDesktopAuthCodeException();
        }
        return value.trim();
    }

    private String requireUrlSafeToken(String value) {
        String token = requireText(value);
        if (
            token.length() < OPAQUE_VALUE_MIN_LENGTH ||
            token.length() > OPAQUE_VALUE_MAX_LENGTH ||
            !token.matches(URL_SAFE_TOKEN_PATTERN)
        ) {
            throw new InvalidDesktopAuthCodeException();
        }

        return token;
    }

    private static String generateOpaqueValue() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
