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
import tr.edu.mersin.bapadvisor.common.RuleCategory;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerDiemAndProcurementEvaluatorTest {

    private PerDiemAndProcurementEvaluator evaluator;
    private RuleLimitsContext context;

    @BeforeEach
    void setUp() {
        evaluator = new PerDiemAndProcurementEvaluator();
        context = new RuleLimitsContext(List.of());
    }

    @Test
    @DisplayName("Prof. Dr. per diem <= 750 TL is COMPLIANT")
    void testProfessorPerDiemCompliant() {
        List<BudgetLineItemDto> items = List.of(
                new BudgetLineItemDto("TRAVEL", "Saha Gündeliği", null, null, null, new BigDecimal("3750.00"), true, 5, new BigDecimal("750.00"), 1, true)
        );
        StructuredSubmissionDto submission = createSubmission("Prof. Dr.", new BigDecimal("3750.00"), items);

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
        assertEquals(RuleCategory.PROCUREMENT, findings.get(0).category());
        assertEquals("Madde 12/2", findings.get(0).regulationArticleRef());
    }

    @Test
    @DisplayName("Prof. Dr. per diem > 750 TL is VIOLATION")
    void testProfessorPerDiemViolation() {
        List<BudgetLineItemDto> items = List.of(
                new BudgetLineItemDto("TRAVEL", "Arazi Yolluk", null, null, null, new BigDecimal("4500.00"), true, 5, new BigDecimal("900.00"), 1, true)
        );
        StructuredSubmissionDto submission = createSubmission("Prof. Dr.", new BigDecimal("4500.00"), items);

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.VIOLATION, findings.get(0).severity());
        assertTrue(findings.get(0).explanationTr().contains("750,00 TL"));
    }

    @Test
    @DisplayName("Doç. Dr. per diem <= 680 TL is COMPLIANT, > 680 TL is VIOLATION")
    void testDocentPerDiem() {
        // 700 TL exceeds 680 TL limit for Doç. Dr.
        List<BudgetLineItemDto> items = List.of(
                new BudgetLineItemDto("TRAVEL", "Kongre Yolluk", null, null, null, new BigDecimal("2100.00"), true, 3, new BigDecimal("700.00"), 1, true)
        );
        StructuredSubmissionDto submission = createSubmission("Doç. Dr.", new BigDecimal("2100.00"), items);

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.VIOLATION, findings.get(0).severity());
        assertTrue(findings.get(0).explanationTr().contains("680,00 TL"));
    }

    @Test
    @DisplayName("Dr. Öğr. Üyesi per diem > 620 TL is VIOLATION")
    void testDrOgrUyesiPerDiemViolation() {
        List<BudgetLineItemDto> items = List.of(
                new BudgetLineItemDto("TRAVEL", "Arazi Yolluk", null, null, null, new BigDecimal("1950.00"), true, 3, new BigDecimal("650.00"), 1, true)
        );
        StructuredSubmissionDto submission = createSubmission("Dr. Öğr. Üyesi", new BigDecimal("1950.00"), items);

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.VIOLATION, findings.get(0).severity());
        assertTrue(findings.get(0).explanationTr().contains("620,00 TL"));
    }

    @Test
    @DisplayName("Research Assistant / Öğr. Gör. per diem > 540 TL is VIOLATION")
    void testInstructorPerDiemViolation() {
        List<BudgetLineItemDto> items = List.of(
                new BudgetLineItemDto("TRAVEL", "Saha", null, null, null, new BigDecimal("1800.00"), true, 3, new BigDecimal("600.00"), 1, true)
        );
        StructuredSubmissionDto submission = createSubmission("Arş. Gör. Dr.", new BigDecimal("1800.00"), items);

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.VIOLATION, findings.get(0).severity());
        assertTrue(findings.get(0).explanationTr().contains("540,00 TL"));
    }

    @Test
    @DisplayName("Total requested budget exceeding direct procurement threshold (620,000 TL) is AMBIGUOUS_COMMISSION")
    void testDirectProcurementThresholdExceeded() {
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.PROCUREMENT_ADVANCE,
                "AP4",
                "Prof. Dr. Fatma Gül",
                "Prof. Dr.",
                "Mühendislik",
                "Büyük Alım Projesi",
                new BigDecimal("650000.00"), // exceeds 620,000 TL
                null,
                12,
                null,
                List.of(),
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM, DocumentRole.PROFORMA_INVOICE),
                null
        );

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f -> f.severity() == FindingSeverity.AMBIGUOUS_COMMISSION && f.titleTr().contains("Doğrudan Temin")));
    }

    @Test
    @DisplayName("PROCUREMENT_ADVANCE within direct procurement threshold is COMPLIANT")
    void testProcurementAdvanceCompliant() {
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.PROCUREMENT_ADVANCE,
                "AP2",
                "Prof. Dr. Fatma Gül",
                "Prof. Dr.",
                "Mühendislik",
                "Normal Alım Projesi",
                new BigDecimal("150000.00"),
                null,
                12,
                null,
                List.of(),
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM, DocumentRole.PROFORMA_INVOICE),
                null
        );

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
    }

    private StructuredSubmissionDto createSubmission(String title, BigDecimal requestedBudget, List<BudgetLineItemDto> items) {
        return new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "AP2",
                "Test Yürütücü",
                title,
                "Bölüm",
                "Yolluk Testi",
                requestedBudget,
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
