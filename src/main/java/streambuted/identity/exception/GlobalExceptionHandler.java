package streambuted.identity.exception;

import jakarta.validation.ConstraintViolation;
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
import streambuted.identity.dto.AccountBannedErrorResponse;
import streambuted.identity.dto.ErrorResponse;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Translates all exceptions into the project-standard error envelope:
 * {"error": "...", "message": "...", "statusCode": ..., "timestamp": "..."}
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Domain exceptions.

    @ExceptionHandler(AccountBannedException.class)
    public ResponseEntity<AccountBannedErrorResponse> handleAccountBanned(AccountBannedException ex) {
        log.warn("Banned account authentication attempt: {}", ex.getMessage());
        AccountBannedErrorResponse body = AccountBannedErrorResponse.of(
            ex.getMessage(),
            ex.getBanType(),
            ex.getBannedUntil(),
            ex.getRemainingSeconds()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<ErrorResponse> handleIdentityException(IdentityException ex) {
        log.warn("Domain exception: {}", ex.getMessage());
        String internalError = ex.getClass().getSimpleName();
        String publicCode = PublicErrorPolicy.inferPublicCode(internalError, ex.getMessage(), ex.getHttpStatus());
        ErrorResponse body = ErrorResponse.of(
            internalError,
            publicCode,
            PublicErrorPolicy.resolvePublicMessage(publicCode, ex.getMessage()),
            ex.getHttpStatus().value()
        );
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    // Bean Validation failures.

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
        ErrorResponse body = ErrorResponse.of("ValidationException", "invalid_input", details, 400);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        ErrorResponse body = ErrorResponse.of(
            "MalformedJsonException",
            "invalid_input",
            "El cuerpo de la solicitud falta, esta mal formado o no se puede procesar.",
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
            "invalid_input",
            "Falta el parametro obligatorio: " + ex.getParameterName(),
            400
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ErrorResponse body = ErrorResponse.of(
            "MethodArgumentTypeMismatchException",
            "invalid_input",
            "El parametro '" + ex.getName() + "' tiene un valor no valido.",
            400
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String details = sanitizeConstraintViolationMessages(ex.getConstraintViolations());
        ErrorResponse body = ErrorResponse.of(
            "ConstraintViolationException",
            "invalid_input",
            details,
            400
        );
        return ResponseEntity.badRequest().body(body);
    }

    // Spring Security.

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        ErrorResponse body = ErrorResponse.of(
            "AuthenticationException",
            "unauthorized",
            "Tu sesion expiro. Inicia sesion nuevamente.",
            401
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse body = ErrorResponse.of(
            "AccessDeniedException",
            "forbidden",
            "No tienes permisos para esta accion.",
            403
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        ErrorResponse body = ErrorResponse.of(
            "DataIntegrityViolationException",
            "conflict_or_state_changed",
            "El contenido cambio y no se pudo completar la accion. Intenta nuevamente.",
            409
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorResponse body = ErrorResponse.of(
            "InternalServerError",
            "unexpected_operation_failure",
            "No se pudo completar la accion en este momento. Intenta de nuevo mas tarde.",
            500
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String sanitizeConstraintViolationMessages(Set<ConstraintViolation<?>> violations) {
        if (violations == null || violations.isEmpty()) {
            return "La solicitud no cumple con el formato esperado.";
        }

        return violations.stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.joining("; "));
    }
}
