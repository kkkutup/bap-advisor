package tr.edu.mersin.bapadvisor.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.RuleCategory;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

public class BudgetCapEvaluator implements RuleEvaluator {

    @Override
    public boolean supports(StructuredSubmissionDto submission) {
        if (submission == null) {
            return false;
        }
        SubmissionType type = submission.detectedSubmissionType();
        return type == SubmissionType.NEW_PROJECT || type == SubmissionType.ADDITIONAL_BUDGET;
    }

    @Override
    public List<FindingDto> evaluate(StructuredSubmissionDto submission, RuleLimitsContext context) {
        List<FindingDto> findings = new ArrayList<>();
        if (submission == null) {
            return findings;
        }

        SubmissionType type = submission.detectedSubmissionType();
        if (type == SubmissionType.NEW_PROJECT) {
            evaluateNewProject(submission, context, findings);
        } else if (type == SubmissionType.ADDITIONAL_BUDGET) {
            evaluateAdditionalBudget(submission, context, findings);
        }

        return findings;
    }

    private void evaluateNewProject(StructuredSubmissionDto submission, RuleLimitsContext context, List<FindingDto> findings) {
        BigDecimal requested = submission.requestedBudgetTotalTl();
        String code = submission.detectedProjectCode();

        if (requested == null) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_BUDGET_CAP,
                    RuleCategory.BUDGET,
                    FindingSeverity.MISSING_DOCUMENT,
                    "Talep Edilen Bütçe Tutarı Belirtilmemiş",
                    "Yeni proje başvurusunda talep edilen toplam bütçe tutarı tespit edilemedi.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 4 ve 5",
                    "Başvuru formunda talep edilen toplam bütçe ayrıntılı olarak belirtilmelidir.",
                    "<= Proje Üst Limiti",
                    "Belirtilmemiş"
            ));
            return;
        }

        if (code == null || code.isBlank()) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_BUDGET_CAP,
                    RuleCategory.BUDGET,
                    FindingSeverity.AMBIGUOUS_COMMISSION,
                    "Proje Tür Kodu Belirtilmemiş",
                    "Proje tür kodu (TP1-AP9) tespit edilemediğinden bütçe üst limiti denetlenemedi; BAP Komisyonu incelemesi gereklidir.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 4 ve 5",
                    "Her başvuru geçerli bir proje türü kodu ile sunulmalıdır.",
                    "Geçerli Proje Kodu (TP1-AP9)",
                    "Belirtilmemiş"
            ));
            return;
        }

        BigDecimal cap = context.getProjectBudgetCap(code);
        if (cap == null) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_BUDGET_CAP,
                    RuleCategory.BUDGET,
                    FindingSeverity.AMBIGUOUS_COMMISSION,
                    "Tanımsız Proje Türü: " + code,
                    "Belirtilen proje türü (" + code + ") için mevzuatta bütçe tavanı tanımlı değildir; BAP Komisyonu takdirine sunulmalıdır.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 4 ve 5",
                    "Proje türleri ve bütçe tavanları BAP Komisyonu ve Senato tarafından belirlenir.",
                    "Tanımlı Proje Türü",
                    code
            ));
            return;
        }

        String articleRef = code.toUpperCase().startsWith("TP") ? "Madde 4" : "Madde 5";
        String verbatim = code.toUpperCase().startsWith("TP")
                ? "Tez projeleri 2026 yılı bütçe tavanları: TP1: 30.000 TL, TP2: 125.000 TL, TP3: 210.000 TL."
                : "Araştırma projeleri 2026 yılı bütçe tavanları: AP1: 250.000 TL, AP2: 300.000 TL, AP3: 500.000 TL, AP4: 800.000 TL, AP7: 100.000 TL, AP9: 5.000 TL.";

        if (requested.compareTo(cap) > 0) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_BUDGET_CAP,
                    RuleCategory.BUDGET,
                    FindingSeverity.VIOLATION,
                    "Proje Bütçe Destek Üst Limiti Aşımı (" + code + ")",
                    "Talep edilen toplam bütçe (" + DeterministicRuleConstants.formatTl(requested) + "), MEÜ 2026 yılı " + code + " bütçe destek üst limitini (" + DeterministicRuleConstants.formatTl(cap) + ") aşmaktadır.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    articleRef,
                    verbatim,
                    "<= " + DeterministicRuleConstants.formatTl(cap),
                    DeterministicRuleConstants.formatTl(requested)
            ));
        } else {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_BUDGET_CAP,
                    RuleCategory.BUDGET,
                    FindingSeverity.COMPLIANT,
                    "Bütçe Destek Üst Limiti Uygun (" + code + ")",
                    "Talep edilen bütçe (" + DeterministicRuleConstants.formatTl(requested) + "), MEÜ 2026 yılı " + code + " bütçe üst limiti (" + DeterministicRuleConstants.formatTl(cap) + ") dâhilindedir.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    articleRef,
                    verbatim,
                    "<= " + DeterministicRuleConstants.formatTl(cap),
                    DeterministicRuleConstants.formatTl(requested)
            ));
        }
    }

    private void evaluateAdditionalBudget(StructuredSubmissionDto submission, RuleLimitsContext context, List<FindingDto> findings) {
        BigDecimal existing = submission.existingApprovedBudgetTl();
        BigDecimal requested = submission.requestedBudgetTotalTl();
        String code = submission.detectedProjectCode();

        if (existing == null) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_BUDGET_CAP,
                    RuleCategory.BUDGET,
                    FindingSeverity.AMBIGUOUS_COMMISSION,
                    "Mevcut Onaylı Bütçe Bilgisi Eksik",
                    "Önceki onaylı bütçe tutarı tespit edilemediğinden ek bütçe tavan denetimi yapılamadı; BAP Komisyonu veya sözleşme kaydı incelenmelidir.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 13/2",
                    "Talep edilen ek bütçe tutarı ile mevcut onaylanmış bütçenin toplamı, ilgili projenin o mali yıl için geçerli olan tür üst limitini aşamaz.",
                    "Onaylı bütçe verisi",
                    "Eksik"
            ));
            return;
        }

        if (requested == null) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_BUDGET_CAP,
                    RuleCategory.BUDGET,
                    FindingSeverity.MISSING_DOCUMENT,
                    "Talep Edilen Ek Bütçe Tutarı Belirtilmemiş",
                    "Ek bütçe başvuru formunda talep edilen ek tutar belirtilmemiştir.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 13/2",
                    "Talep edilen ek bütçe tutarı ve harcama gerekçesi formda yer almalıdır.",
                    "Ek Bütçe Tutarı",
                    "Belirtilmemiş"
            ));
            return;
        }

        BigDecimal total = existing.add(requested);
        BigDecimal cap = code != null ? context.getProjectBudgetCap(code) : null;

        if (cap != null && total.compareTo(cap) > 0) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_BUDGET_CAP,
                    RuleCategory.BUDGET,
                    FindingSeverity.VIOLATION,
                    "Ek Bütçe Dahil Toplam Tavan Aşımı (" + code + ")",
                    "Mevcut onaylı bütçe (" + DeterministicRuleConstants.formatTl(existing) + ") ile talep edilen ek bütçenin ("
                            + DeterministicRuleConstants.formatTl(requested) + ") toplamı (" + DeterministicRuleConstants.formatTl(total)
                            + "), " + code + " için belirlenen bütçe tavanını (" + DeterministicRuleConstants.formatTl(cap) + ") aşmaktadır.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 13/2",
                    "Talep edilen ek bütçe tutarı ile mevcut onaylanmış bütçenin toplamı, ilgili projenin o mali yıl için geçerli olan tür üst limitini aşamaz.",
                    "<= " + DeterministicRuleConstants.formatTl(cap),
                    DeterministicRuleConstants.formatTl(total)
            ));
            return;
        }

        BigDecimal max50 = existing.multiply(new BigDecimal("0.50"));
        if (requested.compareTo(max50) > 0) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_BUDGET_CAP,
                    RuleCategory.BUDGET,
                    FindingSeverity.AMBIGUOUS_COMMISSION,
                    "Ek Bütçe Yüzde 50 Sınırı İncelemesi",
                    "Talep edilen ek bütçe (" + DeterministicRuleConstants.formatTl(requested) + "), mevcut onaylı bütçenin ("
                            + DeterministicRuleConstants.formatTl(existing) + ") yüzde 50'sini (" + DeterministicRuleConstants.formatTl(max50)
                            + ") aşmaktadır. Yönerge gereği BAP Komisyonu özel onayı gereklidir.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 13/2",
                    "Yürürlükteki yönerge gereği ek bütçe tavan yüzdesi (%50) geçilmişse talep mevzuata aykırılık gerekçesiyle BAP Komisyonunca incelenir.",
                    "<= " + DeterministicRuleConstants.formatTl(max50),
                    DeterministicRuleConstants.formatTl(requested)
            ));
        } else {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_BUDGET_CAP,
                    RuleCategory.BUDGET,
                    FindingSeverity.COMPLIANT,
                    "Ek Bütçe Talebi Mevzuata Uygun",
                    "Ek bütçe dâhil toplam bütçe (" + DeterministicRuleConstants.formatTl(total) + ") ve talep edilen ek tutar ("
                            + DeterministicRuleConstants.formatTl(requested) + "), mevzuat sınırları dâhilindedir.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 13/2",
                    "Talep edilen ek bütçe tutarı ile mevcut onaylanmış bütçenin toplamı tür üst limitini aşamaz.",
                    "<= " + (cap != null ? DeterministicRuleConstants.formatTl(cap) : "Tür Tavanı"),
                    DeterministicRuleConstants.formatTl(total)
            ));
        }
    }
}
