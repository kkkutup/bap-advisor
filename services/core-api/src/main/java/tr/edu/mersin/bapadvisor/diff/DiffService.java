package tr.edu.mersin.bapadvisor.diff;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.VersionDiffDto;
import tr.edu.mersin.bapadvisor.document.SubmissionDocument;
import tr.edu.mersin.bapadvisor.document.SubmissionDocumentRepository;
import tr.edu.mersin.bapadvisor.evaluation.Evaluation;
import tr.edu.mersin.bapadvisor.evaluation.EvaluationFinding;
import tr.edu.mersin.bapadvisor.evaluation.EvaluationFindingRepository;
import tr.edu.mersin.bapadvisor.evaluation.EvaluationRepository;
import tr.edu.mersin.bapadvisor.exception.ResourceNotFoundException;
import tr.edu.mersin.bapadvisor.rules.VersionDiffService;
import tr.edu.mersin.bapadvisor.submission.Submission;
import tr.edu.mersin.bapadvisor.submission.SubmissionRepository;
import tr.edu.mersin.bapadvisor.submission.SubmissionVersion;
import tr.edu.mersin.bapadvisor.submission.SubmissionVersionRepository;

import java.math.BigDecimal;
import java.util.*;

@Service
public class DiffService {

    private static final Logger log = LoggerFactory.getLogger(DiffService.class);

    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;
    private final SubmissionDocumentRepository documentRepository;
    private final EvaluationRepository evaluationRepository;
    private final EvaluationFindingRepository findingRepository;
    private final ObjectProvider<VersionDiffService> versionDiffServiceProvider;
    private final ObjectMapper objectMapper;

    public DiffService(
            SubmissionRepository submissionRepository,
            SubmissionVersionRepository versionRepository,
            SubmissionDocumentRepository documentRepository,
            EvaluationRepository evaluationRepository,
            EvaluationFindingRepository findingRepository,
            ObjectProvider<VersionDiffService> versionDiffServiceProvider,
            ObjectMapper objectMapper
    ) {
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
        this.documentRepository = documentRepository;
        this.evaluationRepository = evaluationRepository;
        this.findingRepository = findingRepository;
        this.versionDiffServiceProvider = versionDiffServiceProvider;
        this.objectMapper = objectMapper;
    }

    public VersionDiffResponse calculateDiff(UUID submissionId, int fromVersionNo, int toVersionNo) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Başvuru bulunamadı: " + submissionId));

        SubmissionVersion fromVer = versionRepository.findBySubmissionIdAndVersionNo(submissionId, fromVersionNo)
                .orElseThrow(() -> new ResourceNotFoundException("Kaynak versiyon bulunamadı: v" + fromVersionNo));

        SubmissionVersion toVer = versionRepository.findBySubmissionIdAndVersionNo(submissionId, toVersionNo)
                .orElseThrow(() -> new ResourceNotFoundException("Hedef versiyon bulunamadı: v" + toVersionNo));

        StructuredSubmissionDto fromData = parseStructuredData(fromVer, submission);
        StructuredSubmissionDto toData = parseStructuredData(toVer, submission);

        List<FindingDto> fromFindings = getVersionFindings(fromVer.getId());
        List<FindingDto> toFindings = getVersionFindings(toVer.getId());

        VersionDiffService diffService = versionDiffServiceProvider.getIfAvailable();
        if (diffService != null) {
            try {
                VersionDiffDto diffDto = diffService.calculateDiff(
                        fromVersionNo,
                        fromData,
                        fromFindings,
                        toVersionNo,
                        toData,
                        toFindings
                );
                return VersionDiffResponse.fromCommonDto(diffDto);
            } catch (Exception e) {
                log.error("VersionDiffService invocation failed: {}", e.getMessage(), e);
            }
        }

        // Fallback diff calculation
        return computeFallbackDiff(fromVersionNo, fromVer, fromData, fromFindings, toVersionNo, toVer, toData, toFindings);
    }

    private VersionDiffResponse computeFallbackDiff(
            int fromVersionNo,
            SubmissionVersion fromVer,
            StructuredSubmissionDto fromData,
            List<FindingDto> fromFindings,
            int toVersionNo,
            SubmissionVersion toVer,
            StructuredSubmissionDto toData,
            List<FindingDto> toFindings
    ) {
        BigDecimal fromBudget = fromData.requestedBudgetTotalTl() != null ? fromData.requestedBudgetTotalTl() : BigDecimal.ZERO;
        BigDecimal toBudget = toData.requestedBudgetTotalTl() != null ? toData.requestedBudgetTotalTl() : BigDecimal.ZERO;
        BigDecimal budgetDiff = toBudget.subtract(fromBudget);

        int fromDuration = fromData.requestedDurationMonths() != null ? fromData.requestedDurationMonths() : 0;
        int toDuration = toData.requestedDurationMonths() != null ? toData.requestedDurationMonths() : 0;
        int durationDiff = toDuration - fromDuration;

        List<String> fromDocs = documentRepository.findByVersionId(fromVer.getId()).stream().map(SubmissionDocument::getFileName).toList();
        List<String> toDocs = documentRepository.findByVersionId(toVer.getId()).stream().map(SubmissionDocument::getFileName).toList();

        List<String> docsAdded = toDocs.stream().filter(d -> !fromDocs.contains(d)).toList();
        List<String> docsRemoved = fromDocs.stream().filter(d -> !toDocs.contains(d)).toList();

        String summary = String.format("v%d -> v%d karşılaştırması: Bütçe farkı: %.2f TL, Süre farkı: %d ay.",
                fromVersionNo, toVersionNo, budgetDiff.doubleValue(), durationDiff);

        return new VersionDiffResponse(
                fromVersionNo,
                toVersionNo,
                budgetDiff,
                durationDiff,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                toFindings,
                docsAdded,
                docsRemoved,
                summary
        );
    }

    private List<FindingDto> getVersionFindings(UUID versionId) {
        return evaluationRepository.findTopByVersionIdOrderByCompletedAtDesc(versionId)
                .map(ev -> findingRepository.findByEvaluationId(ev.getId()).stream().map(EvaluationFinding::toDto).toList())
                .orElse(Collections.emptyList());
    }

    private StructuredSubmissionDto parseStructuredData(SubmissionVersion version, Submission submission) {
        if (version.getExtractedMetadataJson() != null && !version.getExtractedMetadataJson().isBlank()) {
            try {
                return objectMapper.readValue(version.getExtractedMetadataJson(), StructuredSubmissionDto.class);
            } catch (Exception e) {
                log.warn("Failed to parse metadata in diff: {}", e.getMessage());
            }
        }
        return new StructuredSubmissionDto(
                submission.getSubmissionType(),
                submission.getProjectCode(),
                submission.getApplicantName(),
                submission.getApplicantTitle(),
                submission.getDepartment(),
                submission.getTitle(),
                null,
                null,
                null,
                null,
                Collections.emptyList(),
                null,
                true,
                false,
                Collections.emptyList(),
                null
        );
    }
}
