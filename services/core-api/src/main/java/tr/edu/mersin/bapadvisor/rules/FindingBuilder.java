package tr.edu.mersin.bapadvisor.rules;

import java.util.UUID;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.FindingSource;
import tr.edu.mersin.bapadvisor.common.RuleCategory;

public final class FindingBuilder {

    private FindingBuilder() {
        // utility
    }

    public static FindingDto create(
            String ruleCode,
            RuleCategory category,
            FindingSeverity severity,
            String titleTr,
            String explanationTr,
            String regulationDocName,
            String regulationArticleRef,
            String verbatimQuote,
            String expectedValue,
            String actualValue
    ) {
        return new FindingDto(
                UUID.randomUUID(),
                FindingSource.DETERMINISTIC_RULE,
                ruleCode,
                category,
                severity,
                titleTr,
                explanationTr,
                regulationDocName,
                regulationArticleRef,
                verbatimQuote,
                expectedValue,
                actualValue,
                null,
                null
        );
    }
}
