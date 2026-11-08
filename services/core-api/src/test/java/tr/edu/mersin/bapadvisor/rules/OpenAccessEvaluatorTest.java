package tr.edu.mersin.bapadvisor.rules;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tr.edu.mersin.bapadvisor.common.DocumentRole;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.RuleCategory;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAccessEvaluatorTest {

    private OpenAccessEvaluator evaluator;
    private RuleLimitsContext context;

    @BeforeEach
    void setUp() {
        evaluator = new OpenAccessEvaluator();
        context = new RuleLimitsContext(List.of());
    }

    @Test
    @DisplayName("Supports OPEN_ACCESS submission type or journalQuartile")
    void testSupports() {
        StructuredSubmissionDto oa = createSubmission("Q1_TOP10", new BigDecimal("100000.00"), SubmissionType.OPEN_ACCESS);
        StructuredSubmissionDto npWithQuartile = createSubmission("Q1", new BigDecimal("50000.00"), SubmissionType.NEW_PROJECT);
        StructuredSubmissionDto npNoQuartile = createSubmission(null, new BigDecimal("50000.00"), SubmissionType.NEW_PROJECT);

        assertTrue(evaluator.supports(oa));
        assertTrue(evaluator.supports(npWithQuartile));
        assertFalse(evaluator.supports(npNoQuartile));
        assertFalse(evaluator.supports(null));
    }

    @Test
    @DisplayName("Q1_TOP10 within 120,000 TL cap is COMPLIANT")
    void testQ1Top10Compliant() {
        StructuredSubmissionDto submission = createSubmission("Q1_TOP10", new BigDecimal("115000.00"), SubmissionType.OPEN_ACCESS);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        FindingDto f = findings.get(0);
        assertEquals(FindingSeverity.COMPLIANT, f.severity());
        assertEquals(RuleCategory.OPEN_ACCESS, f.category());
        assertEquals("Madde 6/1", f.regulationArticleRef());
    }

    @Test
    @DisplayName("Q1_TOP10 exceeding 120,000 TL cap is VIOLATION")
    void testQ1Top10Violation() {
        StructuredSubmissionDto submission = createSubmission("WOS_Q1_TOP10", new BigDecimal("125000.00"), SubmissionType.OPEN_ACCESS);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.VIOLATION, findings.get(0).severity());
        assertTrue(findings.get(0).explanationTr().contains("120.000,00 TL"));
    }

    @Test
    @DisplayName("Q1_OTHER within 80,000 TL cap is COMPLIANT")
    void testQ1OtherCompliant() {
        StructuredSubmissionDto submission = createSubmission("Q1_OTHER", new BigDecimal("75000.00"), SubmissionType.OPEN_ACCESS);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
    }

    @Test
    @DisplayName("Q1_OTHER exceeding 80,000 TL cap is VIOLATION")
    void testQ1OtherViolation() {
        StructuredSubmissionDto submission = createSubmission("Q1", new BigDecimal("85000.00"), SubmissionType.OPEN_ACCESS);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.VIOLATION, findings.get(0).severity());
    }

    @Test
    @DisplayName("Q2 within 60,000 TL cap is COMPLIANT")
    void testQ2Compliant() {
        StructuredSubmissionDto submission = createSubmission("WOS_Q2", new BigDecimal("58000.00"), SubmissionType.OPEN_ACCESS);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
    }

    @Test
    @DisplayName("Q2 exceeding 60,000 TL cap is VIOLATION")
    void testQ2Violation() {
        StructuredSubmissionDto submission = createSubmission("Q2", new BigDecimal("65000.00"), SubmissionType.OPEN_ACCESS);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.VIOLATION, findings.get(0).severity());
    }

    @Test
    @DisplayName("Q3 journal quartile is strictly VIOLATION (out of support)")
    void testQ3Rejected() {
        StructuredSubmissionDto submission = createSubmission("Q3", new BigDecimal("30000.00"), SubmissionType.OPEN_ACCESS);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        FindingDto f = findings.get(0);
        assertEquals(FindingSeverity.VIOLATION, f.severity());
        assertEquals("Madde 6/2", f.regulationArticleRef());
        assertTrue(f.explanationTr().contains("kapsamı dışındadır"));
    }

    @Test
    @DisplayName("Q4 journal quartile is strictly VIOLATION (out of support)")
    void testQ4Rejected() {
        StructuredSubmissionDto submission = createSubmission("Q4", new BigDecimal("20000.00"), SubmissionType.OPEN_ACCESS);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.VIOLATION, findings.get(0).severity());
    }

    @Test
    @DisplayName("Missing journal quartile is MISSING_DOCUMENT")
    void testMissingQuartile() {
        StructuredSubmissionDto submission = createSubmission(null, new BigDecimal("40000.00"), SubmissionType.OPEN_ACCESS);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MISSING_DOCUMENT, findings.get(0).severity());
        assertEquals("Madde 6/3", findings.get(0).regulationArticleRef());
    }

    private StructuredSubmissionDto createSubmission(String quartile, BigDecimal amount, SubmissionType type) {
        return new StructuredSubmissionDto(
                type,
                "AP2",
                "Doç. Dr. Selin Kaya",
                "Doç. Dr.",
                "Biyoloji",
                "Makale Yayını",
                amount,
                null,
                null,
                null,
                List.of(),
                quartile,
                true,
                false,
                List.of(DocumentRole.PUBLICATION_PROOF, DocumentRole.PROFORMA_INVOICE),
                null
        );
    }
}
