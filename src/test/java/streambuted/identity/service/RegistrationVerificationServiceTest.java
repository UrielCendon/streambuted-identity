package streambuted.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegistrationVerificationService Unit Tests")
class RegistrationVerificationServiceTest {

    @Mock private RegistrationVerificationRepository verificationRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private VerificationCodeGenerator codeGenerator;
    @Mock private VerificationEmailService emailService;

    private MutableClock clock;
    private RegistrationVerificationService service;

    private static final String EMAIL = "new@example.com";
    private static final String USERNAME = "newuser";
    private static final String PASSWORD_HASH = "$2a$12$password";
    private static final String CODE = "123456";
    private static final String CODE_HASH = "$2a$12$code";

    @BeforeEach
    void setUp() {
        RegistrationProperties properties = new RegistrationProperties();
        properties.setVerificationCodeTtl(Duration.ofMinutes(15));
        clock = new MutableClock(Instant.parse("2026-05-13T12:00:00Z"));

        service = new RegistrationVerificationService(
            verificationRepository,
            passwordEncoder,
            codeGenerator,
            emailService,
            properties,
            clock
        );
    }

    @Test
    @DisplayName("should create a pending attempt that expires in 15 minutes")
    void startRegistration_createsPendingAttemptWithFifteenMinuteExpiry() {
        when(codeGenerator.generateCode()).thenReturn(CODE);
        when(passwordEncoder.encode(CODE)).thenReturn(CODE_HASH);
        when(verificationRepository.findByEmailAndStatus(EMAIL, RegistrationVerificationStatus.PENDING))
            .thenReturn(List.of());
        when(verificationRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        RegistrationVerificationResponse response = service.startRegistration(
            new RegisterRequest(EMAIL, USERNAME, "SecurePass1!"),
            PASSWORD_HASH
        );

        assertThat(response.expiresInSeconds()).isEqualTo(900);
        assertThat(response.status()).isEqualTo("pending");

        verify(verificationRepository).save(argThat(attempt ->
            attempt.getStatus() == RegistrationVerificationStatus.PENDING &&
            attempt.getExpiresAt().equals(Instant.parse("2026-05-13T12:15:00Z")) &&
            attempt.getCodeHash().equals(CODE_HASH)
        ));
        verify(emailService).sendRegistrationCode(EMAIL, CODE, Instant.parse("2026-05-13T12:15:00Z"));
    }

    @Test
    @DisplayName("should verify a valid code before expiration")
    void verifyCode_validBeforeExpiry_marksVerified() {
        RegistrationVerificationEntity attempt = pendingAttempt();
        when(verificationRepository.findByIdAndEmail(attempt.getId(), EMAIL)).thenReturn(Optional.of(attempt));
        when(passwordEncoder.matches(CODE, CODE_HASH)).thenReturn(true);
        when(verificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationVerificationEntity verified = service.verifyCode(
            new VerifyRegistrationRequest(attempt.getId(), EMAIL, CODE)
        );

        assertThat(verified.getStatus()).isEqualTo(RegistrationVerificationStatus.VERIFIED);
        assertThat(verified.getVerifiedAt()).isEqualTo(clock.instant());
    }

    @Test
    @DisplayName("should reject a code after expiration")
    void verifyCode_afterExpiry_marksExpired() {
        RegistrationVerificationEntity attempt = pendingAttempt();
        clock.setInstant(Instant.parse("2026-05-13T12:15:00Z"));
        when(verificationRepository.findByIdAndEmail(attempt.getId(), EMAIL)).thenReturn(Optional.of(attempt));
        when(verificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.verifyCode(
            new VerifyRegistrationRequest(attempt.getId(), EMAIL, CODE)
        )).isInstanceOf(RegistrationVerificationExpiredException.class);

        assertThat(attempt.getStatus()).isEqualTo(RegistrationVerificationStatus.EXPIRED);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("should invalidate previous pending code when requesting a new one")
    void resendCode_replacesPreviousPendingAttempt() {
        RegistrationVerificationEntity attempt = pendingAttempt();
        when(verificationRepository.findByIdAndEmail(attempt.getId(), EMAIL)).thenReturn(Optional.of(attempt));
        when(verificationRepository.findByEmailAndStatus(EMAIL, RegistrationVerificationStatus.PENDING))
            .thenReturn(List.of(attempt));
        when(codeGenerator.generateCode()).thenReturn("654321");
        when(passwordEncoder.encode("654321")).thenReturn("$2a$12$newcode");
        when(verificationRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        RegistrationVerificationResponse response = service.resendCode(
            new ResendRegistrationCodeRequest(attempt.getId(), EMAIL)
        );

        assertThat(attempt.getStatus()).isEqualTo(RegistrationVerificationStatus.REPLACED);
        assertThat(response.expiresInSeconds()).isEqualTo(900);
        verify(emailService).sendRegistrationCode(eq(EMAIL), eq("654321"), any());
    }

    @Test
    @DisplayName("should cancel pending verification code")
    void cancel_marksPendingAttemptCancelled() {
        RegistrationVerificationEntity attempt = pendingAttempt();
        when(verificationRepository.findByIdAndEmail(attempt.getId(), EMAIL)).thenReturn(Optional.of(attempt));
        when(verificationRepository.findByEmailAndStatus(EMAIL, RegistrationVerificationStatus.PENDING))
            .thenReturn(List.of(attempt));

        service.cancel(new CancelRegistrationVerificationRequest(attempt.getId(), EMAIL));

        assertThat(attempt.getStatus()).isEqualTo(RegistrationVerificationStatus.CANCELLED);
    }

    @Test
    @DisplayName("should reject an incorrect code")
    void verifyCode_incorrectCode_rejected() {
        RegistrationVerificationEntity attempt = pendingAttempt();
        when(verificationRepository.findByIdAndEmail(attempt.getId(), EMAIL)).thenReturn(Optional.of(attempt));
        when(passwordEncoder.matches("000000", CODE_HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.verifyCode(
            new VerifyRegistrationRequest(attempt.getId(), EMAIL, "000000")
        )).isInstanceOf(InvalidRegistrationVerificationException.class)
            .hasMessageContaining("incorrect");

        assertThat(attempt.getStatus()).isEqualTo(RegistrationVerificationStatus.PENDING);
    }

    @Test
    @DisplayName("should reject a code that was already used")
    void verifyCode_usedCode_rejected() {
        RegistrationVerificationEntity attempt = pendingAttempt();
        attempt.setStatus(RegistrationVerificationStatus.VERIFIED);
        when(verificationRepository.findByIdAndEmail(attempt.getId(), EMAIL)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.verifyCode(
            new VerifyRegistrationRequest(attempt.getId(), EMAIL, CODE)
        )).isInstanceOf(InvalidRegistrationVerificationException.class);
    }

    @Test
    @DisplayName("should reject a cancelled code")
    void verifyCode_cancelledCode_rejected() {
        RegistrationVerificationEntity attempt = pendingAttempt();
        attempt.setStatus(RegistrationVerificationStatus.CANCELLED);
        when(verificationRepository.findByIdAndEmail(attempt.getId(), EMAIL)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.verifyCode(
            new VerifyRegistrationRequest(attempt.getId(), EMAIL, CODE)
        )).isInstanceOf(InvalidRegistrationVerificationException.class);
    }

    private RegistrationVerificationEntity pendingAttempt() {
        return RegistrationVerificationEntity.builder()
            .id(UUID.randomUUID())
            .email(EMAIL)
            .username(USERNAME)
            .passwordHash(PASSWORD_HASH)
            .codeHash(CODE_HASH)
            .status(RegistrationVerificationStatus.PENDING)
            .expiresAt(Instant.parse("2026-05-13T12:15:00Z"))
            .build();
    }

    private RegistrationVerificationEntity withId(RegistrationVerificationEntity attempt) {
        if (attempt.getId() == null) {
            attempt.setId(UUID.randomUUID());
        }
        return attempt;
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
