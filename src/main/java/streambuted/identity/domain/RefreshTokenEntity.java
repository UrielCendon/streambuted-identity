package streambuted.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted refresh token record.
 * A token is considered valid only when isRevoked = false and expiresAt is in the future.
 * Old tokens are never deleted to maintain an audit trail; they are revoked instead.
 */
@Entity
@Table(name = "refresh_token", indexes = {
    @Index(name = "idx_refresh_token_value", columnList = "token_value"),
    @Index(name = "idx_refresh_token_account", columnList = "account_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private UserAccountEntity account;

    /** SHA-256 hash of an opaque random refresh token. Raw token values are never persisted. */
    @Column(name = "token_value", nullable = false, unique = true, length = 512)
    private String tokenValue;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "is_revoked", nullable = false)
    @Builder.Default
    private boolean isRevoked = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Convenience helpers.

    /** Returns true when this token may still be exchanged for a new JWT pair. */
    public boolean isValid() {
        return !isRevoked && Instant.now().isBefore(expiresAt);
    }
}
