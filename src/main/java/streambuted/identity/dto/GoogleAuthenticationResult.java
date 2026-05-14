package streambuted.identity.dto;

public record GoogleAuthenticationResult(
    LoginResponse loginResponse,
    boolean passwordSetupRequired
) {}
