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
import streambuted.identity.messaging.UserLoggedInEvent;
import streambuted.identity.messaging.UserPromotedEvent;
import streambuted.identity.repository.OutboxRepository;

import java.time.Duration;
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
    private static final Duration FAILED_RETRY_MIN_AGE = Duration.ofHours(1);

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

    @Scheduled(cron = "0 0 * * * *")
    public void retryStaleFailedEvents() {
        Instant cutoff = Instant.now().minus(FAILED_RETRY_MIN_AGE);
        List<OutboxEntity> failedEvents =
            outboxRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OutboxStatus.FAILED, cutoff);

        if (failedEvents.isEmpty()) {
            return;
        }

        for (OutboxEntity outboxEvent : failedEvents) {
            outboxEvent.setStatus(OutboxStatus.PENDING);
            outboxEvent.setRetryCount(0);

            log.warn(
                "Re-queueing stale FAILED outbox event id={} createdAt={} for another delivery cycle",
                outboxEvent.getId(),
                outboxEvent.getCreatedAt()
            );
        }

        try {
            outboxRepository.saveAll(failedEvents);
        } catch (DataAccessException ex) {
            log.error("Failed to persist stale FAILED outbox retries: {}", ex.getMessage(), ex);
        }
    }

    private void processSingleEvent(OutboxEntity outboxEvent) {
        try {
            boolean published = publishOutboxEvent(outboxEvent);
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

    private boolean publishOutboxEvent(OutboxEntity outboxEvent) throws JsonProcessingException {
        return switch (outboxEvent.getEventType()) {
            case "USER_PROMOTED" -> {
                UserPromotedEvent event = objectMapper.treeToValue(outboxEvent.getPayload(), UserPromotedEvent.class);
                yield eventPublisher.publishUserPromoted(event);
            }
            case "USER_LOGGED_IN" -> {
                UserLoggedInEvent event = objectMapper.treeToValue(outboxEvent.getPayload(), UserLoggedInEvent.class);
                yield eventPublisher.publishUserLoggedIn(event);
            }
            default -> {
                log.warn(
                    "Unsupported outbox event type id={} eventType={}",
                    outboxEvent.getId(),
                    outboxEvent.getEventType()
                );
                yield false;
            }
        };
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
        boolean recoveredFromPreviousFailure =
            outboxEvent.getStatus() == OutboxStatus.PENDING && outboxEvent.getProcessedAt() != null;

        outboxEvent.setRetryCount(outboxEvent.getRetryCount() + 1);

        if (outboxEvent.getRetryCount() >= MAX_RETRY_COUNT) {
            outboxEvent.setStatus(OutboxStatus.FAILED);
            outboxEvent.setProcessedAt(Instant.now());

            if (recoveredFromPreviousFailure) {
                log.error(
                    "[OUTBOX-ALERT] Outbox event id={} failed again after recovery cycle (reason={}, retryCount={})",
                    outboxEvent.getId(),
                    reason,
                    outboxEvent.getRetryCount(),
                    cause
                );
            }
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
