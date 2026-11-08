package tr.edu.mersin.bapadvisor.diff;

import com.fasterxml.jackson.annotation.JsonProperty;
import tr.edu.mersin.bapadvisor.common.BudgetLineItemDto;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.VersionDiffDto;

import java.math.BigDecimal;
import java.util.List;

public record VersionDiffResponse(
        @JsonProperty("from_version") int fromVersion,
        @JsonProperty("to_version") int toVersion,
        @JsonProperty("budget_diff_tl") BigDecimal budgetDiffTl,
        @JsonProperty("duration_diff_months") int durationDiffMonths,
        @JsonProperty("budget_items_added") List<BudgetLineItemDto> budgetItemsAdded,
        @JsonProperty("budget_items_removed") List<BudgetLineItemDto> budgetItemsRemoved,
        @JsonProperty("budget_items_modified") List<VersionDiffDto.BudgetItemModified> budgetItemsModified,
        @JsonProperty("resolved_findings") List<FindingDto> resolvedFindings,
        @JsonProperty("new_findings") List<FindingDto> newFindings,
        @JsonProperty("unresolved_findings") List<FindingDto> unresolvedFindings,
        @JsonProperty("documents_added") List<String> documentsAdded,
        @JsonProperty("documents_removed") List<String> documentsRemoved,
        @JsonProperty("summary_tr") String summaryTr
) {
    public static VersionDiffResponse fromCommonDto(VersionDiffDto dto) {
        return new VersionDiffResponse(
                dto.fromVersion(),
                dto.toVersion(),
                dto.budgetDiffTl() != null ? dto.budgetDiffTl() : BigDecimal.ZERO,
                dto.durationDiffMonths() != null ? dto.durationDiffMonths() : 0,
                dto.budgetItemsAdded(),
                dto.budgetItemsRemoved(),
                dto.budgetItemsModified(),
                dto.resolvedFindings(),
                dto.newFindings(),
                dto.unresolvedFindings(),
                dto.documentsAdded(),
                dto.documentsRemoved(),
                dto.summaryTr() != null ? dto.summaryTr() : ""
        );
    }
}
