package streambuted.identity.service.oauth;

import streambuted.identity.exception.GoogleAuthenticationException;

import java.util.Locale;

public enum GoogleOAuthMode {
    LOGIN,
    REGISTER;

    public static GoogleOAuthMode fromValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return LOGIN;
        }

        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "login" -> LOGIN;
            case "register" -> REGISTER;
            default -> throw new GoogleAuthenticationException("Google OAuth mode is invalid.");
        };
    }
}
