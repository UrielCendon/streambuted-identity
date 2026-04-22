package streambuted.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import streambuted.identity.domain.UserProfileEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Data-access layer for public user profile data.
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {

    Optional<UserProfileEntity> findByAccountId(UUID accountId);

    boolean existsByUsername(String username);
}
