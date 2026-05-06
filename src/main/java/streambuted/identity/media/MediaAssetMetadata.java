package streambuted.identity.media;

import java.util.UUID;

public record MediaAssetMetadata(
    UUID assetId,
    String assetType,
    UUID ownerUserId,
    String contentType,
    long sizeBytes,
    boolean exists
) {}
