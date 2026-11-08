package tr.edu.mersin.bapadvisor.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tr.edu.mersin.bapadvisor.common.UserRole;

public record CreateUserRequest(
        @JsonProperty("email") @NotBlank @Email String email,
        @JsonProperty("password") @NotBlank @Size(min = 8) String password,
        @JsonProperty("full_name") @NotBlank String fullName,
        @JsonProperty("role") @NotNull UserRole role
) {}
