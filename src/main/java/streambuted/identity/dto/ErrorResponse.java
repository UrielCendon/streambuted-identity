package streambuted.identity.dto;

import java.time.Instant;

/**
 * Uniform error envelope returned by the GlobalExceptionHandler.
 * Shape: {"error": "...", "message": "...", "statusCode": ..., "timestamp": "..."}
 */
public record ErrorResponse(
    String error,
    String message,
    int statusCode,
    Instant timestamp
) {

    /** Factory shorthand used by the exception handler. */
    public static ErrorResponse of(String error, String message, int statusCode) {
        return new ErrorResponse(error, message, statusCode, Instant.now());
    }
}
