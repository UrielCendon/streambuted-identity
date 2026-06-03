package streambuted.identity.exception;

import org.springframework.http.HttpStatus;

import java.util.Locale;

final class PublicErrorPolicy {

    private static final String NETWORK_OR_SERVICE_MESSAGE =
        "Esta funcion no esta disponible en este momento. Intenta de nuevo mas tarde.";
    private static final String TIMEOUT_MESSAGE =
        "La solicitud tardo demasiado y no se pudo completar. Intenta nuevamente.";
    private static final String NOT_FOUND_MESSAGE =
        "El contenido solicitado ya no esta disponible.";
    private static final String INVALID_INPUT_MESSAGE =
        "La solicitud no cumple con el formato esperado.";
    private static final String FORBIDDEN_MESSAGE =
        "No tienes permisos para esta accion.";
    private static final String UNAUTHORIZED_MESSAGE =
        "Tu sesion expiro. Inicia sesion nuevamente.";
    private static final String CONFLICT_MESSAGE =
        "El contenido cambio y no se pudo completar la accion. Intenta nuevamente.";
    private static final String DEPENDENCY_VALIDATION_MESSAGE =
        "No se pudo validar la informacion relacionada con esta accion. Intenta nuevamente.";
    private static final String UNEXPECTED_MESSAGE =
        "No se pudo completar la accion en este momento. Intenta de nuevo mas tarde.";

    private PublicErrorPolicy() {
    }

    static String inferPublicCode(String internalError, String rawMessage, HttpStatus status) {
        if ("ACCOUNT_BANNED".equals(internalError) || "AccountBannedException".equals(internalError)) {
            return "ACCOUNT_BANNED";
        }

        String normalized = normalize(internalError + " " + rawMessage);

        if (
            normalized.contains("timeout")
                || normalized.contains("deadline exceeded")
                || normalized.contains("tardo demasiado")
        ) {
            return "request_timeout";
        }

        if (
            normalized.contains("service unavailable")
                || normalized.contains("unavailable")
                || normalized.contains("no esta disponible temporalmente")
        ) {
            return "service_temporarily_unavailable";
        }

        if (
            normalized.contains("no pudo validar")
                || normalized.contains("no es accesible")
                || normalized.contains("dependency")
        ) {
            return "dependency_validation_failed";
        }

        if (
            normalized.contains("conflict")
                || normalized.contains("changed")
        ) {
            return "conflict_or_state_changed";
        }

        return switch (status) {
            case UNAUTHORIZED -> "unauthorized";
            case FORBIDDEN -> "forbidden";
            case NOT_FOUND -> "resource_not_found";
            case REQUEST_TIMEOUT, GATEWAY_TIMEOUT -> "request_timeout";
            case CONFLICT -> "conflict_or_state_changed";
            case BAD_REQUEST, UNPROCESSABLE_ENTITY -> "invalid_input";
            case INTERNAL_SERVER_ERROR, BAD_GATEWAY, SERVICE_UNAVAILABLE -> "service_temporarily_unavailable";
            default -> status.is5xxServerError() ? "service_temporarily_unavailable" : "unexpected_operation_failure";
        };
    }

    static String resolvePublicMessage(String publicCode, String rawMessage) {
        if ("ACCOUNT_BANNED".equals(publicCode)) {
            return rawMessage;
        }

        if (rawMessage == null || rawMessage.isBlank() || hasInternalMarkers(rawMessage)) {
            return defaultMessageFor(publicCode);
        }

        return rawMessage;
    }

    static String defaultMessageFor(String publicCode) {
        return switch (publicCode) {
            case "service_temporarily_unavailable" -> NETWORK_OR_SERVICE_MESSAGE;
            case "request_timeout" -> TIMEOUT_MESSAGE;
            case "resource_not_found" -> NOT_FOUND_MESSAGE;
            case "invalid_input" -> INVALID_INPUT_MESSAGE;
            case "forbidden" -> FORBIDDEN_MESSAGE;
            case "unauthorized" -> UNAUTHORIZED_MESSAGE;
            case "conflict_or_state_changed" -> CONFLICT_MESSAGE;
            case "dependency_validation_failed" -> DEPENDENCY_VALIDATION_MESSAGE;
            default -> UNEXPECTED_MESSAGE;
        };
    }

    private static boolean hasInternalMarkers(String message) {
        String normalized = normalize(message);
        return normalized.contains("jwks")
            || normalized.contains("grpc")
            || normalized.contains("rabbitmq")
            || normalized.contains("minio")
            || normalized.contains("prisma")
            || normalized.contains("postgres")
            || normalized.contains("mongodb")
            || normalized.contains("redis")
            || normalized.contains("identity service")
            || normalized.contains("catalog service")
            || normalized.contains("media service")
            || normalized.contains("analytics service")
            || normalized.contains("streaming service")
            || normalized.contains("live service")
            || normalized.contains("database");
    }

    private static String normalize(String value) {
        return value == null ? "" : value
            .toLowerCase(Locale.ROOT)
            .trim();
    }
}
