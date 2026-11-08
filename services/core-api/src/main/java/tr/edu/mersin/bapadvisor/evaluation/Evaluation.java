package tr.edu.mersin.bapadvisor.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evaluations", schema = "core")
public class Evaluation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "llm_provider_used")
    private String llmProviderUsed;

    @Column(name = "llm_model_name")
    private String llmModelName;

    @Column(name = "overall_compliance_status", nullable = false)
    private String overallComplianceStatus;

    @Column(name = "deterministic_score_summary", columnDefinition = "TEXT")
    private String deterministicScoreSummary;

    @Column(name = "revision_draft_tr", columnDefinition = "TEXT")
    private String revisionDraftTr;

    @Column(name = "commission_referral_summary_tr", columnDefinition = "TEXT")
    private String commissionReferralSummaryTr;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt = Instant.now();

    public Evaluation() {
        this.id = UUID.randomUUID();
        this.startedAt = Instant.now();
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }
    public String getLlmProviderUsed() { return llmProviderUsed; }
    public void setLlmProviderUsed(String llmProviderUsed) { this.llmProviderUsed = llmProviderUsed; }
    public String getLlmModelName() { return llmModelName; }
    public void setLlmModelName(String llmModelName) { this.llmModelName = llmModelName; }
    public String getOverallComplianceStatus() { return overallComplianceStatus; }
    public void setOverallComplianceStatus(String overallComplianceStatus) { this.overallComplianceStatus = overallComplianceStatus; }
    public String getDeterministicScoreSummary() { return deterministicScoreSummary; }
    public void setDeterministicScoreSummary(String deterministicScoreSummary) { this.deterministicScoreSummary = deterministicScoreSummary; }
    public String getRevisionDraftTr() { return revisionDraftTr; }
    public void setRevisionDraftTr(String revisionDraftTr) { this.revisionDraftTr = revisionDraftTr; }
    public String getCommissionReferralSummaryTr() { return commissionReferralSummaryTr; }
    public void setCommissionReferralSummaryTr(String commissionReferralSummaryTr) { this.commissionReferralSummaryTr = commissionReferralSummaryTr; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
