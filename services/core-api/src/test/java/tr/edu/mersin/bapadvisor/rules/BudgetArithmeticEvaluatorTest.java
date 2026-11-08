package tr.edu.mersin.bapadvisor.rules;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tr.edu.mersin.bapadvisor.common.BudgetLineItemDto;
import tr.edu.mersin.bapadvisor.common.DocumentRole;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetArithmeticEvaluatorTest {

    private BudgetArithmeticEvaluator evaluator;
    private RuleLimitsContext context;

    @BeforeEach
    void setUp() {
        evaluator = new BudgetArithmeticEvaluator();
        context = new RuleLimitsContext(List.of());
    }

    @Test
    @DisplayName("Arithmetic consistency across items and general total is COMPLIANT")
    void testArithmeticAllCompliant() {
        List<BudgetLineItemDto> items = List.of(
                new BudgetLineItemDto("CONSUMABLE", "Kimyasal Madde", 2.0, "Adet", new BigDecimal("45000.00"), new BigDecimal("90000.00"), true, null, null, 3, true),
                new BudgetLineItemDto("TRAVEL", "Arazi Yolluğu", null, null, null, new BigDecimal("12500.00"), true, 10, new BigDecimal("1250.00"), 1, true)
        );
        StructuredSubmissionDto submission = createSubmission(new BigDecimal("102500.00"), items);

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
        assertEquals(DeterministicRuleConstants.RULE_BUDGET_ARITHMETIC, findings.get(0).ruleCode());
    }

    @Test
    @DisplayName("Quantity x unit price mismatch creates VIOLATION")
    void testQuantityUnitPriceMismatch() {
        // 2 * 45,000 = 90,000 TL, but totalTl is 85,000 TL
        List<BudgetLineItemDto> items = List.of(
                new BudgetLineItemDto("CONSUMABLE", "Kimyasal Sarf", 2.0, "Adet", new BigDecimal("45000.00"), new BigDecimal("85000.00"), true, null, null, 3, true)
        );
        StructuredSubmissionDto submission = createSubmission(new BigDecimal("85000.00"), items);

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f -> f.severity() == FindingSeverity.VIOLATION && f.titleTr().contains("Kimyasal Sarf")));
    }

    @Test
    @DisplayName("Travel days x daily rate mismatch creates VIOLATION")
    void testTravelDaysDailyRateMismatch() {
        // 10 * 680 = 6,800 TL, but totalTl is 7,500 TL
        List<BudgetLineItemDto> items = List.of(
                new BudgetLineItemDto("TRAVEL", "Saha Yolluğu", null, null, null, new BigDecimal("7500.00"), true, 10, new BigDecimal("680.00"), 1, true)
        );
        StructuredSubmissionDto submission = createSubmission(new BigDecimal("7500.00"), items);

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f -> f.severity() == FindingSeverity.VIOLATION && f.titleTr().contains("Yolluk Kalemi")));
    }

    @Test
    @DisplayName("Sum of items does not match requested total creates VIOLATION")
    void testSumMismatchWithRequestedTotal() {
        List<BudgetLineItemDto> items = List.of(
                new BudgetLineItemDto("CONSUMABLE", "Madde A", 1.0, "Adet", new BigDecimal("50000.00"), new BigDecimal("50000.00"), true, null, null, 3, true),
                new BudgetLineItemDto("SERVICE_PURCHASE", "Analiz", 1.0, "Adet", new BigDecimal("30000.00"), new BigDecimal("30000.00"), true, null, null, 3, true)
        );
        // Total of items is 80,000 TL, but requested is 90,000 TL
        StructuredSubmissionDto submission = createSubmission(new BigDecimal("90000.00"), items);

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertTrue(findings.stream().anyMatch(f -> f.severity() == FindingSeverity.VIOLATION && f.titleTr().contains("Genel Bütçe ve Kalemler Toplamı")));
    }

    @Test
    @DisplayName("Floating point difference <= 1.00 TL is treated as COMPLIANT (tolerance)")
    void testToleranceWithinOneLira() {
        List<BudgetLineItemDto> items = List.of(
                new BudgetLineItemDto("CONSUMABLE", "Madde", 3.0, "Adet", new BigDecimal("33333.33"), new BigDecimal("100000.00"), true, null, null, 3, true)
        );
        // 3 * 33333.33 = 99999.99 (diff 0.01 TL <= 1.00 TL)
        StructuredSubmissionDto submission = createSubmission(new BigDecimal("100000.00"), items);

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
    }

    @Test
    @DisplayName("Empty budget items for NEW_PROJECT with requested budget is MISSING_DOCUMENT")
    void testEmptyItemsForNewProject() {
        StructuredSubmissionDto submission = createSubmission(new BigDecimal("50000.00"), List.of());
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MISSING_DOCUMENT, findings.get(0).severity());
        assertTrue(findings.get(0).titleTr().contains("Ayrıntılı Bütçe Tablosu"));
    }

    private StructuredSubmissionDto createSubmission(BigDecimal requestedTotal, List<BudgetLineItemDto> items) {
        return new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "TP2",
                "Prof. Dr. Fatma Demir",
                "Prof. Dr.",
                "Kimya",
                "Test Projesi",
                requestedTotal,
                null,
                24,
                null,
                items,
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM),
                null
        );
    }
}
