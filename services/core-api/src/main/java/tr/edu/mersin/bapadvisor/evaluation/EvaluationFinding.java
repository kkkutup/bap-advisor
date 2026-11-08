package tr.edu.mersin.bapadvisor.evaluation;

import jakarta.persistence.*;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.FindingSource;
import tr.edu.mersin.bapadvisor.common.RuleCategory;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evaluation_findings", schema = "core")
public class EvaluationFinding {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "evaluation_id", nullable = false)
    private UUID evaluationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private FindingSource sourceType;

    @Column(name = "rule_code", nullable = false)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private RuleCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private FindingSeverity severity;

    @Column(name = "title_tr", nullable = false)
    private String titleTr;

    @Column(name = "explanation_tr", nullable = false, columnDefinition = "TEXT")
    private String explanationTr;

    @Column(name = "regulation_doc_name", nullable = false)
    private String regulationDocName;

    @Column(name = "regulation_article_ref", nullable = false)
    private String regulationArticleRef;

    @Column(name = "verbatim_quote", columnDefinition = "TEXT")
    private String verbatimQuote;

    @Column(name = "expected_value")
    private String expectedValue;

    @Column(name = "actual_value")
    private String actualValue;

    @Column(name = "officer_override_status")
    private String officerOverrideStatus;

    @Column(name = "officer_override_reason", columnDefinition = "TEXT")
    private String officerOverrideReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public EvaluationFinding() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public FindingDto toDto() {
        return new FindingDto(
                this.id,
                this.sourceType,
                this.ruleCode,
                this.category,
                this.severity,
                this.titleTr,
                this.explanationTr,
                this.regulationDocName,
                this.regulationArticleRef,
                this.verbatimQuote,
                this.expectedValue,
                this.actualValue,
                this.officerOverrideStatus,
                this.officerOverrideReason
        );
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEvaluationId() { return evaluationId; }
    public void setEvaluationId(UUID evaluationId) { this.evaluationId = evaluationId; }
    public FindingSource getSourceType() { return sourceType; }
    public void setSourceType(FindingSource sourceType) { this.sourceType = sourceType; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public RuleCategory getCategory() { return category; }
    public void setCategory(RuleCategory category) { this.category = category; }
    public FindingSeverity getSeverity() { return severity; }
    public void setSeverity(FindingSeverity severity) { this.severity = severity; }
    public String getTitleTr() { return titleTr; }
    public void setTitleTr(String titleTr) { this.titleTr = titleTr; }
    public String getExplanationTr() { return explanationTr; }
    public void setExplanationTr(String explanationTr) { this.explanationTr = explanationTr; }
    public String getRegulationDocName() { return regulationDocName; }
    public void setRegulationDocName(String regulationDocName) { this.regulationDocName = regulationDocName; }
    public String getRegulationArticleRef() { return regulationArticleRef; }
    public void setRegulationArticleRef(String regulationArticleRef) { this.regulationArticleRef = regulationArticleRef; }
    public String getVerbatimQuote() { return verbatimQuote; }
    public void setVerbatimQuote(String verbatimQuote) { this.verbatimQuote = verbatimQuote; }
    public String getExpectedValue() { return expectedValue; }
    public void setExpectedValue(String expectedValue) { this.expectedValue = expectedValue; }
    public String getActualValue() { return actualValue; }
    public void setActualValue(String actualValue) { this.actualValue = actualValue; }
    public String getOfficerOverrideStatus() { return officerOverrideStatus; }
    public void setOfficerOverrideStatus(String officerOverrideStatus) { this.officerOverrideStatus = officerOverrideStatus; }
    public String getOfficerOverrideReason() { return officerOverrideReason; }
    public void setOfficerOverrideReason(String officerOverrideReason) { this.officerOverrideReason = officerOverrideReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
