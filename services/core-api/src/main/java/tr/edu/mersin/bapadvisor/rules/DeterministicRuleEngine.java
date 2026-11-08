package tr.edu.mersin.bapadvisor.rules;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.LimitConfigDto;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;

@Service
public class DeterministicRuleEngine implements DeterministicRuleService {

    private final List<RuleEvaluator> evaluators;

    public DeterministicRuleEngine() {
        this.evaluators = List.of(
                new BudgetCapEvaluator(),
                new BudgetArithmeticEvaluator(),
                new OpenAccessEvaluator(),
                new DurationEvaluator(),
                new WetSignatureEvaluator(),
                new RequiredAttachmentsEvaluator(),
                new PerDiemAndProcurementEvaluator(),
                new ApplicantEligibilityEvaluator()
        );
    }

    public DeterministicRuleEngine(List<RuleEvaluator> evaluators) {
        this.evaluators = evaluators != null ? List.copyOf(evaluators) : List.of();
    }

    @Override
    public List<FindingDto> evaluate(StructuredSubmissionDto submission, List<LimitConfigDto> activeLimits) {
        if (submission == null) {
            return List.of();
        }

        RuleLimitsContext context = new RuleLimitsContext(activeLimits);
        List<FindingDto> allFindings = new ArrayList<>();

        for (RuleEvaluator evaluator : evaluators) {
            if (evaluator.supports(submission)) {
                List<FindingDto> findings = evaluator.evaluate(submission, context);
                if (findings != null && !findings.isEmpty()) {
                    allFindings.addAll(findings);
                }
            }
        }

        return List.copyOf(allFindings);
    }
}
