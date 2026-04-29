package streambuted.identity.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("IdentityEventPublisher Unit Tests")
class IdentityEventPublisherTest {

    @Test
    @DisplayName("should serialize Instant payload and attach HMAC signature header")
    void publishUserPromoted_serializesInstantAndSignsPayload() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        IdentityEventPublisher publisher = new IdentityEventPublisher(rabbitTemplate, objectMapper);
        String signingSecret = "test-secret-with-enough-entropy-for-hmac";

        ReflectionTestUtils.setField(publisher, "identityExchange", "identity.events");
        ReflectionTestUtils.setField(publisher, "userPromotedRoutingKey", "user.promoted");
        ReflectionTestUtils.setField(publisher, "eventSigningSecret", signingSecret);
        publisher.afterPropertiesSet();

        UserPromotedEvent event = new UserPromotedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "artist@example.com",
            "artistname",
            "listener",
            "artist",
            Instant.parse("2026-04-28T17:57:36.491Z")
        );

        boolean published = publisher.publishUserPromoted(event);

        assertThat(published).isTrue();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
            eq("identity.events"),
            eq("user.promoted"),
            messageCaptor.capture()
        );

        Message message = messageCaptor.getValue();
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"promotedAt\"");
        assertThat(payload).contains(event.userId().toString());

        assertThat(message.getMessageProperties().getHeaders())
            .containsEntry("X-Event-Signature", computeHmacBase64(payload, signingSecret));
    }

    private String computeHmacBase64(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
