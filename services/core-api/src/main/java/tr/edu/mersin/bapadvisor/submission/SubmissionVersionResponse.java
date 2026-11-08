package tr.edu.mersin.bapadvisor.submission;

import com.fasterxml.jackson.annotation.JsonProperty;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.document.SubmissionDocumentDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubmissionVersionResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("submission_id") UUID submissionId,
        @JsonProperty("version_no") int versionNo,
        @JsonProperty("extracted_metadata") StructuredSubmissionDto extractedMetadata,
        @JsonProperty("selected_modules") EvaluationModuleFlagsDto selectedModules,
        @JsonProperty("officer_notes") String officerNotes,
        @JsonProperty("submitted_at") Instant submittedAt,
        @JsonProperty("documents") List<SubmissionDocumentDto> documents
) {}
