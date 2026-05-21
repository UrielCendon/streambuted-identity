package streambuted.identity.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.InitializingBean;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Publishes domain events from the Identity Service to RabbitMQ.
 * The caller receives a boolean outcome so the outbox relay can decide
 * whether to retry or mark the row as processed.
 *
 * Requires EVENT_SIGNING_SECRET to be configured for HMAC signing.
 * Fails on startup if the secret is missing or blank.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityEventPublisher implements InitializingBean {

    private final RabbitTemplate rabbitTemplate;

    @Value("${messaging.exchange.identity}")
    private String identityExchange;

    @Value("${messaging.routing-key.user-promoted}")
    private String userPromotedRoutingKey;

    @Value("${messaging.routing-key.user-logged-in}")
    private String userLoggedInRoutingKey;

    @Value("${EVENT_SIGNING_SECRET:}")
    private String eventSigningSecret;

    private final ObjectMapper objectMapper;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (eventSigningSecret == null || eventSigningSecret.isBlank()) {
            throw new IllegalArgumentException(
                "EVENT_SIGNING_SECRET must be configured and non-empty. " +
                "This environment variable is required for event signing security."
            );
        }
        log.info("IdentityEventPublisher initialized with event signing enabled.");
    }

    /**
     * Publishes a UserPromotedEvent to the identity.events exchange.
     * Downstream consumers (Catalog Service, Analytics Service) subscribe
     * to this event to update their own bounded contexts.
     *
     * @param event the fully constructed event payload
     */
    public boolean publishUserPromoted(UserPromotedEvent event) {
        return publishEvent(
            event,
            userPromotedRoutingKey,
            "UserPromotedEvent",
            event.userId(),
            event.eventId()
        );
    }

    /**
     * Publishes a UserLoggedInEvent to the identity.events exchange.
     *
     * @param event the fully constructed event payload
     */
    public boolean publishUserLoggedIn(UserLoggedInEvent event) {
        return publishEvent(
            event,
            userLoggedInRoutingKey,
            "UserLoggedInEvent",
            event.userId(),
            event.eventId()
        );
    }

    private boolean publishEvent(
        Object event,
        String routingKey,
        String eventName,
        Object userId,
        Object eventId
    ) {
        try {
            // Serialize the event to JSON to compute a stable HMAC signature
            String payloadJson = objectMapper.writeValueAsString(event);

            // Always sign: afterPropertiesSet() guarantees eventSigningSecret is non-null and non-blank
            String signature = computeHmacBase64(payloadJson, eventSigningSecret);

            Message message = MessageBuilder
                .withBody(payloadJson.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setHeader("X-Event-Signature", signature)
                .build();

            rabbitTemplate.send(identityExchange, routingKey, message);

            log.info("Published {} for userId={}, eventId={}", eventName, userId, eventId);
            return true;
        } catch (AmqpException ex) {
            log.error("Failed to publish {} for userId={}: {}",
                eventName, userId, ex.getMessage(), ex);
            return false;
        } catch (Exception ex) {
            log.error("Failed to serialize/sign {} for userId={}: {}",
                eventName, userId, ex.getMessage(), ex);
            return false;
        }
    }

    private String computeHmacBase64(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sig);
    }
}
