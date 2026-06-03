package streambuted.identity.dto;

import java.time.Instant;

/**
 * Uniform error envelope returned by the GlobalExceptionHandler.
 * Shape: {"error": "...", "message": "...", "statusCode": ..., "timestamp": "..."}
 */
public record ErrorResponse(
    String error,
    String code,
    String message,
    int statusCode,
    Instant timestamp
) {

    /** Factory shorthand used by the exception handler. */
    public static ErrorResponse of(String error, String code, String message, int statusCode) {
        return new ErrorResponse(error, code, message, statusCode, Instant.now());
    }

    public static ErrorResponse of(String error, String message, int statusCode) {
        return new ErrorResponse(error, error, message, statusCode, Instant.now());
    }
}
