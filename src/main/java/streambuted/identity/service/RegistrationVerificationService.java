package streambuted.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import streambuted.identity.config.RegistrationProperties;
import streambuted.identity.domain.RegistrationVerificationEntity;
import streambuted.identity.domain.RegistrationVerificationStatus;
import streambuted.identity.dto.*;
import streambuted.identity.exception.InvalidRegistrationVerificationException;
import streambuted.identity.exception.RegistrationVerificationExpiredException;
import streambuted.identity.repository.RegistrationVerificationRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RegistrationVerificationService {

    private static final String CODE_SENT_MESSAGE = "Codigo de verificacion enviado.";

    private final RegistrationVerificationRepository verificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationEmailService emailService;
    private final RegistrationProperties registrationProperties;
    private final Clock clock;

    public RegistrationVerificationResponse startRegistration(
        RegisterRequest request,
        String passwordHash
    ) {
        String email = normalizeEmail(request.email());
        invalidatePendingForEmail(email, RegistrationVerificationStatus.REPLACED);

        GeneratedAttempt attempt = createAttempt(
            email,
            request.username().trim(),
            passwordHash
        );

        return saveAndSendCode(attempt);
    }

    public RegistrationVerificationResponse resendCode(ResendRegistrationCodeRequest request) {
        RegistrationVerificationEntity current = findAttempt(request.attemptId(), request.email());

        if (current.getStatus() == RegistrationVerificationStatus.CANCELLED ||
            current.getStatus() == RegistrationVerificationStatus.VERIFIED ||
            current.getStatus() == RegistrationVerificationStatus.REPLACED) {
            throw new InvalidRegistrationVerificationException(
                "El intento de verificacion ya no es valido. Inicia el registro nuevamente."
            );
        }

        invalidatePendingForEmail(current.getEmail(), RegistrationVerificationStatus.REPLACED);

        GeneratedAttempt replacement = createAttempt(
            current.getEmail(),
            current.getUsername(),
            current.getPasswordHash()
        );

        return saveAndSendCode(replacement);
    }

    public RegistrationVerificationEntity verifyCode(VerifyRegistrationRequest request) {
        RegistrationVerificationEntity attempt = findAttempt(request.attemptId(), request.email());

        if (attempt.getStatus() != RegistrationVerificationStatus.PENDING) {
            throwInvalidForStatus(attempt.getStatus());
        }

        Instant now = clock.instant();
        if (hasExpired(attempt, now)) {
            attempt.setStatus(RegistrationVerificationStatus.EXPIRED);
            verificationRepository.save(attempt);
            throw new RegistrationVerificationExpiredException();
        }

        if (!passwordEncoder.matches(request.code(), attempt.getCodeHash())) {
            throw new InvalidRegistrationVerificationException("El codigo de verificacion es incorrecto.");
        }

        attempt.setStatus(RegistrationVerificationStatus.VERIFIED);
        attempt.setVerifiedAt(now);
        return verificationRepository.save(attempt);
    }

    public void cancel(CancelRegistrationVerificationRequest request) {
        RegistrationVerificationEntity attempt = findAttempt(request.attemptId(), request.email());
        invalidatePendingForEmail(attempt.getEmail(), RegistrationVerificationStatus.CANCELLED);
    }

    private GeneratedAttempt createAttempt(
        String email,
        String username,
        String passwordHash
    ) {
        String code = codeGenerator.generateCode();
        Instant expiresAt = clock.instant().plus(registrationProperties.getVerificationCodeTtl());

        RegistrationVerificationEntity attempt = RegistrationVerificationEntity.builder()
            .email(email)
            .username(username)
            .passwordHash(passwordHash)
            .codeHash(passwordEncoder.encode(code))
            .status(RegistrationVerificationStatus.PENDING)
            .expiresAt(expiresAt)
            .build();

        return new GeneratedAttempt(attempt, code);
    }

    private RegistrationVerificationResponse saveAndSendCode(GeneratedAttempt generatedAttempt) {
        RegistrationVerificationEntity saved = verificationRepository.save(generatedAttempt.attempt());
        emailService.sendRegistrationCode(saved.getEmail(), generatedAttempt.code(), saved.getExpiresAt());
        return toResponse(saved, CODE_SENT_MESSAGE);
    }

    private RegistrationVerificationEntity findAttempt(UUID attemptId, String email) {
        return verificationRepository
            .findByIdAndEmail(attemptId, normalizeEmail(email))
            .orElseThrow(() -> new InvalidRegistrationVerificationException(
                "El intento de verificacion no fue encontrado."
            ));
    }

    private void invalidatePendingForEmail(String email, RegistrationVerificationStatus activeStatus) {
        Instant now = clock.instant();
        var pendingAttempts = verificationRepository.findByEmailAndStatus(
            email,
            RegistrationVerificationStatus.PENDING
        );

        for (RegistrationVerificationEntity pendingAttempt : pendingAttempts) {
            pendingAttempt.setStatus(hasExpired(pendingAttempt, now)
                ? RegistrationVerificationStatus.EXPIRED
                : activeStatus);
        }

        if (!pendingAttempts.isEmpty()) {
            verificationRepository.saveAll(pendingAttempts);
        }
    }

    private void throwInvalidForStatus(RegistrationVerificationStatus status) {
        if (status == RegistrationVerificationStatus.EXPIRED) {
            throw new RegistrationVerificationExpiredException();
        }

        throw new InvalidRegistrationVerificationException(
            "El codigo de verificacion ya no es valido. Solicita un nuevo codigo."
        );
    }

    private boolean hasExpired(RegistrationVerificationEntity attempt, Instant now) {
        return !now.isBefore(attempt.getExpiresAt());
    }

    private RegistrationVerificationResponse toResponse(
        RegistrationVerificationEntity attempt,
        String message
    ) {
        long expiresInSeconds = Math.max(
            0,
            Duration.between(clock.instant(), attempt.getExpiresAt()).toSeconds()
        );

        return new RegistrationVerificationResponse(
            attempt.getId(),
            attempt.getEmail(),
            attempt.getStatus().name().toLowerCase(Locale.ROOT),
            expiresInSeconds,
            message
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record GeneratedAttempt(RegistrationVerificationEntity attempt, String code) {}
}
