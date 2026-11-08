package tr.edu.mersin.bapadvisor.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import tr.edu.mersin.bapadvisor.common.BudgetLineItemDto;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.RuleCategory;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

public class PerDiemAndProcurementEvaluator implements RuleEvaluator {

    @Override
    public boolean supports(StructuredSubmissionDto submission) {
        if (submission == null) {
            return false;
        }
        if (submission.detectedSubmissionType() == SubmissionType.PROCUREMENT_ADVANCE) {
            return true;
        }
        if (submission.budgetLineItems() != null) {
            for (BudgetLineItemDto item : submission.budgetLineItems()) {
                String cat = item.category() != null ? item.category().toUpperCase() : "";
                if (cat.equals("TRAVEL") || (item.name() != null && item.name().toLowerCase().contains("yolluk"))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<FindingDto> evaluate(StructuredSubmissionDto submission, RuleLimitsContext context) {
        List<FindingDto> findings = new ArrayList<>();
        if (submission == null) {
            return findings;
        }

        BigDecimal perDiemLimit = context.getPerDiemAllowance(submission.applicantTitle());
        List<BudgetLineItemDto> items = submission.budgetLineItems() != null ? submission.budgetLineItems() : List.of();
        boolean hasTravelItems = false;
        boolean hasTravelViolation = false;

        for (BudgetLineItemDto item : items) {
            String cat = item.category() != null ? item.category().toUpperCase() : "";
            boolean isTravel = cat.equals("TRAVEL")
                    || (item.name() != null && (item.name().toLowerCase().contains("yolluk") || item.name().toLowerCase().contains("gündelik")));

            if (isTravel) {
                hasTravelItems = true;
                String itemName = item.name() != null ? item.name() : "Yolluk / Gündelik Kalemi";
                BigDecimal dailyRate = item.dailyRateTl();

                if (dailyRate == null && item.days() != null && item.days() > 0 && item.totalTl() != null) {
                    dailyRate = item.totalTl().divide(BigDecimal.valueOf(item.days()), 2, RoundingMode.HALF_UP);
                } else if (dailyRate == null && item.unitPriceTl() != null) {
                    dailyRate = item.unitPriceTl();
                }

                if (dailyRate != null && dailyRate.compareTo(perDiemLimit) > 0) {
                    hasTravelViolation = true;
                    findings.add(FindingBuilder.create(
                            DeterministicRuleConstants.RULE_PER_DIEM_AND_PROCUREMENT,
                            RuleCategory.PROCUREMENT,
                            FindingSeverity.VIOLATION,
                            "2026 H Cetveli Günlük Harcırah Tavanı Aşımı: " + itemName,
                            "Yurtiçi harcırah günlük tutarı (" + DeterministicRuleConstants.formatTl(dailyRate)
                                    + "), 2026 yılı H Cetveli azami yolluk limitini (" + DeterministicRuleConstants.formatTl(perDiemLimit)
                                    + ") aşmaktadır. Yürütücü unvanı/kategorisi: "
                                    + (submission.applicantTitle() != null ? submission.applicantTitle() : "Standart Kadro"),
                            DeterministicRuleConstants.DOC_H_CETVELI,
                            "Madde 12/2",
                            "Saha, arazi çalışması veya toplantı katılımlarında ödenecek gündelikler 2026 yılı H Cetveli tavanlarını aşamaz.",
                            "<= " + DeterministicRuleConstants.formatTl(perDiemLimit),
                            DeterministicRuleConstants.formatTl(dailyRate)
                    ));
                }
            }
        }

        if (hasTravelItems && !hasTravelViolation) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_PER_DIEM_AND_PROCUREMENT,
                    RuleCategory.PROCUREMENT,
                    FindingSeverity.COMPLIANT,
                    "2026 H Cetveli Yolluk Tutarları Uygun",
                    "Yolluk ve gündelik harcama kalemleri 2026 yılı H Cetveli yasal üst limitleri ("
                            + DeterministicRuleConstants.formatTl(perDiemLimit) + " / gün) dâhilindedir.",
                    DeterministicRuleConstants.DOC_H_CETVELI,
                    "Madde 12/2",
                    "Gündelik tutarları ilgili kadro derecesi için belirlenen H Cetveli tutarını aşamaz.",
                    "<= " + DeterministicRuleConstants.formatTl(perDiemLimit),
                    "Limit dâhilinde"
            ));
        }

        // Direct Procurement Threshold Check (4734 KİK 22/d: 620,000 TL)
        BigDecimal requested = submission.requestedBudgetTotalTl();
        if (requested != null && requested.compareTo(DeterministicRuleConstants.DIRECT_PURCHASE_THRESHOLD_TL) > 0) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_PER_DIEM_AND_PROCUREMENT,
                    RuleCategory.PROCUREMENT,
                    FindingSeverity.AMBIGUOUS_COMMISSION,
                    "Doğrudan Temin Parasal Sınırı İncelemesi",
                    "Talep tutarı (" + DeterministicRuleConstants.formatTl(requested)
                            + "), 4734 sayılı Kamu İhale Kanunu Madde 22/d doğrudan temin parasal sınırını ("
                            + DeterministicRuleConstants.formatTl(DeterministicRuleConstants.DIRECT_PURCHASE_THRESHOLD_TL)
                            + ") aşmaktadır. Alımın açık ihale usulü ile yapılması veya BAP Komisyonu / İhale Yetkilisi özel izni gereklidir.",
                    DeterministicRuleConstants.DOC_KIK,
                    "Madde 22/d",
                    "4734 sayılı Kanunun 22 nci maddesinin (d) bendi uyarınca doğrudan temin limitleri aşılmamalıdır.",
                    "<= " + DeterministicRuleConstants.formatTl(DeterministicRuleConstants.DIRECT_PURCHASE_THRESHOLD_TL),
                    DeterministicRuleConstants.formatTl(requested)
            ));
        } else if (submission.detectedSubmissionType() == SubmissionType.PROCUREMENT_ADVANCE && !hasTravelViolation) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_PER_DIEM_AND_PROCUREMENT,
                    RuleCategory.PROCUREMENT,
                    FindingSeverity.COMPLIANT,
                    "Doğrudan Temin ve Avans Limiti Uygun",
                    "Talep tutarı (" + (requested != null ? DeterministicRuleConstants.formatTl(requested) : "Belirtilen Tutar")
                            + "), 4734 sayılı Kanun 22/d doğrudan temin parasal sınırı dâhilindedir.",
                    DeterministicRuleConstants.DOC_KIK,
                    "Madde 22/d",
                    "Doğrudan temin limitleri dâhilinde yapılan alımlar mevzuata uygundur.",
                    "<= " + DeterministicRuleConstants.formatTl(DeterministicRuleConstants.DIRECT_PURCHASE_THRESHOLD_TL),
                    requested != null ? DeterministicRuleConstants.formatTl(requested) : "Uygun"
            ));
        }

        return findings;
    }
}
