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

class RequiredAttachmentsEvaluatorTest {

    private RequiredAttachmentsEvaluator evaluator;
    private RuleLimitsContext context;

    @BeforeEach
    void setUp() {
        evaluator = new RequiredAttachmentsEvaluator();
        context = new RuleLimitsContext(List.of());
    }

    @Test
    @DisplayName("NEW_PROJECT with APPLICATION_FORM and no special flags is COMPLIANT")
    void testNewProjectAllCompliant() {
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "AP2",
                "Prof. Dr. Deniz Kaya",
                "Prof. Dr.",
                "Kimya",
                "Temiz Proje",
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

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
        assertEquals(RuleCategory.DOCUMENTS, findings.get(0).category());
    }

    @Test
    @DisplayName("NEW_PROJECT missing APPLICATION_FORM is MISSING_DOCUMENT")
    void testNewProjectMissingApplicationForm() {
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "AP2",
                "Prof. Dr. Deniz Kaya",
                "Prof. Dr.",
                "Kimya",
                "Eksik Form Projesi",
                new BigDecimal("100000.00"),
                null,
                24,
                null,
                List.of(),
                null,
                true,
                false,
                List.of(), // no attachments
                null
        );

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f -> f.severity() == FindingSeverity.MISSING_DOCUMENT && f.titleTr().contains("Başvuru Formu")));
    }

    @Test
    @DisplayName("NEW_PROJECT TP2 missing THESIS_PROPOSAL is MISSING_DOCUMENT")
    void testNewProjectTp2MissingThesisProposal() {
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "TP2",
                "Doç. Dr. Ahmet Bilge",
                "Doç. Dr.",
                "Fizik",
                "Tez Projesi",
                new BigDecimal("100000.00"),
                null,
                24,
                null,
                List.of(),
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM), // thesis proposal missing
                null
        );

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertTrue(findings.stream().anyMatch(f -> f.severity() == FindingSeverity.MISSING_DOCUMENT && f.titleTr().contains("Tez Önerisi")));
    }

    @Test
    @DisplayName("Ethics keywords detected without ETHICS_APPROVAL is MISSING_DOCUMENT")
    void testEthicsKeywordsWithoutEthicsApproval() {
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "AP2",
                "Prof. Dr. Deniz Kaya",
                "Prof. Dr.",
                "Tıp",
                "İnsan Denekli Proje",
                new BigDecimal("100000.00"),
                null,
                24,
                null,
                List.of(),
                null,
                true,
                true, // ethics keywords detected!
                List.of(DocumentRole.APPLICATION_FORM), // ETHICS_APPROVAL missing
                null
        );

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertTrue(findings.stream().anyMatch(f -> f.severity() == FindingSeverity.MISSING_DOCUMENT && f.titleTr().contains("Etik Kurul")));
    }

    @Test
    @DisplayName("Ethics keywords detected with ETHICS_APPROVAL is COMPLIANT")
    void testEthicsKeywordsWithEthicsApproval() {
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "AP2",
                "Prof. Dr. Deniz Kaya",
                "Prof. Dr.",
                "Tıp",
                "İnsan Denekli Proje",
                new BigDecimal("100000.00"),
                null,
                24,
                null,
                List.of(),
                null,
                true,
                true,
                List.of(DocumentRole.APPLICATION_FORM, DocumentRole.ETHICS_APPROVAL),
                null
        );

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.COMPLIANT, findings.get(0).severity());
    }

    @Test
    @DisplayName("OPEN_ACCESS missing PUBLICATION_PROOF or PROFORMA_INVOICE is MISSING_DOCUMENT")
    void testOpenAccessMissingDocs() {
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.OPEN_ACCESS,
                "AP2",
                "Prof. Dr. Deniz Kaya",
                "Prof. Dr.",
                "Tıp",
                "Yayın Desteği",
                new BigDecimal("60000.00"),
                null,
                null,
                null,
                List.of(),
                "Q1",
                true,
                false,
                List.of(DocumentRole.PUBLICATION_PROOF), // PROFORMA_INVOICE missing
                null
        );

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertTrue(findings.stream().anyMatch(f -> f.severity() == FindingSeverity.MISSING_DOCUMENT && f.titleTr().contains("Fatura")));
    }

    @Test
    @DisplayName("Item > 25,000 TL with proformaCount < 3 is VIOLATION")
    void testItemAbove25kWithInsufficientProformas() {
        List<BudgetLineItemDto> items = List.of(
                new BudgetLineItemDto("CONSUMABLE", "Pahalı Kimyasal", 1.0, "Adet", new BigDecimal("30000.00"), new BigDecimal("30000.00"), true, null, null, 2, true) // proformaCount = 2 < 3
        );
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "AP2",
                "Prof. Dr. Deniz Kaya",
                "Prof. Dr.",
                "Kimya",
                "Proforma Testi",
                new BigDecimal("30000.00"),
                null,
                12,
                null,
                items,
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM, DocumentRole.PROFORMA_INVOICE),
                null
        );

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertTrue(findings.stream().anyMatch(f -> f.severity() == FindingSeverity.VIOLATION && f.titleTr().contains("Yetersiz Proforma")));
    }

    @Test
    @DisplayName("Equipment item > 50,000 TL without TECHNICAL_SPEC is MISSING_DOCUMENT")
    void testEquipmentAbove50kWithoutTechnicalSpec() {
        List<BudgetLineItemDto> items = List.of(
                new BudgetLineItemDto("EQUIPMENT", "Spektrometre Cihazı", 1.0, "Adet", new BigDecimal("60000.00"), new BigDecimal("60000.00"), true, null, null, 3, false) // hasTechnicalSpec = false
        );
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "AP2",
                "Prof. Dr. Deniz Kaya",
                "Prof. Dr.",
                "Fizik",
                "Cihaz Testi",
                new BigDecimal("60000.00"),
                null,
                24,
                null,
                items,
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM, DocumentRole.PROFORMA_INVOICE), // TECHNICAL_SPEC missing
                null
        );

        List<FindingDto> findings = evaluator.evaluate(submission, context);
        assertTrue(findings.stream().anyMatch(f -> f.severity() == FindingSeverity.MISSING_DOCUMENT && f.titleTr().contains("Teknik Şartname")));
    }
}
