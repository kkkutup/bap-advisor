package tr.edu.mersin.bapadvisor.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import tr.edu.mersin.bapadvisor.common.BudgetLineItemDto;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.RuleCategory;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

public class OpenAccessEvaluator implements RuleEvaluator {

    @Override
    public boolean supports(StructuredSubmissionDto submission) {
        if (submission == null) {
            return false;
        }
        if (submission.detectedSubmissionType() == SubmissionType.OPEN_ACCESS) {
            return true;
        }
        if (submission.journalQuartile() != null && !submission.journalQuartile().isBlank()) {
            return true;
        }
        if (submission.budgetLineItems() != null) {
            for (BudgetLineItemDto item : submission.budgetLineItems()) {
                if (item.category() != null && (item.category().equalsIgnoreCase("PUBLICATION") || item.category().equalsIgnoreCase("OPEN_ACCESS"))) {
                    return true;
                }
                if (item.name() != null && (item.name().toLowerCase().contains("açık erişim") || item.name().toLowerCase().contains("apc"))) {
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

        String quartile = submission.journalQuartile();

        if (quartile == null || quartile.isBlank()) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_OPEN_ACCESS_CAP,
                    RuleCategory.OPEN_ACCESS,
                    FindingSeverity.MISSING_DOCUMENT,
                    "Açık Erişim Dergi Dilim Belgesi Eksik",
                    "Açık erişim yayın desteği için derginin Web of Science (WoS) çeyreklik etki dilimi (Q1_TOP10, Q1_OTHER veya Q2) belirtilmelidir.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 6/3",
                    "Başvuru dosyasında makalenin kabul edildiğine dair resmi yazı ve dergi çeyreklik dilim belgesi bulunmak zorundadır.",
                    "Q1_TOP10, Q1_OTHER veya Q2",
                    "Belirtilmemiş"
            ));
            return findings;
        }

        String normalizedQuartile = quartile.trim().toUpperCase();

        if (normalizedQuartile.contains("Q3") || normalizedQuartile.contains("Q4")) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_OPEN_ACCESS_CAP,
                    RuleCategory.OPEN_ACCESS,
                    FindingSeverity.VIOLATION,
                    "Desteklenmeyen Dergi Çeyreklik Dilimi (" + quartile + ")",
                    "WoS Q3 ve Q4 kategorisindeki dergiler MEÜ BAP Açık Erişim desteği kapsamı dışındadır. Yalnızca Q1 ve Q2 dergiler desteklenir.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 6/2",
                    "WoS Q3 ve Q4 kategorisindeki dergiler ile derleme, editöre mektup ve vaka takdimi açık erişim desteği kapsamı dışındadır.",
                    "Q1_TOP10, Q1_OTHER veya Q2",
                    quartile
            ));
            return findings;
        }

        BigDecimal cap = context.getOpenAccessCap(quartile);
        if (cap == null) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_OPEN_ACCESS_CAP,
                    RuleCategory.OPEN_ACCESS,
                    FindingSeverity.AMBIGUOUS_COMMISSION,
                    "Tanınmayan Dergi Dilimi: " + quartile,
                    "Belirtilen dergi çeyreklik bilgisi (" + quartile + ") standart MEÜ BAP Açık Erişim kategorileri ile eşleşmedi; BAP Komisyonu incelemesi gereklidir.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 6/1",
                    "Açık erişim desteği WOS_Q1_TOP10, WOS_Q1_OTHER ve WOS_Q2 tavanları üzerinden sağlanır.",
                    "Standart WoS Dilim Kodu",
                    quartile
            ));
            return findings;
        }

        BigDecimal amount = resolveOpenAccessAmount(submission);

        if (amount == null) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_OPEN_ACCESS_CAP,
                    RuleCategory.OPEN_ACCESS,
                    FindingSeverity.MISSING_DOCUMENT,
                    "Talep Edilen Açık Erişim Tutarı Belirtilmemiş",
                    "Açık erişim desteği için fatura/proforma veya talep edilen tutar belirtilmemiştir.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 6/3",
                    "Başvuru dosyasında yayıncının fatura veya proforması bulunmak zorundadır.",
                    "<= " + DeterministicRuleConstants.formatTl(cap),
                    "Belirtilmemiş"
            ));
            return findings;
        }

        if (amount.compareTo(cap) > 0) {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_OPEN_ACCESS_CAP,
                    RuleCategory.OPEN_ACCESS,
                    FindingSeverity.VIOLATION,
                    "Açık Erişim Destek Tavanı Aşımı (" + quartile + ")",
                    "Talep edilen açık erişim tutarı (" + DeterministicRuleConstants.formatTl(amount)
                            + "), MEÜ 2026 yılı " + quartile + " için belirlenen üst limiti ("
                            + DeterministicRuleConstants.formatTl(cap) + ") aşmaktadır.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 6/1",
                    "WoS Q1 (%10'luk dilim) tavan 120.000 TL, WoS Q1 diğer 80.000 TL, WoS Q2 60.000 TL (KDV dâhil).",
                    "<= " + DeterministicRuleConstants.formatTl(cap),
                    DeterministicRuleConstants.formatTl(amount)
            ));
        } else {
            findings.add(FindingBuilder.create(
                    DeterministicRuleConstants.RULE_OPEN_ACCESS_CAP,
                    RuleCategory.OPEN_ACCESS,
                    FindingSeverity.COMPLIANT,
                    "Açık Erişim Destek Tavanı Uygun (" + quartile + ")",
                    "Talep edilen açık erişim desteği (" + DeterministicRuleConstants.formatTl(amount)
                            + "), " + quartile + " için belirlenen 2026 yılı üst limiti ("
                            + DeterministicRuleConstants.formatTl(cap) + ") dâhilindedir.",
                    DeterministicRuleConstants.DOC_USUL_ESASLAR,
                    "Madde 6/1",
                    "Talep tutarı ilgili kategori üst limitini aşamaz.",
                    "<= " + DeterministicRuleConstants.formatTl(cap),
                    DeterministicRuleConstants.formatTl(amount)
            ));
        }

        return findings;
    }

    private BigDecimal resolveOpenAccessAmount(StructuredSubmissionDto submission) {
        if (submission.requestedBudgetTotalTl() != null) {
            return submission.requestedBudgetTotalTl();
        }
        if (submission.budgetLineItems() != null) {
            for (BudgetLineItemDto item : submission.budgetLineItems()) {
                if (item.category() != null && (item.category().equalsIgnoreCase("PUBLICATION") || item.category().equalsIgnoreCase("OPEN_ACCESS"))) {
                    if (item.totalTl() != null) {
                        return item.totalTl();
                    }
                }
            }
        }
        return null;
    }
}
