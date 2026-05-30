package streambuted.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import streambuted.identity.config.RegistrationProperties;
import streambuted.identity.domain.PasswordResetVerificationEntity;
import streambuted.identity.domain.PasswordResetVerificationStatus;
import streambuted.identity.domain.UserAccountEntity;
import streambuted.identity.dto.*;
import streambuted.identity.exception.*;
import streambuted.identity.repository.PasswordResetVerificationRepository;
import streambuted.identity.repository.UserAccountRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PasswordResetVerificationService {

    private static final String CODE_SENT_MESSAGE = "Código de recuperación enviado.";

    private final PasswordResetVerificationRepository verificationRepository;
    private final UserAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationEmailService emailService;
    private final RegistrationProperties registrationProperties;
    private final Clock clock;

    public RegistrationVerificationResponse startReset(UserAccountEntity account) {
      String email = normalizeEmail(account.getEmail());
      invalidatePendingForEmail(email, PasswordResetVerificationStatus.REPLACED);

      GeneratedAttempt attempt = createAttempt(account, email);
      return saveAndSendCode(attempt);
    }

    public RegistrationVerificationResponse resendCode(PasswordResetActionRequest request) {
        PasswordResetVerificationEntity current = findAttempt(request.attemptId(), request.email());

        if (current.getStatus() == PasswordResetVerificationStatus.CANCELLED
            || current.getStatus() == PasswordResetVerificationStatus.REPLACED
            || current.getStatus() == PasswordResetVerificationStatus.COMPLETED) {
            throw new InvalidPasswordResetVerificationException(
                "El intento de recuperación ya no es válido. Inicia nuevamente."
            );
        }

        invalidatePendingForEmail(current.getEmail(), PasswordResetVerificationStatus.REPLACED);
        GeneratedAttempt replacement = createAttempt(current.getAccount(), current.getEmail());
        return saveAndSendCode(replacement);
    }

    public void verifyCode(VerifyPasswordResetCodeRequest request) {
        PasswordResetVerificationEntity attempt = findAttempt(request.attemptId(), request.email());

        if (attempt.getStatus() != PasswordResetVerificationStatus.PENDING) {
            throwInvalidForStatus(attempt.getStatus());
        }

        Instant now = clock.instant();
        if (hasExpired(attempt, now)) {
            attempt.setStatus(PasswordResetVerificationStatus.EXPIRED);
            verificationRepository.save(attempt);
            throw new PasswordResetVerificationExpiredException();
        }

        if (!passwordEncoder.matches(request.code(), attempt.getCodeHash())) {
            throw new InvalidPasswordResetVerificationException("El código de recuperación es incorrecto.");
        }

        attempt.setStatus(PasswordResetVerificationStatus.VERIFIED);
        attempt.setVerifiedAt(now);
        verificationRepository.save(attempt);
    }

    public void completeReset(CompletePasswordResetRequest request) {
        PasswordResetVerificationEntity attempt = findAttempt(request.attemptId(), request.email());

        if (attempt.getStatus() != PasswordResetVerificationStatus.VERIFIED) {
            if (attempt.getStatus() == PasswordResetVerificationStatus.EXPIRED) {
                throw new PasswordResetVerificationExpiredException();
            }
            throw new PasswordResetNotVerifiedException();
        }

        if (hasExpired(attempt, clock.instant())) {
            attempt.setStatus(PasswordResetVerificationStatus.EXPIRED);
            verificationRepository.save(attempt);
            throw new PasswordResetVerificationExpiredException();
        }

        UserAccountEntity account = accountRepository.findById(attempt.getAccount().getId())
            .orElseThrow(() -> new UserNotFoundException(attempt.getAccount().getId().toString()));
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setPasswordSetupRequired(false);
        accountRepository.save(account);

        attempt.setStatus(PasswordResetVerificationStatus.COMPLETED);
        attempt.setCompletedAt(clock.instant());
        verificationRepository.save(attempt);
    }

    private GeneratedAttempt createAttempt(UserAccountEntity account, String email) {
        String code = codeGenerator.generateCode();
        Instant expiresAt = clock.instant().plus(registrationProperties.getVerificationCodeTtl());

        PasswordResetVerificationEntity attempt = PasswordResetVerificationEntity.builder()
            .account(account)
            .email(email)
            .codeHash(passwordEncoder.encode(code))
            .status(PasswordResetVerificationStatus.PENDING)
            .expiresAt(expiresAt)
            .build();

        return new GeneratedAttempt(attempt, code);
    }

    private RegistrationVerificationResponse saveAndSendCode(GeneratedAttempt generatedAttempt) {
        PasswordResetVerificationEntity saved = verificationRepository.save(generatedAttempt.attempt());
        emailService.sendPasswordResetCode(saved.getEmail(), generatedAttempt.code(), saved.getExpiresAt());
        return toResponse(saved);
    }

    private PasswordResetVerificationEntity findAttempt(UUID attemptId, String email) {
        return verificationRepository.findByIdAndEmail(attemptId, normalizeEmail(email))
            .orElseThrow(() -> new InvalidPasswordResetVerificationException(
                "El intento de recuperación no fue encontrado."
            ));
    }

    private void invalidatePendingForEmail(String email, PasswordResetVerificationStatus replacementStatus) {
        Instant now = clock.instant();
        var pendingAttempts = verificationRepository.findByEmailAndStatus(email, PasswordResetVerificationStatus.PENDING);

        for (PasswordResetVerificationEntity pendingAttempt : pendingAttempts) {
            pendingAttempt.setStatus(hasExpired(pendingAttempt, now)
                ? PasswordResetVerificationStatus.EXPIRED
                : replacementStatus);
        }

        if (!pendingAttempts.isEmpty()) {
            verificationRepository.saveAll(pendingAttempts);
        }
    }

    private void throwInvalidForStatus(PasswordResetVerificationStatus status) {
        if (status == PasswordResetVerificationStatus.EXPIRED) {
            throw new PasswordResetVerificationExpiredException();
        }

        if (status == PasswordResetVerificationStatus.VERIFIED) {
            throw new InvalidPasswordResetVerificationException(
                "El código ya fue verificado. Ahora define una nueva contraseña."
            );
        }

        if (status == PasswordResetVerificationStatus.COMPLETED) {
            throw new InvalidPasswordResetVerificationException(
                "La recuperación ya fue completada. Inicia sesión con tu nueva contraseña."
            );
        }

        throw new InvalidPasswordResetVerificationException(
            "El código de recuperación ya no es válido. Solicita uno nuevo."
        );
    }

    private boolean hasExpired(PasswordResetVerificationEntity attempt, Instant now) {
        return !now.isBefore(attempt.getExpiresAt());
    }

    private RegistrationVerificationResponse toResponse(PasswordResetVerificationEntity attempt) {
        long expiresInSeconds = Math.max(
            0,
            Duration.between(clock.instant(), attempt.getExpiresAt()).toSeconds()
        );

        return new RegistrationVerificationResponse(
            attempt.getId(),
            attempt.getEmail(),
            attempt.getStatus().name().toLowerCase(Locale.ROOT),
            expiresInSeconds,
            CODE_SENT_MESSAGE
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record GeneratedAttempt(PasswordResetVerificationEntity attempt, String code) {}
}
