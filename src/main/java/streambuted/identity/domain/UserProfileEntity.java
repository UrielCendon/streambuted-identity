package streambuted.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Holds the public-facing profile information for a user.
 * Maintains a strict 1:1 relationship with UserAccountEntity.
 * profile_image_asset_id is a reference to the Media Service asset registry.
 */
@Entity
@Table(
    name = "user_profile",
    uniqueConstraints = @UniqueConstraint(columnNames = "username")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true, updatable = false)
    private UserAccountEntity account;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(columnDefinition = "TEXT")
    private String bio;

    /**
     * External asset reference managed by the Media Service.
     * Null until the user uploads a profile picture.
     */
    @Column(name = "profile_image_asset_id")
    private String profileImageAssetId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
