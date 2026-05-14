package streambuted.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import streambuted.identity.config.EmailProperties;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class VerificationEmailService {

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;

    public void sendRegistrationCode(String email, String code, Instant expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailProperties.getFrom());
        message.setTo(email);
        message.setSubject(emailProperties.getVerificationSubject());
        message.setText("""
            Tu codigo de verificacion de StreamButed es: %s

            El codigo expira en 15 minutos. Si no solicitaste este registro, puedes ignorar este correo.

            Expira en: %s
            """.formatted(code, expiresAt));

        mailSender.send(message);
    }
}
