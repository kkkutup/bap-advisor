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

class DurationEvaluatorTest {

    private DurationEvaluator evaluator;
    private RuleLimitsContext context;

    @BeforeEach
    void setUp() {
        evaluator = new DurationEvaluator();
        context = new RuleLimitsContext(List.of());
    }

    @Test
    @DisplayName("NEW_PROJECT TP1 within 12 months is COMPLIANT")
    void testTp1Compliant() {
        StructuredSubmissionDto submission = createSubmission(SubmissionType.NEW_PROJECT, "TP1", 12, null);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
        assertEquals(RuleCategory.DURATION, findings.get(0).category());
    }

    @Test
    @DisplayName("NEW_PROJECT TP2 exceeding 24 months is VIOLATION")
    void testTp2Exceeding24Months() {
        StructuredSubmissionDto submission = createSubmission(SubmissionType.NEW_PROJECT, "TP2", 30, null);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.VIOLATION, findings.get(0).severity());
        assertTrue(findings.get(0).expectedValue().contains("24 ay"));
    }

    @Test
    @DisplayName("NEW_PROJECT AP7 exceeding 12 months is VIOLATION")
    void testAp7Exceeding12Months() {
        StructuredSubmissionDto submission = createSubmission(SubmissionType.NEW_PROJECT, "AP7", 18, null);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.VIOLATION, findings.get(0).severity());
        assertTrue(findings.get(0).expectedValue().contains("12 ay"));
    }

    @Test
    @DisplayName("NEW_PROJECT AP2 within 36 months is COMPLIANT")
    void testAp2Compliant() {
        StructuredSubmissionDto submission = createSubmission(SubmissionType.NEW_PROJECT, "AP2", 36, null);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
    }

    @Test
    @DisplayName("NEW_PROJECT missing duration is MISSING_DOCUMENT")
    void testNewProjectMissingDuration() {
        StructuredSubmissionDto submission = createSubmission(SubmissionType.NEW_PROJECT, "AP2", null, null);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MISSING_DOCUMENT, findings.get(0).severity());
    }

    @Test
    @DisplayName("ADDITIONAL_TIME non-thesis total <= 36m and <= 50% is COMPLIANT")
    void testAdditionalTimeNonThesisCompliant() {
        // AP2: existing 24 months, requested 10 months. Total 34 months <= 36. 10m <= 50% (12m)
        StructuredSubmissionDto submission = createSubmission(SubmissionType.ADDITIONAL_TIME, "AP2", 10, 24);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
    }

    @Test
    @DisplayName("ADDITIONAL_TIME non-thesis total > 36m is VIOLATION")
    void testAdditionalTimeNonThesisTotalExceeds36() {
        // AP2: existing 30 months, requested 8 months. Total 38 months > 36
        StructuredSubmissionDto submission = createSubmission(SubmissionType.ADDITIONAL_TIME, "AP2", 8, 30);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.VIOLATION, findings.get(0).severity());
        assertEquals("Madde 8/1", findings.get(0).regulationArticleRef());
    }

    @Test
    @DisplayName("ADDITIONAL_TIME non-thesis requested > 50% of existing is AMBIGUOUS_COMMISSION")
    void testAdditionalTimeNonThesisExceeds50Percent() {
        // AP2: existing 12 months, requested 8 months. Total 20 months <= 36, but 8 > 50% (6 months)
        StructuredSubmissionDto submission = createSubmission(SubmissionType.ADDITIONAL_TIME, "AP2", 8, 12);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.AMBIGUOUS_COMMISSION, findings.get(0).severity());
        assertEquals("Madde 8/3", findings.get(0).regulationArticleRef());
    }

    @Test
    @DisplayName("ADDITIONAL_TIME thesis total > normal duration is AMBIGUOUS_COMMISSION (requires Commission review)")
    void testAdditionalTimeThesisExceedsNormalDuration() {
        // TP2 (Master thesis, normal 24 months). Existing 24 months, requested 6 months. Total 30 months > 24
        StructuredSubmissionDto submission = createSubmission(SubmissionType.ADDITIONAL_TIME, "TP2", 6, 24);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.AMBIGUOUS_COMMISSION, findings.get(0).severity());
        assertTrue(findings.get(0).explanationTr().contains("öğrencilik"));
    }

    @Test
    @DisplayName("ADDITIONAL_TIME missing duration fields is MISSING_DOCUMENT")
    void testAdditionalTimeMissingFields() {
        StructuredSubmissionDto submission = createSubmission(SubmissionType.ADDITIONAL_TIME, "TP2", null, 24);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MISSING_DOCUMENT, findings.get(0).severity());
    }

    private StructuredSubmissionDto createSubmission(SubmissionType type, String code, Integer requestedMonths, Integer existingMonths) {
        return new StructuredSubmissionDto(
                type,
                code,
                "Prof. Dr. Ali Vural",
                "Prof. Dr.",
                "Fizik",
                "Süre Testi",
                new BigDecimal("100000.00"),
                new BigDecimal("100000.00"),
                requestedMonths,
                existingMonths,
                List.of(),
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM),
                null
        );
    }
}
