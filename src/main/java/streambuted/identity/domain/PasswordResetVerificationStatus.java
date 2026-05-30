package streambuted.identity.domain;

public enum PasswordResetVerificationStatus {
    PENDING,
    VERIFIED,
    CANCELLED,
    REPLACED,
    EXPIRED,
    COMPLETED
}
