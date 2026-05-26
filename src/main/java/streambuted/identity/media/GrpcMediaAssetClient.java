package streambuted.identity.media;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import streambuted.identity.exception.ProfileUpdateException;
import streambuted.media.v1.AssetMetadataResponse;
import streambuted.media.v1.GetAssetMetadataRequest;
import streambuted.media.v1.MediaAssetServiceGrpc;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class GrpcMediaAssetClient implements MediaAssetClient {

    private static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY =
        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel channel;
    private final MediaAssetServiceGrpc.MediaAssetServiceBlockingStub blockingStub;
    private final long timeoutMs;

    public GrpcMediaAssetClient(
        @Value("${media.grpc.target:media-service:9093}") String target,
        @Value("${media.grpc.timeout-ms:2000}") long timeoutMs
    ) {
        String normalizedTarget = normalizeTarget(target);
        this.timeoutMs = timeoutMs;
        this.channel = ManagedChannelBuilder
            .forTarget(normalizedTarget)
            .usePlaintext()
            .build();
        this.blockingStub = MediaAssetServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public MediaAssetMetadata getAssetMetadata(UUID assetId, String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw ProfileUpdateException.unauthorized(
                "Se requiere contexto de autenticacion para validar archivos multimedia."
            );
        }

        Metadata metadata = new Metadata();
        metadata.put(AUTHORIZATION_METADATA_KEY, authorizationHeader);

        GetAssetMetadataRequest request = GetAssetMetadataRequest.newBuilder()
            .setAssetId(assetId.toString())
            .build();

        try {
            AssetMetadataResponse response = blockingStub
                .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                .getAssetMetadata(request);
            return normalizeResponse(response, assetId);
        } catch (StatusRuntimeException ex) {
            throw mapGrpcError(ex, assetId);
        } catch (ProfileUpdateException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Unexpected Media gRPC validation failure for assetId={}", assetId, ex);
            throw ProfileUpdateException.badGateway(
                "Media Service no pudo validar el archivo indicado."
            );
        }
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        channel.shutdown();
        if (!channel.awaitTermination(2, TimeUnit.SECONDS)) {
            channel.shutdownNow();
        }
    }

    private static String normalizeTarget(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalStateException("MEDIA_GRPC_TARGET must be configured.");
        }
        return target.trim();
    }

    private static MediaAssetMetadata normalizeResponse(
        AssetMetadataResponse response,
        UUID expectedAssetId
    ) {
        UUID responseAssetId = parseUuid(response.getAssetId(), "assetId");
        UUID ownerUserId = parseUuid(response.getOwnerUserId(), "ownerUserId");

        if (!expectedAssetId.equals(responseAssetId)) {
            throw ProfileUpdateException.badGateway(
                "Media Service devolvio metadatos de un archivo diferente."
            );
        }

        if (response.getAssetType().isBlank() || response.getContentType().isBlank()) {
            throw ProfileUpdateException.badGateway(
                "Media Service devolvio una respuesta de metadatos invalida."
            );
        }

        return new MediaAssetMetadata(
            responseAssetId,
            response.getAssetType(),
            ownerUserId,
            response.getContentType(),
            response.getSizeBytes(),
            response.getExists()
        );
    }

    private static UUID parseUuid(String value, String fieldName) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ex) {
            throw ProfileUpdateException.badGateway(
                "Media Service devolvio un campo invalido: " + fieldName + "."
            );
        }
    }

    private static ProfileUpdateException mapGrpcError(StatusRuntimeException error, UUID assetId) {
        Status.Code code = error.getStatus().getCode();
        return switch (code) {
            case UNAUTHENTICATED -> ProfileUpdateException.unauthorized(
                "Media Service rechazo el token de autorizacion."
            );
            case PERMISSION_DENIED -> ProfileUpdateException.forbidden(
                "El archivo multimedia indicado no es accesible."
            );
            case NOT_FOUND, INVALID_ARGUMENT -> ProfileUpdateException.badRequest(
                "No se encontro el archivo multimedia " + assetId + "."
            );
            case UNAVAILABLE -> ProfileUpdateException.serviceUnavailable(
                "Media Service no esta disponible temporalmente para validar archivos."
            );
            case DEADLINE_EXCEEDED -> ProfileUpdateException.gatewayTimeout(
                "Media Service tardo demasiado al validar el archivo indicado."
            );
            default -> ProfileUpdateException.badGateway(
                "Media Service no pudo validar el archivo indicado."
            );
        };
    }
}
