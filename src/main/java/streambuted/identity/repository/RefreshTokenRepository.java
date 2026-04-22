package streambuted.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import streambuted.identity.domain.RefreshTokenEntity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access layer for persisted refresh tokens.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenValue(String tokenValue);

    /** Revokes all active refresh tokens belonging to a given account (logout-all). */
    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.isRevoked = true WHERE r.account.id = :accountId AND r.isRevoked = false")
    void revokeAllByAccountId(UUID accountId);

    /** Purges expired records older than the given threshold to keep the table lean. */
    @Modifying
    @Query("DELETE FROM RefreshTokenEntity r WHERE r.expiresAt < :threshold")
    void deleteExpiredBefore(Instant threshold);
}
