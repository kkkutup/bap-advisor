package tr.edu.mersin.bapadvisor.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tr.edu.mersin.bapadvisor.common.DocumentRole;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.LimitConfigDto;
import tr.edu.mersin.bapadvisor.common.RuleCategory;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetCapEvaluatorTest {

    private BudgetCapEvaluator evaluator;
    private RuleLimitsContext emptyLimitsContext;

    @BeforeEach
    void setUp() {
        evaluator = new BudgetCapEvaluator();
        emptyLimitsContext = new RuleLimitsContext(List.of());
    }

    @Test
    @DisplayName("Supports only NEW_PROJECT and ADDITIONAL_BUDGET")
    void testSupports() {
        StructuredSubmissionDto newProj = createSubmission(SubmissionType.NEW_PROJECT, "TP2", new BigDecimal("100000.00"), null);
        StructuredSubmissionDto addBudget = createSubmission(SubmissionType.ADDITIONAL_BUDGET, "TP2", new BigDecimal("20000.00"), new BigDecimal("100000.00"));
        StructuredSubmissionDto openAccess = createSubmission(SubmissionType.OPEN_ACCESS, null, new BigDecimal("50000.00"), null);

        assertTrue(evaluator.supports(newProj));
        assertTrue(evaluator.supports(addBudget));
        assertFalse(evaluator.supports(openAccess));
        assertFalse(evaluator.supports(null));
    }

    @Test
    @DisplayName("NEW_PROJECT TP2 within 125,000 TL cap is COMPLIANT")
    void testNewProjectTp2Compliant() {
        StructuredSubmissionDto submission = createSubmission(SubmissionType.NEW_PROJECT, "TP2", new BigDecimal("120000.00"), null);
        List<FindingDto> findings = evaluator.evaluate(submission, emptyLimitsContext);

        assertEquals(1, findings.size());
        FindingDto f = findings.get(0);
        assertEquals(FindingSeverity.COMPLIANT, f.severity());
        assertEquals(DeterministicRuleConstants.RULE_BUDGET_CAP, f.ruleCode());
        assertEquals(RuleCategory.BUDGET, f.category());
        assertTrue(f.explanationTr().contains("120.000,00 TL"));
        assertTrue(f.explanationTr().contains("125.000,00 TL"));
    }

    @Test
    @DisplayName("NEW_PROJECT TP2 exceeding 125,000 TL cap is VIOLATION")
    void testNewProjectTp2Violation() {
        StructuredSubmissionDto submission = createSubmission(SubmissionType.NEW_PROJECT, "TP2", new BigDecimal("130000.00"), null);
        List<FindingDto> findings = evaluator.evaluate(submission, emptyLimitsContext);

        assertEquals(1, findings.size());
        FindingDto f = findings.get(0);
        assertEquals(FindingSeverity.VIOLATION, f.severity());
        assertEquals("Madde 4", f.regulationArticleRef());
    }

    @Test
    @DisplayName("NEW_PROJECT AP4 within 800,000 TL cap is COMPLIANT")
    void testNewProjectAp4Compliant() {
        StructuredSubmissionDto submission = createSubmission(SubmissionType.NEW_PROJECT, "AP4", new BigDecimal("800000.00"), null);
        List<FindingDto> findings = evaluator.evaluate(submission, emptyLimitsContext);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
        assertEquals("Madde 5", findings.get(0).regulationArticleRef());
    }

    @Test
    @DisplayName("NEW_PROJECT AP4 exceeding 800,000 TL cap is VIOLATION")
    void testNewProjectAp4Violation() {
        StructuredSubmissionDto submission = createSubmission(SubmissionType.NEW_PROJECT, "AP4", new BigDecimal("800000.01"), null);
        List<FindingDto> findings = evaluator.evaluate(submission, emptyLimitsContext);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.VIOLATION, findings.get(0).severity());
    }

    @Test
    @DisplayName("NEW_PROJECT missing requested budget is MISSING_DOCUMENT")
    void testNewProjectMissingBudget() {
        StructuredSubmissionDto submission = createSubmission(SubmissionType.NEW_PROJECT, "AP1", null, null);
        List<FindingDto> findings = evaluator.evaluate(submission, emptyLimitsContext);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MISSING_DOCUMENT, findings.get(0).severity());
    }

    @Test
    @DisplayName("NEW_PROJECT missing project code is AMBIGUOUS_COMMISSION")
    void testNewProjectMissingCode() {
        StructuredSubmissionDto submission = createSubmission(SubmissionType.NEW_PROJECT, null, new BigDecimal("50000.00"), null);
        List<FindingDto> findings = evaluator.evaluate(submission, emptyLimitsContext);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.AMBIGUOUS_COMMISSION, findings.get(0).severity());
    }

    @Test
    @DisplayName("Dynamic LimitConfigDto overrides fallback limit")
    void testDynamicLimitConfigOverride() {
        LimitConfigDto customLimit = new LimitConfigDto(
                UUID.randomUUID(), 2026, RuleCategory.BUDGET, "TP2", "Özel Limit",
                new BigDecimal("150000.00"), 24, "TRY", true, null, null, true
        );
        RuleLimitsContext dynamicContext = new RuleLimitsContext(List.of(customLimit));

        // 140,000 TL would violate default (125k) but is compliant under custom limit (150k)
        StructuredSubmissionDto submission = createSubmission(SubmissionType.NEW_PROJECT, "TP2", new BigDecimal("140000.00"), null);
        List<FindingDto> findings = evaluator.evaluate(submission, dynamicContext);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
        assertTrue(findings.get(0).expectedValue().contains("150.000,00 TL"));
    }

    @Test
    @DisplayName("ADDITIONAL_BUDGET compliant within cap and 50%")
    void testAdditionalBudgetCompliant() {
        // TP2 cap 125,000 TL. Existing: 80,000 TL, Requested: 35,000 TL. Total: 115,000 <= 125,000. Requested <= 50% (40,000)
        StructuredSubmissionDto submission = createSubmission(SubmissionType.ADDITIONAL_BUDGET, "TP2", new BigDecimal("35000.00"), new BigDecimal("80000.00"));
        List<FindingDto> findings = evaluator.evaluate(submission, emptyLimitsContext);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
        assertEquals("Madde 13/2", findings.get(0).regulationArticleRef());
    }

    @Test
    @DisplayName("ADDITIONAL_BUDGET exceeding project cap is VIOLATION")
    void testAdditionalBudgetExceedingCap() {
        // TP2 cap 125,000 TL. Existing: 100,000 TL, Requested: 30,000 TL. Total: 130,000 > 125,000
        StructuredSubmissionDto submission = createSubmission(SubmissionType.ADDITIONAL_BUDGET, "TP2", new BigDecimal("30000.00"), new BigDecimal("100000.00"));
        List<FindingDto> findings = evaluator.evaluate(submission, emptyLimitsContext);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.VIOLATION, findings.get(0).severity());
        assertTrue(findings.get(0).explanationTr().contains("130.000,00 TL"));
    }

    @Test
    @DisplayName("ADDITIONAL_BUDGET exceeding 50% of existing budget is AMBIGUOUS_COMMISSION")
    void testAdditionalBudgetExceeding50Percent() {
        // TP2 cap 125,000 TL. Existing: 40,000 TL, Requested: 25,000 TL. Total: 65,000 <= 125,000, but 25k > 50% (20k)
        StructuredSubmissionDto submission = createSubmission(SubmissionType.ADDITIONAL_BUDGET, "TP2", new BigDecimal("25000.00"), new BigDecimal("40000.00"));
        List<FindingDto> findings = evaluator.evaluate(submission, emptyLimitsContext);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.AMBIGUOUS_COMMISSION, findings.get(0).severity());
        assertTrue(findings.get(0).titleTr().contains("Yüzde 50"));
    }

    @Test
    @DisplayName("ADDITIONAL_BUDGET missing existing budget is AMBIGUOUS_COMMISSION")
    void testAdditionalBudgetMissingExistingBudget() {
        StructuredSubmissionDto submission = createSubmission(SubmissionType.ADDITIONAL_BUDGET, "TP2", new BigDecimal("25000.00"), null);
        List<FindingDto> findings = evaluator.evaluate(submission, emptyLimitsContext);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.AMBIGUOUS_COMMISSION, findings.get(0).severity());
    }

    private StructuredSubmissionDto createSubmission(SubmissionType type, String code, BigDecimal requested, BigDecimal existing) {
        return new StructuredSubmissionDto(
                type,
                code,
                "Dr. Öğr. Üyesi Ahmet Yılmaz",
                "Dr. Öğr. Üyesi",
                "Bilgisayar Mühendisliği",
                "Örnek BAP Başlığı",
                requested,
                existing,
                18,
                null,
                List.of(),
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM),
                "Notlar"
        );
    }
}
