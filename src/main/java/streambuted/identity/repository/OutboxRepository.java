package streambuted.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import streambuted.identity.domain.OutboxEntity;
import streambuted.identity.domain.OutboxStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data-access layer for transactional outbox rows.
 */
@Repository
public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {

    List<OutboxEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    List<OutboxEntity> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OutboxStatus status, Instant createdAt);
}