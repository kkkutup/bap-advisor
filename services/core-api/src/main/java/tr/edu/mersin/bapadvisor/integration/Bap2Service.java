package tr.edu.mersin.bapadvisor.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.mersin.bapadvisor.audit.AuditLogService;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.SubmissionStatus;
import tr.edu.mersin.bapadvisor.evaluation.Evaluation;
import tr.edu.mersin.bapadvisor.evaluation.EvaluationFinding;
import tr.edu.mersin.bapadvisor.evaluation.EvaluationFindingRepository;
import tr.edu.mersin.bapadvisor.evaluation.EvaluationRepository;
import tr.edu.mersin.bapadvisor.exception.ResourceNotFoundException;
import tr.edu.mersin.bapadvisor.submission.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class Bap2Service {

    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;
    private final EvaluationRepository evaluationRepository;
    private final EvaluationFindingRepository findingRepository;
    private final SubmissionService submissionService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public Bap2Service(
            SubmissionRepository submissionRepository,
            SubmissionVersionRepository versionRepository,
            EvaluationRepository evaluationRepository,
            EvaluationFindingRepository findingRepository,
            SubmissionService submissionService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper
    ) {
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
        this.evaluationRepository = evaluationRepository;
        this.findingRepository = findingRepository;
        this.submissionService = submissionService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SubmissionDetailResponse importBap2Submission(Bap2ImportRequest request) {
        CreateSubmissionRequest createReq = new CreateSubmissionRequest(
                request.submissionType(),
                request.projectCode(),
                request.title(),
                request.applicantName(),
                request.applicantTitle(),
                request.department(),
                request.externalBap2Id()
        );

        SubmissionDetailResponse detail = submissionService.createSubmission(createReq, null);

        // Update version 1 with the structured data from bap2
        SubmissionVersion v1 = versionRepository.findBySubmissionIdAndVersionNo(detail.id(), 1)
                .orElseThrow(() -> new ResourceNotFoundException("Versiyon bulunamadı: v1"));

        try {
            v1.setExtractedMetadataJson(objectMapper.writeValueAsString(request.structuredData()));
            versionRepository.save(v1);
        } catch (JsonProcessingException ignored) {}

        submissionRepository.findById(detail.id()).ifPresent(s -> {
            s.setStatus(SubmissionStatus.EXTRACTION_REVIEW);
            submissionRepository.save(s);
        });

        auditLogService.logAction(
                null,
                "BAP2_IMPORT",
                "SUBMISSION",
                detail.id().toString(),
                null,
                "{\"external_bap2_id\":\"" + request.externalBap2Id() + "\"}"
        );

        return submissionService.getSubmissionById(detail.id());
    }

    public Bap2ExportResponse exportBap2Evaluation(UUID submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Başvuru bulunamadı: " + submissionId));

        SubmissionVersion latestVer = versionRepository
                .findBySubmissionIdAndVersionNo(submissionId, submission.getCurrentVersionNo())
                .orElseThrow(() -> new ResourceNotFoundException("Versiyon bulunamadı: v" + submission.getCurrentVersionNo()));

        Evaluation evaluation = evaluationRepository.findTopByVersionIdOrderByCompletedAtDesc(latestVer.getId()).orElse(null);

        List<FindingDto> findings = evaluation != null
                ? findingRepository.findByEvaluationId(evaluation.getId()).stream().map(EvaluationFinding::toDto).toList()
                : Collections.emptyList();

        String overallStatus = evaluation != null ? evaluation.getOverallComplianceStatus() : "NOT_EVALUATED";
        String revisionDraft = evaluation != null ? evaluation.getRevisionDraftTr() : null;
        String referralSummary = evaluation != null ? evaluation.getCommissionReferralSummaryTr() : null;

        return new Bap2ExportResponse(
                submission.getId(),
                submission.getReferenceNo(),
                submission.getExternalBap2Id(),
                submission.getStatus(),
                overallStatus,
                revisionDraft,
                referralSummary,
                findings
        );
    }
}
