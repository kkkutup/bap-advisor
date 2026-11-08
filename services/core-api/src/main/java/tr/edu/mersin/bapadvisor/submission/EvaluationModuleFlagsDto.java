package tr.edu.mersin.bapadvisor.submission;

import com.fasterxml.jackson.annotation.JsonProperty;
import tr.edu.mersin.bapadvisor.common.EvaluationModulesDto;

public record EvaluationModuleFlagsDto(
        @JsonProperty("run_deterministic_rules") Boolean runDeterministicRules,
        @JsonProperty("run_rag_checklist") Boolean runRagChecklist,
        @JsonProperty("generate_action_drafts") Boolean generateActionDrafts,
        @JsonProperty("llm_provider_override") String llmProviderOverride,
        @JsonProperty("llm_model_override") String llmModelOverride
) {
    public EvaluationModuleFlagsDto {
        if (runDeterministicRules == null) runDeterministicRules = Boolean.TRUE;
        if (runRagChecklist == null) runRagChecklist = Boolean.TRUE;
        if (generateActionDrafts == null) generateActionDrafts = Boolean.TRUE;
    }

    public static EvaluationModuleFlagsDto defaultFlags() {
        return new EvaluationModuleFlagsDto(true, true, true, null, null);
    }

    public EvaluationModulesDto toCommonDto() {
        return new EvaluationModulesDto(
                this.runDeterministicRules,
                this.runRagChecklist,
                this.generateActionDrafts,
                this.llmProviderOverride,
                this.llmModelOverride
        );
    }

    public static EvaluationModuleFlagsDto fromCommonDto(EvaluationModulesDto dto) {
        if (dto == null) return defaultFlags();
        return new EvaluationModuleFlagsDto(
                dto.runDeterministicRules(),
                dto.runRagChecklist(),
                dto.generateActionDrafts(),
                dto.llmProviderOverride(),
                dto.llmModelOverride()
        );
    }
}
