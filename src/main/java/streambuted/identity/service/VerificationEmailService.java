package streambuted.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import streambuted.identity.config.EmailProperties;
import streambuted.identity.exception.VerificationEmailDeliveryException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationEmailService {

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;
    private final Clock clock;

    public void sendRegistrationCode(String email, String code, Instant expiresAt) {
        sendVerificationCode(
            email,
            code,
            expiresAt,
            emailProperties.getVerificationSubject(),
            "registro",
            "Tu código de verificación de StreamButed es: %s"
        );
    }

    public void sendPasswordResetCode(String email, String code, Instant expiresAt) {
        sendVerificationCode(
            email,
            code,
            expiresAt,
            "Código de recuperación de StreamButed",
            "recuperacion",
            "Tu código de recuperación de StreamButed es: %s"
        );
    }

    private void sendVerificationCode(
        String email,
        String code,
        Instant expiresAt,
        String subject,
        String purpose,
        String headlineTemplate
    ) {
        long expiresInMinutes = Math.max(1, Duration.between(clock.instant(), expiresAt).toMinutes());
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailProperties.getFrom());
        message.setTo(email);
        message.setSubject(subject);
        message.setText("""
            %s

            El codigo expira en %d minutos. Si no solicitaste esta %s, puedes ignorar este correo.

            Expira en: %s
            """.formatted(headlineTemplate.formatted(code), expiresInMinutes, purpose, expiresAt));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Failed to send {} verification email to {}: {}", purpose, email, ex.getMessage());
            throw new VerificationEmailDeliveryException();
        }
    }
}
