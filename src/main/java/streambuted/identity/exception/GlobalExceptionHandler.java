package streambuted.identity.exception;

import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import streambuted.identity.dto.ErrorResponse;

import java.util.stream.Collectors;

/**
 * Translates all exceptions into the project-standard error envelope:
 * {"error": "...", "message": "...", "statusCode": ..., "timestamp": "..."}
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── Domain exceptions (IdentityException hierarchy) ──────────────────────

    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<ErrorResponse> handleIdentityException(IdentityException ex) {
        log.warn("Domain exception: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
            ex.getClass().getSimpleName(),
            ex.getMessage(),
            ex.getHttpStatus().value()
        );
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    // ── Bean Validation failures (@Valid) ─────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
        ErrorResponse body = ErrorResponse.of("ValidationException", details, 400);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        ErrorResponse body = ErrorResponse.of(
            "MalformedJsonException",
            "The request body is missing, malformed, or cannot be parsed.",
            400
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameter(
        MissingServletRequestParameterException ex
    ) {
        ErrorResponse body = ErrorResponse.of(
            "MissingRequestParameterException",
            "Missing required parameter: " + ex.getParameterName(),
            400
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ErrorResponse body = ErrorResponse.of(
            "MethodArgumentTypeMismatchException",
            "Parameter '" + ex.getName() + "' has an invalid value.",
            400
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        ErrorResponse body = ErrorResponse.of(
            "ConstraintViolationException",
            ex.getMessage(),
            400
        );
        return ResponseEntity.badRequest().body(body);
    }

    // ── Spring Security ───────────────────────────────────────────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        ErrorResponse body = ErrorResponse.of("AuthenticationException", ex.getMessage(), 401);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse body = ErrorResponse.of("AccessDeniedException", ex.getMessage(), 403);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        ErrorResponse body = ErrorResponse.of(
            "DataIntegrityViolationException",
            "The requested operation violates a data constraint.",
            409
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
