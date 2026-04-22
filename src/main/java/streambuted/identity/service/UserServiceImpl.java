package streambuted.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import streambuted.identity.domain.OutboxEntity;
import streambuted.identity.domain.OutboxStatus;
import streambuted.identity.domain.Role;
import streambuted.identity.domain.UserAccountEntity;
import streambuted.identity.domain.UserProfileEntity;
import streambuted.identity.dto.UserProfileResponse;
import streambuted.identity.exception.RolePromotionException;
import streambuted.identity.exception.UserNotFoundException;
import streambuted.identity.messaging.UserPromotedEvent;
import streambuted.identity.repository.OutboxRepository;
import streambuted.identity.repository.UserAccountRepository;
import streambuted.identity.repository.UserProfileRepository;

import java.util.UUID;

/**
 * Handles profile reads and the listener-to-artist promotion flow.
 *
 * Promotion is persisted atomically together with an outbox row.
 * A scheduled relay later publishes the event to RabbitMQ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserServiceImpl implements UserService {

    private final UserAccountRepository   accountRepository;
    private final UserProfileRepository   profileRepository;
    private final OutboxRepository        outboxRepository;
    private final ObjectMapper            objectMapper;

    // ── Profile retrieval ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        UserAccountEntity account = accountRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        UserProfileEntity profile = profileRepository.findByAccountId(userId)
            .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        return mapToResponse(account, profile);
    }

    // ── Promotion ─────────────────────────────────────────────────────────────

    @Override
    public UserProfileResponse promoteToArtist(UUID userId) {
        UserAccountEntity account = accountRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        // Only listeners can be promoted — admins and existing artists are rejected
        if (account.getRole() != Role.LISTENER) {
            throw new RolePromotionException(
                "Role promotion is only available to accounts with the LISTENER role. "
                + "Current role: " + account.getRole().name()
            );
        }

        account.setRole(Role.ARTIST);
        accountRepository.save(account);

        UserProfileEntity profile = profileRepository.findByAccountId(userId)
            .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        UserPromotedEvent event = UserPromotedEvent.of(
            userId,
            account.getEmail(),
            profile.getUsername()
        );

        OutboxEntity outboxEvent = OutboxEntity.builder()
            .aggregateId(userId)
            .eventType("USER_PROMOTED")
            .payload(createPayload(event))
            .status(OutboxStatus.PENDING)
            .retryCount(0)
            .build();

        outboxRepository.save(outboxEvent);

        log.info("Promoted userId={} from LISTENER to ARTIST", userId);

        return mapToResponse(account, profile);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private UserProfileResponse mapToResponse(UserAccountEntity account, UserProfileEntity profile) {
        return new UserProfileResponse(
            account.getId(),
            account.getEmail(),
            profile.getUsername(),
            profile.getBio(),
            profile.getProfileImageAssetId(),
            account.getRole().name().toLowerCase(),
            account.isActive(),
            account.getCreatedAt()
        );
    }

    private JsonNode createPayload(UserPromotedEvent event) {
        try {
            return objectMapper.valueToTree(event);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to serialize outbox event", ex);
        }
    }
}
