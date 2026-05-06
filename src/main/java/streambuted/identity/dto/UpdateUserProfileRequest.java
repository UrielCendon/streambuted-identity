package streambuted.identity.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

/**
 * Payload for PUT /api/v1/users/me.
 *
 * Setter flags let the service distinguish an omitted property from an explicit
 * null, which is needed to support clearing profileImageAssetId.
 */
public class UpdateUserProfileRequest {

    private String username;
    private String bio;
    private String profileImageAssetId;

    private boolean usernamePresent;
    private boolean bioPresent;
    private boolean profileImageAssetIdPresent;

    public String username() {
        return username;
    }

    public String bio() {
        return bio;
    }

    public String profileImageAssetId() {
        return profileImageAssetId;
    }

    @JsonSetter("username")
    public void setUsername(String username) {
        this.username = username;
        this.usernamePresent = true;
    }

    @JsonSetter("bio")
    public void setBio(String bio) {
        this.bio = bio;
        this.bioPresent = true;
    }

    @JsonSetter("profileImageAssetId")
    public void setProfileImageAssetId(String profileImageAssetId) {
        this.profileImageAssetId = profileImageAssetId;
        this.profileImageAssetIdPresent = true;
    }

    @JsonIgnore
    public boolean hasUsername() {
        return usernamePresent;
    }

    @JsonIgnore
    public boolean hasBio() {
        return bioPresent;
    }

    @JsonIgnore
    public boolean hasProfileImageAssetId() {
        return profileImageAssetIdPresent;
    }
}
