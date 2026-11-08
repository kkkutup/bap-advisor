package tr.edu.mersin.bapadvisor.rules;

import java.util.List;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.VersionDiffDto;

public interface VersionDiffService {
    VersionDiffDto calculateDiff(
        int fromVersion,
        StructuredSubmissionDto fromData,
        List<FindingDto> fromFindings,
        int toVersion,
        StructuredSubmissionDto toData,
        List<FindingDto> toFindings
    );
}
