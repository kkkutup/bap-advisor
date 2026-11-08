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

class WetSignatureEvaluatorTest {

    private WetSignatureEvaluator evaluator;
    private RuleLimitsContext context;

    @BeforeEach
    void setUp() {
        evaluator = new WetSignatureEvaluator();
        context = new RuleLimitsContext(List.of());
    }

    @Test
    @DisplayName("Wet signature present is COMPLIANT")
    void testWetSignaturePresentCompliant() {
        StructuredSubmissionDto submission = createSubmission(true);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        FindingDto f = findings.get(0);
        assertEquals(FindingSeverity.COMPLIANT, f.severity());
        assertEquals(RuleCategory.FORMAL_SIGNATURE, f.category());
        assertEquals(DeterministicRuleConstants.RULE_WET_SIGNATURE, f.ruleCode());
        assertEquals("Madde 7/2", f.regulationArticleRef());
    }

    @Test
    @DisplayName("Wet signature absent (false) is MISSING_DOCUMENT")
    void testWetSignatureAbsentMissingDocument() {
        StructuredSubmissionDto submission = createSubmission(false);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        FindingDto f = findings.get(0);
        assertEquals(FindingSeverity.MISSING_DOCUMENT, f.severity());
        assertEquals("Madde 7/2", f.regulationArticleRef());
        assertTrue(f.explanationTr().contains("mavi"));
    }

    @Test
    @DisplayName("Wet signature null defaults to false / MISSING_DOCUMENT")
    void testWetSignatureNullDefaultsMissingDocument() {
        StructuredSubmissionDto submission = createSubmission(null);
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MISSING_DOCUMENT, findings.get(0).severity());
    }

    private StructuredSubmissionDto createSubmission(Boolean wetSignaturePresent) {
        return new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "TP2",
                "Prof. Dr. Cemil Deniz",
                "Prof. Dr.",
                "Tıp",
                "İmza Testi",
                new BigDecimal("50000.00"),
                null,
                12,
                null,
                List.of(),
                null,
                wetSignaturePresent,
                false,
                List.of(DocumentRole.APPLICATION_FORM),
                null
        );
    }
}
