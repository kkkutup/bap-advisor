package tr.edu.mersin.bapadvisor.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import tr.edu.mersin.bapadvisor.common.DocumentRole;

import java.util.UUID;

public record SubmissionDocumentDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("file_size_bytes") Long fileSizeBytes,
        @JsonProperty("doc_role") DocumentRole docRole,
        @JsonProperty("ocr_confidence") Double ocrConfidence,
        @JsonProperty("has_signature_detected") Boolean hasSignatureDetected,
        @JsonProperty("storage_path") String storagePath
) {}
