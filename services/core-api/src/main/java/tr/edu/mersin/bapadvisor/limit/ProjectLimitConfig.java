package tr.edu.mersin.bapadvisor.limit;

import jakarta.persistence.*;
import tr.edu.mersin.bapadvisor.common.LimitConfigDto;
import tr.edu.mersin.bapadvisor.common.RuleCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "project_limit_configs", schema = "core")
public class ProjectLimitConfig {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_category", nullable = false)
    private RuleCategory ruleCategory;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "title_tr", nullable = false)
    private String titleTr;

    @Column(name = "max_amount_tl", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxAmountTl;

    @Column(name = "max_duration_months")
    private Integer maxDurationMonths;

    @Column(name = "currency", nullable = false)
    private String currency = "TRY";

    @Column(name = "vat_included", nullable = false)
    private Boolean vatIncluded = true;

    @Column(name = "conditions_json", columnDefinition = "TEXT")
    private String conditionsJson;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ProjectLimitConfig() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public LimitConfigDto toDto() {
        return new LimitConfigDto(
                this.id,
                this.fiscalYear,
                this.ruleCategory,
                this.code,
                this.titleTr,
                this.maxAmountTl,
                this.maxDurationMonths,
                this.currency,
                this.vatIncluded,
                this.conditionsJson,
                this.effectiveFrom,
                this.isActive
        );
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(Integer fiscalYear) { this.fiscalYear = fiscalYear; }
    public RuleCategory getRuleCategory() { return ruleCategory; }
    public void setRuleCategory(RuleCategory ruleCategory) { this.ruleCategory = ruleCategory; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitleTr() { return titleTr; }
    public void setTitleTr(String titleTr) { this.titleTr = titleTr; }
    public BigDecimal getMaxAmountTl() { return maxAmountTl; }
    public void setMaxAmountTl(BigDecimal maxAmountTl) { this.maxAmountTl = maxAmountTl; }
    public Integer getMaxDurationMonths() { return maxDurationMonths; }
    public void setMaxDurationMonths(Integer maxDurationMonths) { this.maxDurationMonths = maxDurationMonths; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Boolean getVatIncluded() { return vatIncluded; }
    public void setVatIncluded(Boolean vatIncluded) { this.vatIncluded = vatIncluded; }
    public String getConditionsJson() { return conditionsJson; }
    public void setConditionsJson(String conditionsJson) { this.conditionsJson = conditionsJson; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
