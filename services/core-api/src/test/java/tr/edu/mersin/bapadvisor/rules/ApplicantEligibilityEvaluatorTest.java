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

class ApplicantEligibilityEvaluatorTest {

    private ApplicantEligibilityEvaluator evaluator;
    private RuleLimitsContext context;

    @BeforeEach
    void setUp() {
        evaluator = new ApplicantEligibilityEvaluator();
        context = new RuleLimitsContext(List.of());
    }

    @Test
    @DisplayName("Missing applicant title is AMBIGUOUS_COMMISSION")
    void testMissingApplicantTitle() {
        StructuredSubmissionDto submission = createSubmission(null, "AP2");
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        FindingDto f = findings.get(0);
        assertEquals(FindingSeverity.AMBIGUOUS_COMMISSION, f.severity());
        assertEquals(RuleCategory.ELIGIBILITY, f.category());
        assertTrue(f.explanationTr().contains("BAP Komisyonu"));
    }

    @Test
    @DisplayName("Faculty member (Prof. Dr.) for AP2 is COMPLIANT")
    void testFacultyForAp2Compliant() {
        StructuredSubmissionDto submission = createSubmission("Prof. Dr.", "AP2");
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        FindingDto f = findings.get(0);
        assertEquals(FindingSeverity.COMPLIANT, f.severity());
        assertTrue(f.explanationTr().contains(DeterministicRuleConstants.GUARDRAIL_NOTICE));
    }

    @Test
    @DisplayName("Non-faculty (e.g. Arş. Gör.) for AP2 is VIOLATION")
    void testNonFacultyForAp2Violation() {
        StructuredSubmissionDto submission = createSubmission("Arş. Gör.", "AP2");
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        FindingDto f = findings.get(0);
        assertEquals(FindingSeverity.VIOLATION, f.severity());
        assertEquals("Madde 5/1", f.regulationArticleRef());
    }

    @Test
    @DisplayName("Advisor holding PhD for TP2 is COMPLIANT")
    void testDoctorateAdvisorForTp2Compliant() {
        StructuredSubmissionDto submission = createSubmission("Dr. Öğr. Üyesi", "TP2");
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
    }

    @Test
    @DisplayName("Non-doctoral advisor (e.g. Uzman / Öğrenci) for TP2 is VIOLATION")
    void testNonDoctoralForTp2Violation() {
        StructuredSubmissionDto submission = createSubmission("Öğrenci", "TP2");
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.VIOLATION, findings.get(0).severity());
        assertEquals("Madde 4/1-b,c", findings.get(0).regulationArticleRef());
    }

    @Test
    @DisplayName("PhD holder (Dr.) for AP1 Career Start is COMPLIANT")
    void testPhdHolderForAp1Compliant() {
        StructuredSubmissionDto submission = createSubmission("Dr.", "AP1");
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
    }

    @Test
    @DisplayName("Domain Guardrail: Never assesses scientific quality or methodology")
    void testDomainGuardrailInFindings() {
        StructuredSubmissionDto submission = createSubmission("Doç. Dr.", "AP3");
        List<FindingDto> findings = evaluator.evaluate(submission, context);

        assertEquals(1, findings.size());
        FindingDto f = findings.get(0);
        assertTrue(f.explanationTr().contains("yalnızca şekli, idari ve mali mevzuat kontrolüdür"));
        assertTrue(f.explanationTr().contains("bilimsel değeri, akademik özgünlüğü ve araştırma niteliği BAP Komisyonu"));
    }

    private StructuredSubmissionDto createSubmission(String applicantTitle, String projectCode) {
        return new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                projectCode,
                "Araştırmacı Adı",
                applicantTitle,
                "Mühendislik",
                "Başlık",
                new BigDecimal("100000.00"),
                null,
                24,
                null,
                List.of(),
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM),
                null
        );
    }
}
