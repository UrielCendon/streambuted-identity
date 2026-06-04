package streambuted.identity.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @EntityGraph(attributePaths = "profile")
    @Query(
        """
            SELECT account
            FROM UserAccountEntity account
            ORDER BY account.createdAt DESC
            """
    )
    Page<UserAccountEntity> findAllForAdmin(Pageable pageable);

    @EntityGraph(attributePaths = "profile")
    @Query(
        value = """
            SELECT account
            FROM UserAccountEntity account
            LEFT JOIN account.profile profile
            WHERE (
                :searchTerm IS NULL
                OR lower(account.email) LIKE lower(concat('%', :searchTerm, '%'))
                OR lower(profile.username) LIKE lower(concat('%', :searchTerm, '%'))
            )
            ORDER BY account.createdAt DESC
            """,
        countQuery = """
            SELECT count(account)
            FROM UserAccountEntity account
            LEFT JOIN account.profile profile
            WHERE (
                :searchTerm IS NULL
                OR lower(account.email) LIKE lower(concat('%', :searchTerm, '%'))
                OR lower(profile.username) LIKE lower(concat('%', :searchTerm, '%'))
            )
            """
    )
    Page<UserAccountEntity> searchAllForAdmin(@Param("searchTerm") String searchTerm, Pageable pageable);
}
