package tr.edu.mersin.bapadvisor;

import org.junit.jupiter.api.Test;
import tr.edu.mersin.bapadvisor.common.*;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BapAdvisorApplicationTests {

    @Test
    void testCommonContractsInstantiation() {
        BudgetLineItemDto item = new BudgetLineItemDto(
                "CONSUMABLE",
                "Kimyasal Madde",
                2.0,
                "ADET",
                BigDecimal.valueOf(15000),
                BigDecimal.valueOf(30000),
                true,
                null,
                null,
                3,
                true
        );
        assertEquals("CONSUMABLE", item.category());
        assertEquals(BigDecimal.valueOf(30000), item.totalTl());

        StructuredSubmissionDto submission = new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "TP2",
                "Dr. Ali Veli",
                "Doç. Dr.",
                "Bilgisayar Mühendisliği",
                "Yapay Zeka Destekli BAP Projesi",
                BigDecimal.valueOf(125000),
                null,
                24,
                null,
                List.of(item),
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM, DocumentRole.PROFORMA_INVOICE),
                "Örnek açıklama"
        );
        assertEquals(SubmissionType.NEW_PROJECT, submission.detectedSubmissionType());
        assertTrue(submission.wetSignaturePresent());
        assertEquals(1, submission.budgetLineItems().size());
    }
}
