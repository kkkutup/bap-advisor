package tr.edu.mersin.bapadvisor.rules;

import java.math.BigDecimal;
import java.util.List;
import tr.edu.mersin.bapadvisor.common.LimitConfigDto;
import tr.edu.mersin.bapadvisor.common.RuleCategory;

public class RuleLimitsContext {

    private final List<LimitConfigDto> activeLimits;

    public RuleLimitsContext(List<LimitConfigDto> activeLimits) {
        this.activeLimits = activeLimits != null ? activeLimits : List.of();
    }

    public BigDecimal getProjectBudgetCap(String projectCode) {
        if (projectCode == null) {
            return null;
        }
        String normalized = projectCode.trim().toUpperCase();

        for (LimitConfigDto limit : activeLimits) {
            if (Boolean.TRUE.equals(limit.isActive())
                    && limit.code() != null
                    && limit.code().equalsIgnoreCase(normalized)
                    && (limit.ruleCategory() == null || limit.ruleCategory() == RuleCategory.BUDGET)
                    && limit.maxAmountTl() != null) {
                return limit.maxAmountTl();
            }
        }

        return DeterministicRuleConstants.DEFAULT_PROJECT_BUDGET_CAPS.get(normalized);
    }

    public Integer getProjectDurationCapMonths(String projectCode) {
        if (projectCode == null) {
            return DeterministicRuleConstants.MAX_DURATION_NON_THESIS_MONTHS;
        }
        String normalized = projectCode.trim().toUpperCase();

        for (LimitConfigDto limit : activeLimits) {
            if (Boolean.TRUE.equals(limit.isActive())
                    && limit.code() != null
                    && limit.code().equalsIgnoreCase(normalized)
                    && limit.maxDurationMonths() != null) {
                return limit.maxDurationMonths();
            }
        }

        Integer defaultDuration = DeterministicRuleConstants.DEFAULT_PROJECT_DURATION_CAPS.get(normalized);
        return defaultDuration != null ? defaultDuration : DeterministicRuleConstants.MAX_DURATION_NON_THESIS_MONTHS;
    }

    public BigDecimal getOpenAccessCap(String journalQuartile) {
        if (journalQuartile == null) {
            return null;
        }
        String normalized = journalQuartile.trim().toUpperCase();

        if (normalized.contains("TOP10") || normalized.contains("TOP_10")) {
            for (LimitConfigDto limit : activeLimits) {
                if (Boolean.TRUE.equals(limit.isActive())
                        && limit.code() != null
                        && limit.code().toUpperCase().contains("TOP10")
                        && limit.maxAmountTl() != null) {
                    return limit.maxAmountTl();
                }
            }
            return DeterministicRuleConstants.CAP_OPEN_ACCESS_Q1_TOP10;
        }

        if (normalized.contains("Q1")) {
            for (LimitConfigDto limit : activeLimits) {
                if (Boolean.TRUE.equals(limit.isActive())
                        && limit.code() != null
                        && (limit.code().equalsIgnoreCase("WOS_Q1_OTHER") || limit.code().equalsIgnoreCase("Q1_OTHER") || limit.code().equalsIgnoreCase("WOS_Q1"))
                        && limit.maxAmountTl() != null) {
                    return limit.maxAmountTl();
                }
            }
            return DeterministicRuleConstants.CAP_OPEN_ACCESS_Q1_OTHER;
        }

        if (normalized.contains("Q2")) {
            for (LimitConfigDto limit : activeLimits) {
                if (Boolean.TRUE.equals(limit.isActive())
                        && limit.code() != null
                        && (limit.code().equalsIgnoreCase("WOS_Q2") || limit.code().equalsIgnoreCase("Q2"))
                        && limit.maxAmountTl() != null) {
                    return limit.maxAmountTl();
                }
            }
            return DeterministicRuleConstants.CAP_OPEN_ACCESS_Q2;
        }

        return null;
    }

    public BigDecimal getPerDiemAllowance(String applicantTitle) {
        if (applicantTitle == null || applicantTitle.isBlank()) {
            return DeterministicRuleConstants.PER_DIEM_PROFESSOR_TL;
        }

        String lower = applicantTitle.trim().toLowerCase();

        if (lower.contains("prof") || lower.contains("profesör")) {
            return findPerDiemConfig("PROFESSOR", DeterministicRuleConstants.PER_DIEM_PROFESSOR_TL);
        }
        if (lower.contains("doç") || lower.contains("doc") || lower.contains("doçent")) {
            return findPerDiemConfig("ASSOCIATE_PROFESSOR", DeterministicRuleConstants.PER_DIEM_ASSOC_PROF_TL);
        }
        if (lower.contains("dr. öğr") || lower.contains("doktor öğretim") || lower.contains("yrd. doç")) {
            return findPerDiemConfig("ASSISTANT_PROFESSOR", DeterministicRuleConstants.PER_DIEM_ASST_PROF_TL);
        }
        if (lower.contains("öğr. gör") || lower.contains("arş. gör") || lower.contains("araştırma görevlisi") || lower.contains("öğretim görevlisi")) {
            return findPerDiemConfig("INSTRUCTOR", DeterministicRuleConstants.PER_DIEM_INSTRUCTOR_TL);
        }

        return findPerDiemConfig("OTHER", DeterministicRuleConstants.PER_DIEM_STUDENT_OTHER_TL);
    }

    private BigDecimal findPerDiemConfig(String keyword, BigDecimal fallback) {
        for (LimitConfigDto limit : activeLimits) {
            if (Boolean.TRUE.equals(limit.isActive())
                    && limit.ruleCategory() == RuleCategory.PROCUREMENT
                    && limit.code() != null
                    && limit.code().toUpperCase().contains(keyword)
                    && limit.maxAmountTl() != null) {
                return limit.maxAmountTl();
            }
        }
        return fallback;
    }
}
