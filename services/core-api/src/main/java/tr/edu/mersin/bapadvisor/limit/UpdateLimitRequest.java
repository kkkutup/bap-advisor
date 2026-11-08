package tr.edu.mersin.bapadvisor.limit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

public record UpdateLimitRequest(
        @JsonProperty("title_tr") String titleTr,
        @JsonProperty("max_amount_tl") BigDecimal maxAmountTl,
        @JsonProperty("max_duration_months") Integer maxDurationMonths,
        @JsonProperty("vat_included") Boolean vatIncluded,
        @JsonProperty("conditions_json") JsonNode conditionsJson,
        @JsonProperty("is_active") Boolean isActive
) {}
