package tr.edu.mersin.bapadvisor.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import tr.edu.mersin.bapadvisor.common.BudgetLineItemDto;
import tr.edu.mersin.bapadvisor.common.DocumentRole;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.VersionDiffDto;
import tr.edu.mersin.bapadvisor.common.VersionDiffDto.BudgetItemModified;

@Service
public class VersionDiffCalculator implements VersionDiffService {

    @Override
    public VersionDiffDto calculateDiff(
            int fromVersion,
            StructuredSubmissionDto fromData,
            List<FindingDto> fromFindings,
            int toVersion,
            StructuredSubmissionDto toData,
            List<FindingDto> toFindings
    ) {
        BigDecimal fromBudget = (fromData != null && fromData.requestedBudgetTotalTl() != null)
                ? fromData.requestedBudgetTotalTl()
                : BigDecimal.ZERO;
        BigDecimal toBudget = (toData != null && toData.requestedBudgetTotalTl() != null)
                ? toData.requestedBudgetTotalTl()
                : BigDecimal.ZERO;
        BigDecimal budgetDiffTl = toBudget.subtract(fromBudget);

        Integer fromDuration = (fromData != null && fromData.requestedDurationMonths() != null)
                ? fromData.requestedDurationMonths()
                : 0;
        Integer toDuration = (toData != null && toData.requestedDurationMonths() != null)
                ? toData.requestedDurationMonths()
                : 0;
        Integer durationDiffMonths = toDuration - fromDuration;

        // 1. Budget Line Items comparison
        List<BudgetLineItemDto> fromItems = (fromData != null && fromData.budgetLineItems() != null)
                ? fromData.budgetLineItems()
                : List.of();
        List<BudgetLineItemDto> toItems = (toData != null && toData.budgetLineItems() != null)
                ? toData.budgetLineItems()
                : List.of();

        Map<String, BudgetLineItemDto> fromItemMap = new LinkedHashMap<>();
        for (BudgetLineItemDto item : fromItems) {
            String key = normalizeItemName(item.name());
            fromItemMap.put(key, item);
        }

        Map<String, BudgetLineItemDto> toItemMap = new LinkedHashMap<>();
        for (BudgetLineItemDto item : toItems) {
            String key = normalizeItemName(item.name());
            toItemMap.put(key, item);
        }

        List<BudgetLineItemDto> budgetItemsAdded = new ArrayList<>();
        List<BudgetLineItemDto> budgetItemsRemoved = new ArrayList<>();
        List<BudgetItemModified> budgetItemsModified = new ArrayList<>();

        for (Map.Entry<String, BudgetLineItemDto> entry : toItemMap.entrySet()) {
            String key = entry.getKey();
            BudgetLineItemDto toItem = entry.getValue();
            if (!fromItemMap.containsKey(key)) {
                budgetItemsAdded.add(toItem);
            } else {
                BudgetLineItemDto fromItem = fromItemMap.get(key);
                compareLineItemFields(fromItem, toItem, budgetItemsModified);
            }
        }

        for (Map.Entry<String, BudgetLineItemDto> entry : fromItemMap.entrySet()) {
            String key = entry.getKey();
            if (!toItemMap.containsKey(key)) {
                budgetItemsRemoved.add(entry.getValue());
            }
        }

        // 2. Documents comparison
        List<DocumentRole> fromDocs = (fromData != null && fromData.detectedAttachments() != null)
                ? fromData.detectedAttachments()
                : List.of();
        List<DocumentRole> toDocs = (toData != null && toData.detectedAttachments() != null)
                ? toData.detectedAttachments()
                : List.of();

        List<String> documentsAdded = new ArrayList<>();
        for (DocumentRole doc : toDocs) {
            if (!fromDocs.contains(doc)) {
                documentsAdded.add(doc.name());
            }
        }

        List<String> documentsRemoved = new ArrayList<>();
        for (DocumentRole doc : fromDocs) {
            if (!toDocs.contains(doc)) {
                documentsRemoved.add(doc.name());
            }
        }

        // 3. Findings comparison
        List<FindingDto> safeFromFindings = fromFindings != null ? fromFindings : Collections.emptyList();
        List<FindingDto> safeToFindings = toFindings != null ? toFindings : Collections.emptyList();

        Map<String, FindingDto> fromIssueMap = new LinkedHashMap<>();
        for (FindingDto f : safeFromFindings) {
            if (isIssue(f)) {
                fromIssueMap.put(findingKey(f), f);
            }
        }

        Map<String, FindingDto> toIssueMap = new LinkedHashMap<>();
        for (FindingDto f : safeToFindings) {
            if (isIssue(f)) {
                toIssueMap.put(findingKey(f), f);
            }
        }

        List<FindingDto> resolvedFindings = new ArrayList<>();
        List<FindingDto> unresolvedFindings = new ArrayList<>();
        List<FindingDto> newFindings = new ArrayList<>();

        for (Map.Entry<String, FindingDto> entry : fromIssueMap.entrySet()) {
            String key = entry.getKey();
            FindingDto fromIssue = entry.getValue();
            if (toIssueMap.containsKey(key)) {
                unresolvedFindings.add(toIssueMap.get(key));
            } else {
                resolvedFindings.add(fromIssue);
            }
        }

        for (Map.Entry<String, FindingDto> entry : toIssueMap.entrySet()) {
            String key = entry.getKey();
            if (!fromIssueMap.containsKey(key)) {
                newFindings.add(entry.getValue());
            }
        }

        // 4. Construct human-readable Turkish summary
        String summaryTr = buildSummaryTr(
                fromVersion,
                toVersion,
                budgetDiffTl,
                durationDiffMonths,
                budgetItemsAdded.size(),
                budgetItemsRemoved.size(),
                budgetItemsModified.size(),
                documentsAdded,
                documentsRemoved,
                resolvedFindings.size(),
                unresolvedFindings.size(),
                newFindings.size()
        );

        return new VersionDiffDto(
                fromVersion,
                toVersion,
                budgetDiffTl,
                durationDiffMonths,
                budgetItemsAdded,
                budgetItemsRemoved,
                budgetItemsModified,
                resolvedFindings,
                newFindings,
                unresolvedFindings,
                documentsAdded,
                documentsRemoved,
                summaryTr
        );
    }

    private boolean isIssue(FindingDto f) {
        if (f == null || f.severity() == null) {
            return false;
        }
        return f.severity() == FindingSeverity.VIOLATION
                || f.severity() == FindingSeverity.MISSING_DOCUMENT
                || f.severity() == FindingSeverity.AMBIGUOUS_COMMISSION;
    }

    private String findingKey(FindingDto f) {
        String rule = f.ruleCode() != null ? f.ruleCode() : "UNKNOWN";
        String cat = f.category() != null ? f.category().name() : "GENERAL";
        String title = f.titleTr() != null ? f.titleTr().trim().toLowerCase() : "";
        return rule + ":" + cat + ":" + title;
    }

    private String normalizeItemName(String name) {
        return name != null ? name.trim().toLowerCase() : "";
    }

    private void compareLineItemFields(BudgetLineItemDto fromItem, BudgetLineItemDto toItem, List<BudgetItemModified> modifications) {
        String itemName = toItem.name() != null ? toItem.name() : fromItem.name();

        if (!Objects.equals(fromItem.quantity(), toItem.quantity())) {
            modifications.add(new BudgetItemModified(itemName, "quantity", String.valueOf(fromItem.quantity()), String.valueOf(toItem.quantity())));
        }
        if (!Objects.equals(fromItem.unitPriceTl(), toItem.unitPriceTl())) {
            modifications.add(new BudgetItemModified(itemName, "unitPriceTl", String.valueOf(fromItem.unitPriceTl()), String.valueOf(toItem.unitPriceTl())));
        }
        if (!Objects.equals(fromItem.totalTl(), toItem.totalTl())) {
            modifications.add(new BudgetItemModified(itemName, "totalTl", String.valueOf(fromItem.totalTl()), String.valueOf(toItem.totalTl())));
        }
        if (!Objects.equals(fromItem.category(), toItem.category())) {
            modifications.add(new BudgetItemModified(itemName, "category", String.valueOf(fromItem.category()), String.valueOf(toItem.category())));
        }
        if (!Objects.equals(fromItem.days(), toItem.days())) {
            modifications.add(new BudgetItemModified(itemName, "days", String.valueOf(fromItem.days()), String.valueOf(toItem.days())));
        }
        if (!Objects.equals(fromItem.dailyRateTl(), toItem.dailyRateTl())) {
            modifications.add(new BudgetItemModified(itemName, "dailyRateTl", String.valueOf(fromItem.dailyRateTl()), String.valueOf(toItem.dailyRateTl())));
        }
        if (!Objects.equals(fromItem.hasTechnicalSpec(), toItem.hasTechnicalSpec())) {
            modifications.add(new BudgetItemModified(itemName, "hasTechnicalSpec", String.valueOf(fromItem.hasTechnicalSpec()), String.valueOf(toItem.hasTechnicalSpec())));
        }
        if (!Objects.equals(fromItem.proformaCount(), toItem.proformaCount())) {
            modifications.add(new BudgetItemModified(itemName, "proformaCount", String.valueOf(fromItem.proformaCount()), String.valueOf(toItem.proformaCount())));
        }
    }

    private String buildSummaryTr(
            int fromVersion,
            int toVersion,
            BigDecimal budgetDiff,
            int durationDiff,
            int itemsAdded,
            int itemsRemoved,
            int itemsModified,
            List<String> docsAdded,
            List<String> docsRemoved,
            int resolvedCount,
            int unresolvedCount,
            int newCount
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("v").append(fromVersion).append(" -> v").append(toVersion).append(" revizyon fark özeti: ");

        if (budgetDiff.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("Bütçe +").append(DeterministicRuleConstants.formatTl(budgetDiff)).append(" arttı. ");
        } else if (budgetDiff.compareTo(BigDecimal.ZERO) < 0) {
            sb.append("Bütçe ").append(DeterministicRuleConstants.formatTl(budgetDiff)).append(" azaldı. ");
        } else {
            sb.append("Bütçe değişmedi. ");
        }

        if (durationDiff > 0) {
            sb.append("Süre +").append(durationDiff).append(" ay uzatıldı. ");
        } else if (durationDiff < 0) {
            sb.append("Süre ").append(durationDiff).append(" ay kısaltıldı. ");
        }

        if (itemsAdded > 0 || itemsRemoved > 0 || itemsModified > 0) {
            sb.append("Kalem değişiklikleri: ")
                    .append(itemsAdded).append(" eklendi, ")
                    .append(itemsRemoved).append(" çıkarıldı, ")
                    .append(itemsModified).append(" güncellendi. ");
        }

        if (!docsAdded.isEmpty()) {
            sb.append("Eklenen belgeler: ").append(docsAdded).append(". ");
        }
        if (!docsRemoved.isEmpty()) {
            sb.append("Çıkarılan belgeler: ").append(docsRemoved).append(". ");
        }

        sb.append(resolvedCount).append(" bulgu giderildi, ")
                .append(unresolvedCount).append(" bulgu devam ediyor, ")
                .append(newCount).append(" yeni bulgu oluştu.");

        return sb.toString();
    }
}
