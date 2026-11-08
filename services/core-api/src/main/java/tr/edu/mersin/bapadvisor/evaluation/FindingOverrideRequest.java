package tr.edu.mersin.bapadvisor.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record FindingOverrideRequest(
        @JsonProperty("override_status") @NotBlank String overrideStatus,
        @JsonProperty("override_reason") @NotBlank String overrideReason
) {}
