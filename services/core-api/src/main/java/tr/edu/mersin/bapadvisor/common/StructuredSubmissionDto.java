package tr.edu.mersin.bapadvisor.common;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public record StructuredSubmissionDto(
        SubmissionType detectedSubmissionType,
        String detectedProjectCode,
        String applicantName,
        String applicantTitle,
        String department,
        String projectTitle,
        BigDecimal requestedBudgetTotalTl,
        BigDecimal existingApprovedBudgetTl,
        Integer requestedDurationMonths,
        Integer existingDurationMonths,
        List<BudgetLineItemDto> budgetLineItems,
        String journalQuartile,
        Boolean wetSignaturePresent,
        Boolean ethicsRequiredKeywordsDetected,
        List<DocumentRole> detectedAttachments,
        String notes
) {
    public StructuredSubmissionDto {
        budgetLineItems = budgetLineItems == null ? Collections.emptyList() : List.copyOf(budgetLineItems);
        detectedAttachments = detectedAttachments == null ? Collections.emptyList() : List.copyOf(detectedAttachments);
        if (ethicsRequiredKeywordsDetected == null) {
            ethicsRequiredKeywordsDetected = Boolean.FALSE;
        }
        if (wetSignaturePresent == null) {
            wetSignaturePresent = Boolean.FALSE;
        }
    }
}
