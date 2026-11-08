package tr.edu.mersin.bapadvisor.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import tr.edu.mersin.bapadvisor.user.UserProfileResponse;

public record AuthTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in_seconds") long expiresInSeconds,
        @JsonProperty("user") UserProfileResponse user
) {
    public static AuthTokenResponse of(String accessToken, String refreshToken, long expiresInSeconds, UserProfileResponse user) {
        return new AuthTokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, user);
    }
}
