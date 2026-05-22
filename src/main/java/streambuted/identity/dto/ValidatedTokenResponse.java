package streambuted.identity.dto;

public record ValidatedTokenResponse(
    String userId,
    String role,
    String email,
    boolean isActive
) {
}
