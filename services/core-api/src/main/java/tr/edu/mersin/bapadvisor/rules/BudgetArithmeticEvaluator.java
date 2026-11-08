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

public class BudgetArithmeticEvaluator implements RuleEvaluator {

    @Override
    public boolean supports(StructuredSubmissionDto submission) {
        if (submission == null) {
            return false;
        }
        return (submission.budgetLineItems() != null && !submission.budgetLineItems().isEmpty())
                || submission.requestedBudgetTotalTl() != null;
    }

    @Override
    public List<FindingDto> evaluate(StructuredSubmissionDto submission, RuleLimitsContext context) {
        List<FindingDto> findings = new ArrayList<>();
        if (submission == null) {
            return findings;
        }

        List<BudgetLineItemDto> items = submission.budgetLineItems();
        BigDecimal requested = submission.requestedBudgetTotalTl();
        SubmissionType type = submission.detectedSubmissionType();

        if (items == null || items.isEmpty()) {
            if ((type == SubmissionType.NEW_PROJECT || type == SubmissionType.ADDITIONAL_BUDGET || type == SubmissionType.PROCUREMENT_ADVANCE)
                    && requested != null && requested.compareTo(BigDecimal.ZERO) > 0) {
                findings.add(FindingBuilder.create(
                        DeterministicRuleConstants.RULE_BUDGET_ARITHMETIC,
                        RuleCategory.BUDGET,
                        FindingSeverity.MISSING_DOCUMENT,
                        "Ayrıntılı Bütçe Tablosu Eksik",
                        "Talep edilen bütçe (" + DeterministicRuleConstants.formatTl(requested)
                                + ") için ayrıntılı harcama kalemleri (tüketim, teçhizat, hizmet, yolluk) listesi bulunamadı.",
                        DeterministicRuleConstants.DOC_USUL_ESASLAR,
                        "Madde 10/1",
                        "Başvuru formundaki bütçe kalemleri (Tüketim Malzemeleri, Cihaz ve Donanım, Hizmet Alımı, Yolluk/Gündelik vb.) tek tek ayrıntılandırılmalıdır.",
                        "Ayrıntılı harcama tablosu",
                        "Boş veya eksik"
                ));
            }
            return findings;
        }

        boolean hasItemViolation = false;
        BigDecimal sumOfItems = BigDecimal.ZERO;

        for (BudgetLineItemDto item : items) {
            String itemName = (item.name() != null && !item.name().isBlank()) ? item.name() : "Belirtilmemiş Kalem";

            // 1. Quantity x Unit Price check
            if (item.quantity() != null && item.unitPriceTl() != null && item.totalTl() != null) {
                BigDecimal expected = BigDecimal.valueOf(item.quantity()).multiply(item.unitPriceTl());
                BigDecimal diff = expected.subtract(item.totalTl()).abs();
                if (diff.compareTo(DeterministicRuleConstants.ARITHMETIC_TOLERANCE_TL) > 0) {
                    hasItemViolation = true;
                    findings.add(FindingBuilder.create(
                            DeterministicRuleConstants.RULE_BUDGET_ARITHMETIC,
                            RuleCategory.BUDGET,
                            FindingSeverity.VIOLATION,
                            "Bütçe Kalemi Aritmetik Hatası: " + itemName,
                            "Bütçe kalemi aritmetik hesap hatası: '" + itemName + "' için Miktar (" + item.quantity()
                                    + ") x Birim Fiyat (" + DeterministicRuleConstants.formatTl(item.unitPriceTl())
                                    + ") = " + DeterministicRuleConstants.formatTl(expected)
                                    + " olması gerekirken " + DeterministicRuleConstants.formatTl(item.totalTl()) + " yazılmıştır.",
                            DeterministicRuleConstants.DOC_USUL_ESASLAR,
                            "Madde 10/2",
                            "Her bir harcama satırında belirtilen miktar ile birim fiyatın çarpımı satır toplamına eşit olmak zorundadır.",
                            DeterministicRuleConstants.formatTl(expected),
                            DeterministicRuleConstants.formatTl(item.totalTl())
                    ));
                }
            }

            // 2. Travel Days x Daily Rate check
            if (item.days() != null && item.days() > 0 && item.dailyRateTl() != null && item.totalTl() != null) {
                BigDecimal expectedTravel = BigDecimal.valueOf(item.days()).multiply(item.dailyRateTl());
                BigDecimal diff = expectedTravel.subtract(item.totalTl()).abs();
                if (diff.compareTo(DeterministicRuleConstants.ARITHMETIC_TOLERANCE_TL) > 0) {
                    hasItemViolation = true;
                    findings.add(FindingBuilder.create(
                            DeterministicRuleConstants.RULE_BUDGET_ARITHMETIC,
                            RuleCategory.BUDGET,
                            FindingSeverity.VIOLATION,
                            "Yolluk Kalemi Aritmetik Hatası: " + itemName,
                            "Yolluk kalemi hesap hatası: '" + itemName + "' için Gün (" + item.days()
                                    + ") x Günlük Tutar (" + DeterministicRuleConstants.formatTl(item.dailyRateTl())
                                    + ") = " + DeterministicRuleConstants.formatTl(expectedTravel)
                                    + " olması gerekirken " + DeterministicRuleConstants.formatTl(item.totalTl()) + " yazılmıştır.",
                            DeterministicRuleConstants.DOC_USUL_ESASLAR,
                            "Madde 10/2",
                            "Formdaki her bir harcama satırında belirtilen miktar ile birim fiyatın çarpımı satır toplamına kuruşu kuruşuna eşit olmak zorundadır.",
                            DeterministicRuleConstants.formatTl(expectedTravel),
                            DeterministicRuleConstants.formatTl(item.totalTl())
                    ));
                }
            }

            if (item.totalTl() != null) {
                sumOfItems = sumOfItems.add(item.totalTl());
            }
        }

        // 3. Sum of line items vs requestedBudgetTotalTl check
        if (requested != null) {
            BigDecimal totalDiff = sumOfItems.subtract(requested).abs();
            if (totalDiff.compareTo(DeterministicRuleConstants.ARITHMETIC_TOLERANCE_TL) > 0) {
                findings.add(FindingBuilder.create(
                        DeterministicRuleConstants.RULE_BUDGET_ARITHMETIC,
                        RuleCategory.BUDGET,
                        FindingSeverity.VIOLATION,
                        "Genel Bütçe ve Kalemler Toplamı Uyuşmazlığı",
                        "Bütçe kalemleri toplamı (" + DeterministicRuleConstants.formatTl(sumOfItems)
                                + ") ile talep edilen genel bütçe toplamı (" + DeterministicRuleConstants.formatTl(requested)
                                + ") uyuşmamaktadır (Fark: " + DeterministicRuleConstants.formatTl(totalDiff) + ").",
                        DeterministicRuleConstants.DOC_USUL_ESASLAR,
                        "Madde 10/2",
                        "Tüm satır toplamlarının genel toplamı ise talep edilen genel bütçeye kuruşu kuruşuna eşit olmak zorundadır.",
                        DeterministicRuleConstants.formatTl(requested),
                        DeterministicRuleConstants.formatTl(sumOfItems)
                ));
            } else if (!hasItemViolation) {
                findings.add(FindingBuilder.create(
                        DeterministicRuleConstants.RULE_BUDGET_ARITHMETIC,
                        RuleCategory.BUDGET,
                        FindingSeverity.COMPLIANT,
                        "Bütçe Aritmetik Bütünlüğü Doğrulandı",
                        "Tüm bütçe harcama kalemleri ve genel bütçe toplamı (" + DeterministicRuleConstants.formatTl(requested)
                                + ") aritmetik olarak kuruşu kuruşuna tutarlıdır.",
                        DeterministicRuleConstants.DOC_USUL_ESASLAR,
                        "Madde 10/2",
                        "Bütçe kalemleri ve genel toplam aritmetik olarak tutarlı olmalıdır.",
                        DeterministicRuleConstants.formatTl(requested),
                        DeterministicRuleConstants.formatTl(sumOfItems)
                ));
            }
        }

        return findings;
    }
}
