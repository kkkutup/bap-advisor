package tr.edu.mersin.bapadvisor.submission;

import com.fasterxml.jackson.annotation.JsonProperty;
import tr.edu.mersin.bapadvisor.common.SubmissionStatus;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

import java.time.Instant;
import java.util.UUID;

public record SubmissionDetailResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("reference_no") String referenceNo,
        @JsonProperty("external_bap2_id") String externalBap2Id,
        @JsonProperty("submission_type") SubmissionType submissionType,
        @JsonProperty("project_code") String projectCode,
        @JsonProperty("title") String title,
        @JsonProperty("applicant_name") String applicantName,
        @JsonProperty("applicant_title") String applicantTitle,
        @JsonProperty("department") String department,
        @JsonProperty("current_version_no") int currentVersionNo,
        @JsonProperty("status") SubmissionStatus status,
        @JsonProperty("assigned_officer_id") UUID assignedOfficerId,
        @JsonProperty("assigned_officer_name") String assignedOfficerName,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("latest_version") SubmissionVersionResponse latestVersion
) {}
