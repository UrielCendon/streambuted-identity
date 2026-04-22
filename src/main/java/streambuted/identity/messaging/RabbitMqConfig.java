package streambuted.identity.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the RabbitMQ topology for the Identity Service.
 *
 * Exchange  : identity.events  (topic, durable)
 * Queue     : identity.user.promoted
 * Routing   : user.promoted → identity.user.promoted
 *
 * All queues and exchanges are durable so they survive broker restarts.
 */
@Configuration
public class RabbitMqConfig {

    @Value("${messaging.exchange.identity}")
    private String identityExchange;

    @Value("${messaging.queue.user-promoted}")
    private String userPromotedQueue;

    @Value("${messaging.routing-key.user-promoted}")
    private String userPromotedRoutingKey;

    // ── Exchange ──────────────────────────────────────────────────────────────

    @Bean
    public TopicExchange identityExchange() {
        return ExchangeBuilder.topicExchange(identityExchange)
            .durable(true)
            .build();
    }

    // ── Queues ────────────────────────────────────────────────────────────────

    @Bean
    public Queue userPromotedQueue() {
        return QueueBuilder.durable(userPromotedQueue).build();
    }

    // ── Bindings ──────────────────────────────────────────────────────────────

    @Bean
    public Binding userPromotedBinding(Queue userPromotedQueue, TopicExchange identityExchange) {
        return BindingBuilder.bind(userPromotedQueue)
            .to(identityExchange)
            .with(userPromotedRoutingKey);
    }

    // ── Message converter (JSON) ──────────────────────────────────────────────

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Configures RabbitTemplate to serialise messages as JSON.
     * Mandatory flag ensures publish errors are routed back to the sender.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter());
        template.setMandatory(true);
        return template;
    }
}
