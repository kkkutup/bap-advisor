package tr.edu.mersin.bapadvisor.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import tr.edu.mersin.bapadvisor.common.UserRole;

public record UpdateUserRequest(
        @JsonProperty("full_name") String fullName,
        @JsonProperty("role") UserRole role,
        @JsonProperty("password") @Size(min = 8) String password
) {}
