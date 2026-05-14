package streambuted.identity.service.oauth;

public record GoogleUserInfo(
    String subject,
    String email,
    String name
) {}
