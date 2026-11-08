package tr.edu.mersin.bapadvisor.limit;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record CloneYearRequest(
        @JsonProperty("source_year") @NotNull Integer sourceYear,
        @JsonProperty("target_year") @NotNull Integer targetYear
) {}
