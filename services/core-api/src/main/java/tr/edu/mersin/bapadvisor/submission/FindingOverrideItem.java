package tr.edu.mersin.bapadvisor.submission;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FindingOverrideItem(
        @JsonProperty("finding_id") @NotNull UUID findingId,
        @JsonProperty("override_status") @NotBlank String overrideStatus,
        @JsonProperty("override_reason") @NotBlank String overrideReason
) {}
