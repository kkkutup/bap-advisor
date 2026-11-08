package tr.edu.mersin.bapadvisor.submission;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import tr.edu.mersin.bapadvisor.common.SubmissionStatus;

import java.util.List;

public record StatusTransitionRequest(
        @JsonProperty("target_status") @NotNull SubmissionStatus targetStatus,
        @JsonProperty("officer_notes") String officerNotes,
        @JsonProperty("finding_overrides") List<FindingOverrideItem> findingOverrides
) {}
