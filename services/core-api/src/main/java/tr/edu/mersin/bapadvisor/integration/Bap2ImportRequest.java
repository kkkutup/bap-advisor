package tr.edu.mersin.bapadvisor.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tr.edu.mersin.bapadvisor.common.DocumentRole;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

import java.util.List;

public record Bap2ImportRequest(
        @JsonProperty("external_bap2_id") @NotBlank String externalBap2Id,
        @JsonProperty("submission_type") @NotNull SubmissionType submissionType,
        @JsonProperty("project_code") String projectCode,
        @JsonProperty("title") @NotBlank String title,
        @JsonProperty("applicant_name") @NotBlank String applicantName,
        @JsonProperty("applicant_title") String applicantTitle,
        @JsonProperty("department") String department,
        @JsonProperty("structured_data") @NotNull StructuredSubmissionDto structuredData,
        @JsonProperty("file_urls") List<FileUrlItem> fileUrls
) {
    public record FileUrlItem(
            @JsonProperty("url") String url,
            @JsonProperty("file_name") String fileName,
            @JsonProperty("doc_role") DocumentRole docRole
    ) {}
}
