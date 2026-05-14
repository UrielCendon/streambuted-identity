package streambuted.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import streambuted.identity.config.EmailProperties;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
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
        VerificationEmailService service = new VerificationEmailService(mailSender, properties);

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
    }
}
