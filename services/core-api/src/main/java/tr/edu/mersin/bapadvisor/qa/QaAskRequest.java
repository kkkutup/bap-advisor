package tr.edu.mersin.bapadvisor.qa;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record QaAskRequest(
        @JsonProperty("question") @NotBlank String question
) {}
