package tr.edu.mersin.bapadvisor.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tr.edu.mersin.bapadvisor.common.BudgetLineItemDto;
import tr.edu.mersin.bapadvisor.common.DocumentRole;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.FindingSource;
import tr.edu.mersin.bapadvisor.common.RuleCategory;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.SubmissionType;
import tr.edu.mersin.bapadvisor.common.VersionDiffDto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionDiffCalculatorTest {

    private VersionDiffCalculator diffCalculator;

    @BeforeEach
    void setUp() {
        diffCalculator = new VersionDiffCalculator();
    }

    @Test
    @DisplayName("Diff with null submissions is null-safe")
    void testNullSafe() {
        VersionDiffDto diff = diffCalculator.calculateDiff(1, null, null, 2, null, null);
        assertNotNull(diff);
        assertEquals(1, diff.fromVersion());
        assertEquals(2, diff.toVersion());
        assertEquals(BigDecimal.ZERO, diff.budgetDiffTl());
        assertEquals(0, diff.durationDiffMonths());
        assertTrue(diff.budgetItemsAdded().isEmpty());
        assertTrue(diff.budgetItemsRemoved().isEmpty());
        assertTrue(diff.resolvedFindings().isEmpty());
        assertTrue(diff.newFindings().isEmpty());
        assertTrue(diff.unresolvedFindings().isEmpty());
    }

    @Test
    @DisplayName("Calculate budget diff and duration diff between v1 and v2")
    void testBudgetAndDurationDiff() {
        StructuredSubmissionDto v1Data = createSubmission(new BigDecimal("100000.00"), 24, List.of(), List.of());
        StructuredSubmissionDto v2Data = createSubmission(new BigDecimal("120000.00"), 30, List.of(), List.of());

        VersionDiffDto diff = diffCalculator.calculateDiff(1, v1Data, List.of(), 2, v2Data, List.of());

        assertEquals(new BigDecimal("20000.00"), diff.budgetDiffTl());
        assertEquals(6, diff.durationDiffMonths());
        assertTrue(diff.summaryTr().contains("+20.000,00 TL"));
        assertTrue(diff.summaryTr().contains("+6 ay"));
    }

    @Test
    @DisplayName("Calculate budget items added, removed, and modified")
    void testBudgetItemsDiff() {
        BudgetLineItemDto itemA = new BudgetLineItemDto("CONSUMABLE", "Kimyasal A", 2.0, "Adet", new BigDecimal("10000.00"), new BigDecimal("20000.00"), true, null, null, 1, true);
        BudgetLineItemDto itemB = new BudgetLineItemDto("CONSUMABLE", "Kimyasal B", 1.0, "Adet", new BigDecimal("5000.00"), new BigDecimal("5000.00"), true, null, null, 1, true);

        // In v2: itemA modified quantity from 2 to 3 and total from 20k to 30k; itemB removed; itemC added
        BudgetLineItemDto itemAModified = new BudgetLineItemDto("CONSUMABLE", "Kimyasal A", 3.0, "Adet", new BigDecimal("10000.00"), new BigDecimal("30000.00"), true, null, null, 3, true);
        BudgetLineItemDto itemC = new BudgetLineItemDto("EQUIPMENT", "Cihaz C", 1.0, "Adet", new BigDecimal("15000.00"), new BigDecimal("15000.00"), true, null, null, 3, true);

        StructuredSubmissionDto v1Data = createSubmission(new BigDecimal("25000.00"), 12, List.of(itemA, itemB), List.of());
        StructuredSubmissionDto v2Data = createSubmission(new BigDecimal("45000.00"), 12, List.of(itemAModified, itemC), List.of());

        VersionDiffDto diff = diffCalculator.calculateDiff(1, v1Data, List.of(), 2, v2Data, List.of());

        assertEquals(1, diff.budgetItemsAdded().size());
        assertEquals("Cihaz C", diff.budgetItemsAdded().get(0).name());

        assertEquals(1, diff.budgetItemsRemoved().size());
        assertEquals("Kimyasal B", diff.budgetItemsRemoved().get(0).name());

        assertEquals(3, diff.budgetItemsModified().size()); // quantity, totalTl, proformaCount
        assertTrue(diff.budgetItemsModified().stream().anyMatch(m -> m.field().equals("quantity") && m.oldValue().equals("2.0") && m.newValue().equals("3.0")));
    }

    @Test
    @DisplayName("Calculate documents added and removed")
    void testDocumentsDiff() {
        StructuredSubmissionDto v1Data = createSubmission(new BigDecimal("50000.00"), 12, List.of(),
                List.of(DocumentRole.APPLICATION_FORM));
        StructuredSubmissionDto v2Data = createSubmission(new BigDecimal("50000.00"), 12, List.of(),
                List.of(DocumentRole.APPLICATION_FORM, DocumentRole.PROFORMA_INVOICE, DocumentRole.ETHICS_APPROVAL));

        VersionDiffDto diff = diffCalculator.calculateDiff(1, v1Data, List.of(), 2, v2Data, List.of());

        assertEquals(2, diff.documentsAdded().size());
        assertTrue(diff.documentsAdded().contains(DocumentRole.PROFORMA_INVOICE.name()));
        assertTrue(diff.documentsAdded().contains(DocumentRole.ETHICS_APPROVAL.name()));
        assertTrue(diff.documentsRemoved().isEmpty());
    }

    @Test
    @DisplayName("Categorize resolved, unresolved, and new findings")
    void testFindingsCategorization() {
        FindingDto v1BudgetViolation = createFinding(
                DeterministicRuleConstants.RULE_BUDGET_CAP,
                RuleCategory.BUDGET,
                FindingSeverity.VIOLATION,
                "Bütçe Tavanı Aşımı",
                "Talep edilen bütçe üst limiti aşıyor"
        );
        FindingDto v1MissingSignature = createFinding(
                DeterministicRuleConstants.RULE_WET_SIGNATURE,
                RuleCategory.FORMAL_SIGNATURE,
                FindingSeverity.MISSING_DOCUMENT,
                "Islak İmza Eksik",
                "İmza bulunamadı"
        );

        // In v2:
        // - Missing signature is resolved (wet signature is now compliant, so not present in toIssues)
        // - Budget violation is STILL present (unresolved)
        // - Arithmetic error was newly introduced (new finding)
        FindingDto v2BudgetViolationStillPresent = createFinding(
                DeterministicRuleConstants.RULE_BUDGET_CAP,
                RuleCategory.BUDGET,
                FindingSeverity.VIOLATION,
                "Bütçe Tavanı Aşımı",
                "Talep edilen bütçe hâlâ üst limiti aşıyor"
        );
        FindingDto v2NewArithmeticViolation = createFinding(
                DeterministicRuleConstants.RULE_BUDGET_ARITHMETIC,
                RuleCategory.BUDGET,
                FindingSeverity.VIOLATION,
                "Aritmetik Hata",
                "Kalemler toplamı genel bütçeye uymuyor"
        );
        FindingDto v2CompliantSignature = createFinding(
                DeterministicRuleConstants.RULE_WET_SIGNATURE,
                RuleCategory.FORMAL_SIGNATURE,
                FindingSeverity.COMPLIANT,
                "Islak İmza Uygun",
                "İmza mevcut"
        );

        List<FindingDto> fromFindings = List.of(v1BudgetViolation, v1MissingSignature);
        List<FindingDto> toFindings = List.of(v2BudgetViolationStillPresent, v2NewArithmeticViolation, v2CompliantSignature);

        VersionDiffDto diff = diffCalculator.calculateDiff(1, null, fromFindings, 2, null, toFindings);

        assertEquals(1, diff.resolvedFindings().size());
        assertEquals(DeterministicRuleConstants.RULE_WET_SIGNATURE, diff.resolvedFindings().get(0).ruleCode());

        assertEquals(1, diff.unresolvedFindings().size());
        assertEquals(DeterministicRuleConstants.RULE_BUDGET_CAP, diff.unresolvedFindings().get(0).ruleCode());

        assertEquals(1, diff.newFindings().size());
        assertEquals(DeterministicRuleConstants.RULE_BUDGET_ARITHMETIC, diff.newFindings().get(0).ruleCode());

        // Check Turkish summary
        assertTrue(diff.summaryTr().contains("1 bulgu giderildi"));
        assertTrue(diff.summaryTr().contains("1 bulgu devam ediyor"));
        assertTrue(diff.summaryTr().contains("1 yeni bulgu oluştu"));
    }

    private StructuredSubmissionDto createSubmission(BigDecimal budget, Integer duration, List<BudgetLineItemDto> items, List<DocumentRole> docs) {
        return new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "TP2",
                "Prof. Dr. Veli Ak",
                "Prof. Dr.",
                "Kimya",
                "Revizyon Projesi",
                budget,
                null,
                duration,
                null,
                items,
                null,
                true,
                false,
                docs,
                null
        );
    }

    private FindingDto createFinding(String ruleCode, RuleCategory category, FindingSeverity severity, String titleTr, String explanationTr) {
        return new FindingDto(
                UUID.randomUUID(),
                FindingSource.DETERMINISTIC_RULE,
                ruleCode,
                category,
                severity,
                titleTr,
                explanationTr,
                DeterministicRuleConstants.DOC_USUL_ESASLAR,
                "Madde 4",
                "Alıntı",
                "Beklenen",
                "Mevcut",
                null,
                null
        );
    }
}
