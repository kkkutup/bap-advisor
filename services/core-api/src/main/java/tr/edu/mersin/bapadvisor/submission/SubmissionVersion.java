package tr.edu.mersin.bapadvisor.submission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "submission_versions", schema = "core")
public class SubmissionVersion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "extracted_metadata_json", columnDefinition = "TEXT")
    private String extractedMetadataJson;

    @Column(name = "selected_modules_json", columnDefinition = "TEXT")
    private String selectedModulesJson;

    @Column(name = "officer_notes", columnDefinition = "TEXT")
    private String officerNotes;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt = Instant.now();

    public SubmissionVersion() {
        this.id = UUID.randomUUID();
        this.submittedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
    public String getExtractedMetadataJson() { return extractedMetadataJson; }
    public void setExtractedMetadataJson(String extractedMetadataJson) { this.extractedMetadataJson = extractedMetadataJson; }
    public String getSelectedModulesJson() { return selectedModulesJson; }
    public void setSelectedModulesJson(String selectedModulesJson) { this.selectedModulesJson = selectedModulesJson; }
    public String getOfficerNotes() { return officerNotes; }
    public void setOfficerNotes(String officerNotes) { this.officerNotes = officerNotes; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
}
