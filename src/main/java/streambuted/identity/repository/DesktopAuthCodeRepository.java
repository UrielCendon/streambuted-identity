package streambuted.identity.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import streambuted.identity.domain.DesktopAuthCodeEntity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DesktopAuthCodeRepository extends JpaRepository<DesktopAuthCodeEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DesktopAuthCodeEntity> findByCodeHash(String codeHash);

    @Modifying
    @Query("""
        DELETE FROM DesktopAuthCodeEntity c
        WHERE c.expiresAt < :threshold OR (c.usedAt IS NOT NULL AND c.usedAt < :threshold)
        """)
    void deleteExpiredOrUsedBefore(Instant threshold);
}
