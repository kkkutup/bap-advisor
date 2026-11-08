package tr.edu.mersin.bapadvisor.common;

import java.math.BigDecimal;

public record BudgetLineItemDto(
        String category,
        String name,
        Double quantity,
        String unit,
        BigDecimal unitPriceTl,
        BigDecimal totalTl,
        Boolean vatIncluded,
        Integer days,
        BigDecimal dailyRateTl,
        Integer proformaCount,
        Boolean hasTechnicalSpec
) {
    public BudgetLineItemDto {
        if (vatIncluded == null) {
            vatIncluded = Boolean.TRUE;
        }
    }
}
