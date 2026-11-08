package tr.edu.mersin.bapadvisor.rules;

import java.util.List;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;

public interface RuleEvaluator {
    boolean supports(StructuredSubmissionDto submission);
    List<FindingDto> evaluate(StructuredSubmissionDto submission, RuleLimitsContext context);
}
