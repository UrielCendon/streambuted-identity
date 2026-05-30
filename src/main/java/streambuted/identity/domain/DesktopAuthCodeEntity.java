package streambuted.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "desktop_auth_code", indexes = {
    @Index(name = "idx_desktop_auth_code_hash", columnList = "code_hash"),
    @Index(name = "idx_desktop_auth_code_account", columnList = "account_id"),
    @Index(name = "idx_desktop_auth_code_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesktopAuthCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private UserAccountEntity account;

    @Column(name = "code_hash", nullable = false, unique = true, length = 512)
    private String codeHash;

    @Column(name = "state_hash", nullable = false, length = 512)
    private String stateHash;

    @Column(name = "redirect_uri", nullable = false, length = 255)
    private String redirectUri;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
