package tr.edu.mersin.bapadvisor.qa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "qa_messages", schema = "core")
public class QaMessage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "citations_json", columnDefinition = "TEXT")
    private String citationsJson;

    @Column(name = "llm_provider_used")
    private String llmProviderUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public QaMessage() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public QaMessage(UUID versionId, UUID userId, String role, String content, String citationsJson, String llmProviderUsed) {
        this.id = UUID.randomUUID();
        this.versionId = versionId;
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.citationsJson = citationsJson;
        this.llmProviderUsed = llmProviderUsed;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCitationsJson() { return citationsJson; }
    public void setCitationsJson(String citationsJson) { this.citationsJson = citationsJson; }
    public String getLlmProviderUsed() { return llmProviderUsed; }
    public void setLlmProviderUsed(String llmProviderUsed) { this.llmProviderUsed = llmProviderUsed; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
