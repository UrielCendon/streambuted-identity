package streambuted.identity.dto;

public record DesktopHandoffCodeResponse(
    String code,
    String state,
    long expiresIn
) {}
