package tr.edu.mersin.bapadvisor.common;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public record VersionDiffDto(
        Integer fromVersion,
        Integer toVersion,
        BigDecimal budgetDiffTl,
        Integer durationDiffMonths,
        List<BudgetLineItemDto> budgetItemsAdded,
        List<BudgetLineItemDto> budgetItemsRemoved,
        List<BudgetItemModified> budgetItemsModified,
        List<FindingDto> resolvedFindings,
        List<FindingDto> newFindings,
        List<FindingDto> unresolvedFindings,
        List<String> documentsAdded,
        List<String> documentsRemoved,
        String summaryTr
) {
    public record BudgetItemModified(
            String name,
            String field,
            String oldValue,
            String newValue
    ) {}

    public VersionDiffDto {
        budgetItemsAdded = budgetItemsAdded == null ? Collections.emptyList() : List.copyOf(budgetItemsAdded);
        budgetItemsRemoved = budgetItemsRemoved == null ? Collections.emptyList() : List.copyOf(budgetItemsRemoved);
        budgetItemsModified = budgetItemsModified == null ? Collections.emptyList() : List.copyOf(budgetItemsModified);
        resolvedFindings = resolvedFindings == null ? Collections.emptyList() : List.copyOf(resolvedFindings);
        newFindings = newFindings == null ? Collections.emptyList() : List.copyOf(newFindings);
        unresolvedFindings = unresolvedFindings == null ? Collections.emptyList() : List.copyOf(unresolvedFindings);
        documentsAdded = documentsAdded == null ? Collections.emptyList() : List.copyOf(documentsAdded);
        documentsRemoved = documentsRemoved == null ? Collections.emptyList() : List.copyOf(documentsRemoved);
    }
}
