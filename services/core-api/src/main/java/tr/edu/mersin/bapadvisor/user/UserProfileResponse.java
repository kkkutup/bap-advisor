package tr.edu.mersin.bapadvisor.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import tr.edu.mersin.bapadvisor.common.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("email") String email,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("role") UserRole role,
        @JsonProperty("is_active") boolean isActive,
        @JsonProperty("created_at") Instant createdAt
) {
    public static UserProfileResponse fromUser(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
