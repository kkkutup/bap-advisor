package tr.edu.mersin.bapadvisor.rules;

import java.util.List;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.LimitConfigDto;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;

public interface DeterministicRuleService {
    List<FindingDto> evaluate(StructuredSubmissionDto submission, List<LimitConfigDto> activeLimits);
}
