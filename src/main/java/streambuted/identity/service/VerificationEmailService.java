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
        long expiresInMinutes = Math.max(1, Duration.between(clock.instant(), expiresAt).toMinutes());
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailProperties.getFrom());
        message.setTo(email);
        message.setSubject(emailProperties.getVerificationSubject());
        message.setText("""
            Tu codigo de verificacion de StreamButed es: %s

            El codigo expira en %d minutos. Si no solicitaste este registro, puedes ignorar este correo.

            Expira en: %s
            """.formatted(code, expiresInMinutes, expiresAt));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Failed to send registration verification email to {}: {}", email, ex.getMessage());
            throw new VerificationEmailDeliveryException();
        }
    }
}
