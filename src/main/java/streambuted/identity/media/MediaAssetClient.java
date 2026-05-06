package streambuted.identity.media;

import java.util.UUID;

/** Client used by Identity to validate Media assets before storing references. */
public interface MediaAssetClient {

    MediaAssetMetadata getAssetMetadata(UUID assetId, String authorizationHeader);
}
