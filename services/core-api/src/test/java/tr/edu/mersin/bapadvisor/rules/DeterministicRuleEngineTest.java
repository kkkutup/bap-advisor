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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicRuleEngineTest {

    private DeterministicRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DeterministicRuleEngine();
    }

    @Test
    @DisplayName("Evaluate null submission returns empty list")
    void testNullSubmission() {
        List<FindingDto> findings = engine.evaluate(null, List.of());
        assertNotNull(findings);
        assertTrue(findings.isEmpty());
    }

    @Test
    @DisplayName("Evaluate NEW_PROJECT TP2 complete valid application")
    void testNewProjectTp2CompleteValid() {
        List<BudgetLineItemDto> items = List.of(
                new BudgetLineItemDto("CONSUMABLE", "Laboratuvar Kimyasalı", 2.0, "Adet", new BigDecimal("45000.00"), new BigDecimal("90000.00"), true, null, null, 3, true),
                new BudgetLineItemDto("TRAVEL", "Arazi Çalışması", null, null, null, new BigDecimal("6800.00"), true, 10, new BigDecimal("680.00"), 1, true)
        );
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "TP2",
                "Prof. Dr. Ayşe Yılmaz",
                "Prof. Dr.",
                "Kimya",
                "Yüksek Lisans Tez Projesi",
                new BigDecimal("96800.00"),
                null,
                24,
                null,
                items,
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM, DocumentRole.THESIS_PROPOSAL, DocumentRole.PROFORMA_INVOICE),
                "Notlar"
        );

        List<FindingDto> findings = engine.evaluate(submission, null);
        assertFalse(findings.isEmpty());

        // All findings should be COMPLIANT
        boolean allCompliant = findings.stream().allMatch(f -> f.severity() == FindingSeverity.COMPLIANT);
        assertTrue(allCompliant, "Expected all findings to be COMPLIANT for a valid submission");

        // Verify categories covered: BUDGET, DURATION, FORMAL_SIGNATURE, DOCUMENTS, PROCUREMENT, ELIGIBILITY
        assertTrue(findings.stream().anyMatch(f -> f.category() == RuleCategory.BUDGET && f.ruleCode().equals(DeterministicRuleConstants.RULE_BUDGET_CAP)));
        assertTrue(findings.stream().anyMatch(f -> f.category() == RuleCategory.BUDGET && f.ruleCode().equals(DeterministicRuleConstants.RULE_BUDGET_ARITHMETIC)));
        assertTrue(findings.stream().anyMatch(f -> f.category() == RuleCategory.DURATION));
        assertTrue(findings.stream().anyMatch(f -> f.category() == RuleCategory.FORMAL_SIGNATURE));
        assertTrue(findings.stream().anyMatch(f -> f.category() == RuleCategory.DOCUMENTS));
        assertTrue(findings.stream().anyMatch(f -> f.category() == RuleCategory.ELIGIBILITY));
    }

    @Test
    @DisplayName("Evaluate NEW_PROJECT with multiple violations (Budget cap, unsigned, ethics missing)")
    void testNewProjectMultipleViolations() {
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "TP1", // cap is 30,000 TL
                "Dr. Ali Kaya",
                "Dr.",
                "Biyoloji",
                "Lisans Projesi",
                new BigDecimal("45000.00"), // exceeds 30k
                null,
                12,
                null,
                List.of(),
                null,
                false, // unsigned!
                true,  // ethics keywords detected!
                List.of(DocumentRole.APPLICATION_FORM), // ETHICS_APPROVAL missing!
                null
        );

        List<FindingDto> findings = engine.evaluate(submission, List.of());

        // Check budget violation
        assertTrue(findings.stream().anyMatch(f -> f.ruleCode().equals(DeterministicRuleConstants.RULE_BUDGET_CAP)
                && f.severity() == FindingSeverity.VIOLATION));

        // Check wet signature missing
        assertTrue(findings.stream().anyMatch(f -> f.ruleCode().equals(DeterministicRuleConstants.RULE_WET_SIGNATURE)
                && f.severity() == FindingSeverity.MISSING_DOCUMENT));

        // Check ethics approval missing
        assertTrue(findings.stream().anyMatch(f -> f.ruleCode().equals(DeterministicRuleConstants.RULE_REQUIRED_ATTACHMENTS)
                && f.severity() == FindingSeverity.MISSING_DOCUMENT && f.titleTr().contains("Etik")));
    }

    @Test
    @DisplayName("Evaluate OPEN_ACCESS complete valid submission")
    void testOpenAccessValid() {
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.OPEN_ACCESS,
                "AP2",
                "Prof. Dr. Ahmet Bilge",
                "Prof. Dr.",
                "Tıp",
                "WoS Q1 Makale",
                new BigDecimal("75000.00"),
                null,
                null,
                null,
                List.of(),
                "Q1_OTHER",
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM, DocumentRole.PUBLICATION_PROOF, DocumentRole.PROFORMA_INVOICE),
                null
        );

        List<FindingDto> findings = engine.evaluate(submission, List.of());
        assertTrue(findings.stream().anyMatch(f -> f.ruleCode().equals(DeterministicRuleConstants.RULE_OPEN_ACCESS_CAP)
                && f.severity() == FindingSeverity.COMPLIANT));
    }

    @Test
    @DisplayName("Evaluate ADDITIONAL_BUDGET submission")
    void testAdditionalBudgetEvaluation() {
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.ADDITIONAL_BUDGET,
                "AP2",
                "Doç. Dr. Seda Türk",
                "Doç. Dr.",
                "Mühendislik",
                "Ek Bütçe Talebi",
                new BigDecimal("50000.00"),
                new BigDecimal("200000.00"), // total 250k <= 300k cap, 50k <= 50% (100k)
                null,
                null,
                List.of(),
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM, DocumentRole.SIGNED_PETITION),
                null
        );

        List<FindingDto> findings = engine.evaluate(submission, List.of());
        assertTrue(findings.stream().anyMatch(f -> f.ruleCode().equals(DeterministicRuleConstants.RULE_BUDGET_CAP)
                && f.severity() == FindingSeverity.COMPLIANT));
    }

    @Test
    @DisplayName("Evaluate ADDITIONAL_TIME submission")
    void testAdditionalTimeEvaluation() {
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.ADDITIONAL_TIME,
                "AP2",
                "Prof. Dr. Seda Türk",
                "Prof. Dr.",
                "Mühendislik",
                "Ek Süre Talebi",
                null,
                null,
                10,
                24, // 24 + 10 = 34 <= 36 months, 10 <= 50% (12m)
                List.of(),
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM, DocumentRole.SIGNED_PETITION),
                null
        );

        List<FindingDto> findings = engine.evaluate(submission, List.of());
        assertTrue(findings.stream().anyMatch(f -> f.ruleCode().equals(DeterministicRuleConstants.RULE_DURATION_MAX_36M)
                && f.severity() == FindingSeverity.COMPLIANT));
    }

    @Test
    @DisplayName("Evaluate PROCUREMENT_ADVANCE submission")
    void testProcurementAdvanceEvaluation() {
        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.PROCUREMENT_ADVANCE,
                "AP2",
                "Prof. Dr. Seda Türk",
                "Prof. Dr.",
                "Mühendislik",
                "Avans Talebi",
                new BigDecimal("40000.00"),
                null,
                null,
                null,
                List.of(),
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM, DocumentRole.SIGNED_PETITION),
                null
        );

        List<FindingDto> findings = engine.evaluate(submission, List.of());
        assertTrue(findings.stream().anyMatch(f -> f.ruleCode().equals(DeterministicRuleConstants.RULE_PER_DIEM_AND_PROCUREMENT)
                && f.severity() == FindingSeverity.COMPLIANT));
    }
}
