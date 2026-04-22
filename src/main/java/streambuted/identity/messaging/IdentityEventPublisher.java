package streambuted.identity.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publishes domain events from the Identity Service to RabbitMQ.
 * The caller receives a boolean outcome so the outbox relay can decide
 * whether to retry or mark the row as processed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${messaging.exchange.identity}")
    private String identityExchange;

    @Value("${messaging.routing-key.user-promoted}")
    private String userPromotedRoutingKey;

    /**
     * Publishes a UserPromotedEvent to the identity.events exchange.
     * Downstream consumers (Catalog Service, Analytics Service) subscribe
     * to this event to update their own bounded contexts.
     *
     * @param event the fully constructed event payload
     */
    public boolean publishUserPromoted(UserPromotedEvent event) {
        try {
            rabbitTemplate.convertAndSend(identityExchange, userPromotedRoutingKey, event);
            log.info("Published UserPromotedEvent for userId={}, eventId={}",
                event.userId(), event.eventId());
            return true;
        } catch (AmqpException ex) {
            log.error("Failed to publish UserPromotedEvent for userId={}: {}",
                event.userId(), ex.getMessage(), ex);
            return false;
        }
    }
}
