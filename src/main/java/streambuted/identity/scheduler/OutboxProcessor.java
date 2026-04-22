package streambuted.identity.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import streambuted.identity.domain.OutboxEntity;
import streambuted.identity.domain.OutboxStatus;
import streambuted.identity.messaging.IdentityEventPublisher;
import streambuted.identity.messaging.UserPromotedEvent;
import streambuted.identity.repository.OutboxRepository;

import java.time.Instant;
import java.util.List;

/**
 * Polls pending outbox rows and relays them to RabbitMQ.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {

    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxRepository outboxRepository;
    private final IdentityEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    public void processPendingEvents() {
        List<OutboxEntity> pendingEvents = outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEntity outboxEvent : pendingEvents) {
            processSingleEvent(outboxEvent);
        }
    }

    private void processSingleEvent(OutboxEntity outboxEvent) {
        try {
            UserPromotedEvent event = objectMapper.treeToValue(outboxEvent.getPayload(), UserPromotedEvent.class);

            boolean published = eventPublisher.publishUserPromoted(event);
            if (published) {
                markProcessed(outboxEvent);
            } else {
                markRetryOrFailed(outboxEvent, "RabbitMQ publish failed");
            }
        } catch (JsonProcessingException ex) {
            markRetryOrFailed(outboxEvent, "Invalid outbox payload", ex);
        } catch (DataAccessException ex) {
            log.error("Failed to update outbox row id={}: {}", outboxEvent.getId(), ex.getMessage(), ex);
        }
    }

    private void markProcessed(OutboxEntity outboxEvent) {
        outboxEvent.setStatus(OutboxStatus.PROCESSED);
        outboxEvent.setProcessedAt(Instant.now());

        try {
            outboxRepository.save(outboxEvent);
        } catch (DataAccessException ex) {
            log.error("Failed to persist processed status for outbox id={}: {}", outboxEvent.getId(), ex.getMessage(), ex);
        }
    }

    private void markRetryOrFailed(OutboxEntity outboxEvent, String reason) {
        markRetryOrFailed(outboxEvent, reason, null);
    }

    private void markRetryOrFailed(OutboxEntity outboxEvent, String reason, Exception cause) {
        outboxEvent.setRetryCount(outboxEvent.getRetryCount() + 1);

        if (outboxEvent.getRetryCount() >= MAX_RETRY_COUNT) {
            outboxEvent.setStatus(OutboxStatus.FAILED);
            outboxEvent.setProcessedAt(Instant.now());
        } else {
            outboxEvent.setStatus(OutboxStatus.PENDING);
        }

        if (cause == null) {
            log.warn(
                "Outbox event id={} failed to publish ({}) [retryCount={}]",
                outboxEvent.getId(),
                reason,
                outboxEvent.getRetryCount()
            );
        } else {
            log.warn(
                "Outbox event id={} failed to publish ({}) [retryCount={}]",
                outboxEvent.getId(),
                reason,
                outboxEvent.getRetryCount(),
                cause
            );
        }

        try {
            outboxRepository.save(outboxEvent);
        } catch (DataAccessException ex) {
            log.error("Failed to persist retry status for outbox id={}: {}", outboxEvent.getId(), ex.getMessage(), ex);
        }
    }
}