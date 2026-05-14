package streambuted.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import streambuted.identity.domain.RegistrationVerificationEntity;
import streambuted.identity.domain.RegistrationVerificationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegistrationVerificationRepository extends JpaRepository<RegistrationVerificationEntity, UUID> {

    Optional<RegistrationVerificationEntity> findByIdAndEmail(UUID id, String email);

    List<RegistrationVerificationEntity> findByEmailAndStatus(
        String email,
        RegistrationVerificationStatus status
    );
}
