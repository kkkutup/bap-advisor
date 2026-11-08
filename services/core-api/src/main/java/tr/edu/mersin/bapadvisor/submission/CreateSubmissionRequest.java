package tr.edu.mersin.bapadvisor.submission;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

public record CreateSubmissionRequest(
        @JsonProperty("submission_type") @NotNull SubmissionType submissionType,
        @JsonProperty("project_code") String projectCode,
        @JsonProperty("title") @NotBlank String title,
        @JsonProperty("applicant_name") @NotBlank String applicantName,
        @JsonProperty("applicant_title") String applicantTitle,
        @JsonProperty("department") String department,
        @JsonProperty("external_bap2_id") String externalBap2Id
) {}
