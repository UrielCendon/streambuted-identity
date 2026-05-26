package streambuted.identity.grpc;

import io.grpc.stub.StreamObserver;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import streambuted.identity.domain.UserAccountEntity;
import streambuted.identity.repository.UserAccountRepository;
import streambuted.identity.security.JwtService;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * gRPC implementation of the TokenValidator service.
 *
 * Consumed by internal microservices (Streaming Service, Catalog Service, etc.)
 * to validate a caller's JWT and retrieve their identity without going through
 * the REST API Gateway. Runs on a dedicated gRPC port (default: 9091).
 *
 * The response always returns is_valid = false (with an error_message) instead
 * of propagating a gRPC error, so callers can handle the failure gracefully
 * without implementing error-code mapping.
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class TokenValidatorGrpcService extends TokenValidatorGrpc.TokenValidatorImplBase {

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
    );

    private final JwtService            jwtService;
    private final UserAccountRepository accountRepository;

    @Override
    public void validateToken(
        TokenRequest request,
        StreamObserver<TokenResponse> responseObserver
    ) {
        String token = request.getToken();

        if (token == null || token.isBlank()) {
            responseObserver.onNext(buildInvalidResponse("El token es obligatorio."));
            responseObserver.onCompleted();
            return;
        }

        Optional<Claims> claimsOpt = jwtService.extractClaims(token);

        if (claimsOpt.isEmpty()) {
            log.debug("gRPC token validation failed: invalid or expired JWT");
            responseObserver.onNext(buildInvalidResponse("El token es invalido o expiro."));
            responseObserver.onCompleted();
            return;
        }

        Claims claims = claimsOpt.get();

        String subject = claims.getSubject();

        if (subject == null || subject.isBlank()) {
            responseObserver.onNext(buildInvalidResponse("El identificador del token no es valido."));
            responseObserver.onCompleted();
            return;
        }

        if (!isUuid(subject)) {
            responseObserver.onNext(buildInvalidResponse("El identificador del token no es valido."));
            responseObserver.onCompleted();
            return;
        }

        UUID userId = UUID.fromString(subject);

        Optional<UserAccountEntity> accountOpt = accountRepository.findById(userId);

        if (accountOpt.isEmpty()) {
            responseObserver.onNext(buildInvalidResponse("La cuenta de usuario no existe o ya no esta disponible."));
            responseObserver.onCompleted();
            return;
        }

        UserAccountEntity account = accountOpt.get();

        TokenResponse response = TokenResponse.newBuilder()
            .setUserId(account.getId().toString())
            .setRole(account.getRole().name().toLowerCase())
            .setEmail(account.getEmail())
            .setIsActive(account.isActive())
            .setIsValid(true)
            .setErrorMessage("")
            .build();

        log.debug("gRPC token validated successfully for userId={}", userId);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // Private helpers.

    private boolean isUuid(String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }

    private TokenResponse buildInvalidResponse(String errorMessage) {
        return TokenResponse.newBuilder()
            .setIsValid(false)
            .setErrorMessage(errorMessage)
            .setUserId("")
            .setRole("")
            .setEmail("")
            .setIsActive(false)
            .build();
    }
}
