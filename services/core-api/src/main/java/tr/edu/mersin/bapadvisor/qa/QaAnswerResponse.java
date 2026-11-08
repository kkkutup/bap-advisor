package tr.edu.mersin.bapadvisor.qa;

import com.fasterxml.jackson.annotation.JsonProperty;
import tr.edu.mersin.bapadvisor.client.AiEngineClient;

import java.util.List;

public record QaAnswerResponse(
        @JsonProperty("answer_tr") String answerTr,
        @JsonProperty("citations") List<AiEngineClient.CitationItem> citations,
        @JsonProperty("llm_provider_used") String llmProviderUsed
) {}
