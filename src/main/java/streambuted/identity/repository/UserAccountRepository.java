package streambuted.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import streambuted.identity.domain.UserAccountEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access layer for user account credentials and role information.
 */
@Repository
public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {

    Optional<UserAccountEntity> findByEmail(String email);

    Optional<UserAccountEntity> findByGoogleSubject(String googleSubject);

    boolean existsByEmail(String email);

    List<UserAccountEntity> findAllByEmailEndingWithIgnoreCase(String suffix);
}
