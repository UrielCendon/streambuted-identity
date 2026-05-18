package streambuted.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import streambuted.identity.config.EmailProperties;
import streambuted.identity.exception.VerificationEmailDeliveryException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("VerificationEmailService Unit Tests")
class VerificationEmailServiceTest {

    @Test
    @DisplayName("should use configured sender address and subject")
    void sendRegistrationCode_usesConfiguredEmailProperties() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailProperties properties = new EmailProperties();
        properties.setFrom("no-reply-streambuted@example.com");
        properties.setVerificationSubject("Codigo StreamButed");
        Clock clock = Clock.fixed(Instant.parse("2026-05-13T12:00:00Z"), ZoneOffset.UTC);
        VerificationEmailService service = new VerificationEmailService(mailSender, properties, clock);

        service.sendRegistrationCode(
            "new@example.com",
            "123456",
            Instant.parse("2026-05-13T12:15:00Z")
        );

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getFrom()).isEqualTo("no-reply-streambuted@example.com");
        assertThat(message.getTo()).containsExactly("new@example.com");
        assertThat(message.getSubject()).isEqualTo("Codigo StreamButed");
        assertThat(message.getText()).contains("123456");
        assertThat(message.getText()).contains("15 minutos");
    }

    @Test
    @DisplayName("should expose a domain error when SMTP delivery fails")
    void sendRegistrationCode_wrapsMailFailures() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailProperties properties = new EmailProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-05-13T12:00:00Z"), ZoneOffset.UTC);
        VerificationEmailService service = new VerificationEmailService(mailSender, properties, clock);

        doThrow(new MailException("SMTP timed out") {})
            .when(mailSender)
            .send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.sendRegistrationCode(
            "new@example.com",
            "123456",
            Instant.parse("2026-05-13T12:15:00Z")
        ))
            .isInstanceOf(VerificationEmailDeliveryException.class)
            .hasMessageContaining("Verification email could not be sent");
    }
}
