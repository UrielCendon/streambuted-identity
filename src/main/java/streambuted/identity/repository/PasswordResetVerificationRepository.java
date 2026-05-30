package streambuted.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import streambuted.identity.domain.PasswordResetVerificationEntity;
import streambuted.identity.domain.PasswordResetVerificationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetVerificationRepository extends JpaRepository<PasswordResetVerificationEntity, UUID> {

    Optional<PasswordResetVerificationEntity> findByIdAndEmail(UUID id, String email);

    List<PasswordResetVerificationEntity> findByEmailAndStatus(
        String email,
        PasswordResetVerificationStatus status
    );
}
