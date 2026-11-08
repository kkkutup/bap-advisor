package tr.edu.mersin.bapadvisor.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LimitConfigDto(
        UUID id,
        Integer fiscalYear,
        RuleCategory ruleCategory,
        String code,
        String titleTr,
        BigDecimal maxAmountTl,
        Integer maxDurationMonths,
        String currency,
        Boolean vatIncluded,
        String conditionsJson,
        LocalDate effectiveFrom,
        Boolean isActive
) {
    public LimitConfigDto {
        if (currency == null) currency = "TRY";
        if (vatIncluded == null) vatIncluded = Boolean.TRUE;
        if (isActive == null) isActive = Boolean.TRUE;
    }
}
