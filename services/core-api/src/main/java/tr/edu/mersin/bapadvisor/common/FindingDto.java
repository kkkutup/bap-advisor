package tr.edu.mersin.bapadvisor.common;

import java.util.UUID;

public record FindingDto(
        UUID id,
        FindingSource sourceType,
        String ruleCode,
        RuleCategory category,
        FindingSeverity severity,
        String titleTr,
        String explanationTr,
        String regulationDocName,
        String regulationArticleRef,
        String verbatimQuote,
        String expectedValue,
        String actualValue,
        String officerOverrideStatus,
        String officerOverrideReason
) {
}
