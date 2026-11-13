package tr.edu.mersin.bapadvisor.document;

import jakarta.persistence.*;
import tr.edu.mersin.bapadvisor.common.DocumentRole;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "submission_documents", schema = "core")
public class SubmissionDocument {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "sha256_hash")
    private String sha256Hash;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_role", nullable = false)
    private DocumentRole docRole = DocumentRole.OTHER;

    @Column(name = "ocr_status")
    private String ocrStatus = "PENDING";

    @Column(name = "ocr_confidence")
    private Double ocrConfidence;

    @Column(name = "has_signature_detected")
    private Boolean hasSignatureDetected;

    @Column(name = "parsed_markdown", columnDefinition = "TEXT")
    private String parsedMarkdown;

    @Column(name = "parsed_tables_json", columnDefinition = "TEXT")
    private String parsedTablesJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public SubmissionDocument() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public SubmissionDocumentDto toDto() {
        return new SubmissionDocumentDto(
                this.id,
                this.fileName,
                this.mimeType,
                this.fileSizeBytes,
                this.docRole,
                this.ocrConfidence,
                this.hasSignatureDetected,
                this.storagePath,
                this.parsedMarkdown
        );
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getSha256Hash() { return sha256Hash; }
    public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }
    public DocumentRole getDocRole() { return docRole; }
    public void setDocRole(DocumentRole docRole) { this.docRole = docRole; }
    public String getOcrStatus() { return ocrStatus; }
    public void setOcrStatus(String ocrStatus) { this.ocrStatus = ocrStatus; }
    public Double getOcrConfidence() { return ocrConfidence; }
    public void setOcrConfidence(Double ocrConfidence) { this.ocrConfidence = ocrConfidence; }
    public Boolean getHasSignatureDetected() { return hasSignatureDetected; }
    public void setHasSignatureDetected(Boolean hasSignatureDetected) { this.hasSignatureDetected = hasSignatureDetected; }
    public String getParsedMarkdown() { return parsedMarkdown; }
    public void setParsedMarkdown(String parsedMarkdown) { this.parsedMarkdown = parsedMarkdown; }
    public String getParsedTablesJson() { return parsedTablesJson; }
    public void setParsedTablesJson(String parsedTablesJson) { this.parsedTablesJson = parsedTablesJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
