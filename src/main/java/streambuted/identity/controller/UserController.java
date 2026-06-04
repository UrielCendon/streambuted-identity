package streambuted.identity.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import streambuted.identity.dto.AdminBanUserRequest;
import streambuted.identity.dto.AdminUserListResponse;
import streambuted.identity.dto.AdminUserResponse;
import streambuted.identity.dto.UpdateUserProfileRequest;
import streambuted.identity.dto.UserProfileResponse;
import streambuted.identity.service.UserService;

import java.util.UUID;

/**
 * Handles authenticated user endpoints under /api/v1/users.
 * Every endpoint in this controller requires a valid Bearer JWT.
 * The authenticated user's UUID is injected via @AuthenticationPrincipal.
 * it is set by JwtAuthenticationFilter as the principal of the token.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * GET /api/v1/users/me
     * Returns the full profile of the currently authenticated user.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
        @AuthenticationPrincipal UUID userId
    ) {
        log.debug("Profile requested for userId={}", userId);
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    /**
     * PUT /api/v1/users/me
     * Updates editable profile fields for the currently authenticated user.
     */
    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
        @AuthenticationPrincipal UUID userId,
        @RequestBody UpdateUserProfileRequest request,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        log.debug("Profile update requested for userId={}", userId);
        return ResponseEntity.ok(userService.updateProfile(userId, request, authorizationHeader));
    }

    /**
     * PATCH /api/v1/users/promote
     * Irreversibly promotes the authenticated LISTENER account to ARTIST.
     * Only accounts with the LISTENER role may call this endpoint.
     * Publishes a UserPromotedEvent to RabbitMQ upon success.
     */
    @PatchMapping("/promote")
    @PreAuthorize("hasRole('LISTENER')")
    public ResponseEntity<UserProfileResponse> promoteToArtist(
        @AuthenticationPrincipal UUID userId
    ) {
        log.info("Promotion request received for userId={}", userId);
        UserProfileResponse response = userService.promoteToArtist(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminUserListResponse> listUsersForAdmin(
        @RequestParam(name = "limit", defaultValue = "50") int limit,
        @RequestParam(name = "offset", defaultValue = "0") int offset,
        @RequestParam(name = "q", required = false) String q,
        @RequestParam(name = "searchTerm", required = false) String searchTerm
    ) {
        String effectiveSearchTerm = q != null ? q : searchTerm;
        return ResponseEntity.ok(userService.listUsersForAdmin(limit, offset, effectiveSearchTerm));
    }

    @PatchMapping("/admin/{targetUserId}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminUserResponse> banUser(
        @AuthenticationPrincipal UUID adminUserId,
        @PathVariable UUID targetUserId,
        @Valid @RequestBody AdminBanUserRequest request
    ) {
        return ResponseEntity.ok(userService.banUser(adminUserId, targetUserId, request));
    }

    @PatchMapping("/admin/{targetUserId}/unban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminUserResponse> unbanUser(
        @AuthenticationPrincipal UUID adminUserId,
        @PathVariable UUID targetUserId
    ) {
        return ResponseEntity.ok(userService.unbanUser(adminUserId, targetUserId));
    }
}
