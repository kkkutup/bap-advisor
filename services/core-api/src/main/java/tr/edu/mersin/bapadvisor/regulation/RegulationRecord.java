package tr.edu.mersin.bapadvisor.regulation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "regulation_records", schema = "core")
public class RegulationRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "title_tr", nullable = false)
    private String titleTr;

    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Column(name = "version_label")
    private String versionLabel;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "sha256_hash")
    private String sha256Hash;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "ingested_at")
    private Instant ingestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RegulationRecord() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public RegulationDocumentDto toDto() {
        return new RegulationDocumentDto(
                this.id,
                this.slug,
                this.titleTr,
                this.docType,
                this.versionLabel,
                this.sourceUrl,
                this.isActive,
                this.ingestedAt
        );
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitleTr() { return titleTr; }
    public void setTitleTr(String titleTr) { this.titleTr = titleTr; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getVersionLabel() { return versionLabel; }
    public void setVersionLabel(String versionLabel) { this.versionLabel = versionLabel; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getSha256Hash() { return sha256Hash; }
    public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
    public Instant getIngestedAt() { return ingestedAt; }
    public void setIngestedAt(Instant ingestedAt) { this.ingestedAt = ingestedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
