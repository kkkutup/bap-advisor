package tr.edu.mersin.bapadvisor.common;

public record EvaluationModulesDto(
        Boolean runDeterministicRules,
        Boolean runRagChecklist,
        Boolean generateActionDrafts,
        String llmProviderOverride,
        String llmModelOverride
) {
    public EvaluationModulesDto {
        if (runDeterministicRules == null) runDeterministicRules = Boolean.TRUE;
        if (runRagChecklist == null) runRagChecklist = Boolean.TRUE;
        if (generateActionDrafts == null) generateActionDrafts = Boolean.TRUE;
    }

    public static EvaluationModulesDto defaultModules() {
        return new EvaluationModulesDto(true, true, true, null, null);
    }
}
