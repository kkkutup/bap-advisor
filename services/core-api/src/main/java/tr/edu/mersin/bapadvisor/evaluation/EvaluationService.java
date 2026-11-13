package tr.edu.mersin.bapadvisor.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.mersin.bapadvisor.audit.AuditLogService;
import tr.edu.mersin.bapadvisor.client.AiEngineClient;
import tr.edu.mersin.bapadvisor.common.*;
import tr.edu.mersin.bapadvisor.document.SubmissionDocument;
import tr.edu.mersin.bapadvisor.document.SubmissionDocumentRepository;
import tr.edu.mersin.bapadvisor.exception.ResourceNotFoundException;
import tr.edu.mersin.bapadvisor.limit.LimitConfigService;
import tr.edu.mersin.bapadvisor.rules.DeterministicRuleService;
import tr.edu.mersin.bapadvisor.submission.EvaluationModuleFlagsDto;
import tr.edu.mersin.bapadvisor.submission.Submission;
import tr.edu.mersin.bapadvisor.submission.SubmissionRepository;
import tr.edu.mersin.bapadvisor.submission.SubmissionVersion;
import tr.edu.mersin.bapadvisor.submission.SubmissionVersionRepository;

import java.time.Instant;
import java.util.*;

@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;
    private final SubmissionDocumentRepository documentRepository;
    private final EvaluationRepository evaluationRepository;
    private final EvaluationFindingRepository findingRepository;
    private final LimitConfigService limitConfigService;
    private final AiEngineClient aiEngineClient;
    private final AuditLogService auditLogService;
    private final ObjectProvider<DeterministicRuleService> deterministicRuleServiceProvider;
    private final ObjectMapper objectMapper;

    public EvaluationService(
            SubmissionRepository submissionRepository,
            SubmissionVersionRepository versionRepository,
            SubmissionDocumentRepository documentRepository,
            EvaluationRepository evaluationRepository,
            EvaluationFindingRepository findingRepository,
            LimitConfigService limitConfigService,
            AiEngineClient aiEngineClient,
            AuditLogService auditLogService,
            ObjectProvider<DeterministicRuleService> deterministicRuleServiceProvider,
            ObjectMapper objectMapper
    ) {
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
        this.documentRepository = documentRepository;
        this.evaluationRepository = evaluationRepository;
        this.findingRepository = findingRepository;
        this.limitConfigService = limitConfigService;
        this.aiEngineClient = aiEngineClient;
        this.auditLogService = auditLogService;
        this.deterministicRuleServiceProvider = deterministicRuleServiceProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EvaluationResultResponse evaluate(
            UUID submissionId,
            int versionNo,
            EvaluationModuleFlagsDto moduleFlags,
            UUID actorId
    ) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Başvuru bulunamadı: " + submissionId));

        SubmissionVersion version = versionRepository.findBySubmissionIdAndVersionNo(submissionId, versionNo)
                .orElseThrow(() -> new ResourceNotFoundException("Versiyon bulunamadı: v" + versionNo));

        EvaluationModuleFlagsDto flags = moduleFlags != null ? moduleFlags : EvaluationModuleFlagsDto.defaultFlags();
        version.setSelectedModulesJson(writeJson(flags));
        versionRepository.save(version);

        Instant startedAt = Instant.now();

        // 1. Parse structured submission
        StructuredSubmissionDto submissionDto = parseStructuredData(version, submission);

        // 2. Deterministic Rule Engine
        List<FindingDto> deterministicFindings = new ArrayList<>();
        if (Boolean.TRUE.equals(flags.runDeterministicRules())) {
            List<LimitConfigDto> activeLimits = limitConfigService.getLimits(2026);
            DeterministicRuleService ruleService = deterministicRuleServiceProvider.getIfAvailable();
            if (ruleService != null) {
                try {
                    deterministicFindings.addAll(ruleService.evaluate(submissionDto, activeLimits));
                } catch (Exception e) {
                    log.error("DeterministicRuleService failed: {}", e.getMessage(), e);
                    deterministicFindings.addAll(fallbackDeterministicChecks(submissionDto, activeLimits));
                }
            } else {
                deterministicFindings.addAll(fallbackDeterministicChecks(submissionDto, activeLimits));
            }
        }

        // 3. AI Engine Evaluation (RAG Checklist + Action Drafts)
        String llmProvider = "LOCAL_RULE_ENGINE";
        String llmModel = "deterministic-v1";
        String revisionDraft = null;
        String referralSummary = null;
        List<FindingDto> ragFindings = new ArrayList<>();

        if (Boolean.TRUE.equals(flags.runRagChecklist()) || Boolean.TRUE.equals(flags.generateActionDrafts())) {
            List<SubmissionDocument> docs = documentRepository.findByVersionId(version.getId());
            List<AiEngineClient.DocumentMarkdownItem> docMarkdowns = docs.stream()
                    .map(d -> new AiEngineClient.DocumentMarkdownItem(
                            d.getId(),
                            d.getDocRole(),
                            d.getParsedMarkdown() != null ? d.getParsedMarkdown() : ""
                    ))
                    .toList();

            AiEngineClient.AiEvaluationResult aiResult = aiEngineClient.evaluate(
                    version.getId(),
                    submission.getSubmissionType(),
                    submission.getProjectCode(),
                    submissionDto,
                    docMarkdowns,
                    deterministicFindings,
                    Boolean.TRUE.equals(flags.runRagChecklist()),
                    Boolean.TRUE.equals(flags.generateActionDrafts()),
                    flags.llmProviderOverride(),
                    flags.llmModelOverride()
            );

            if (aiResult != null) {
                llmProvider = aiResult.llmProviderUsed();
                llmModel = aiResult.llmModelName();
                revisionDraft = aiResult.revisionDraftTr();
                referralSummary = aiResult.commissionReferralSummaryTr();
                if (Boolean.TRUE.equals(flags.runRagChecklist()) && aiResult.ragFindings() != null) {
                    ragFindings.addAll(aiResult.ragFindings());
                }
            }
        }

        // 4. Merge all findings
        List<FindingDto> allFindings = new ArrayList<>(deterministicFindings);
        allFindings.addAll(ragFindings);

        // 5. Compute overall compliance status
        String overallStatus = computeOverallStatus(allFindings);

        // 6. Save Evaluation entity
        Evaluation evaluation = new Evaluation();
        evaluation.setVersionId(version.getId());
        evaluation.setLlmProviderUsed(llmProvider);
        evaluation.setLlmModelName(llmModel);
        evaluation.setOverallComplianceStatus(overallStatus);
        evaluation.setDeterministicScoreSummary(writeJson(Map.of("deterministic_count", deterministicFindings.size(), "rag_count", ragFindings.size())));
        evaluation.setRevisionDraftTr(revisionDraft);
        evaluation.setCommissionReferralSummaryTr(referralSummary);
        evaluation.setStartedAt(startedAt);
        evaluation.setCompletedAt(Instant.now());

        Evaluation savedEvaluation = evaluationRepository.save(evaluation);

        // 7. Save EvaluationFinding entities
        List<FindingDto> savedFindingDtos = new ArrayList<>();
        for (FindingDto f : allFindings) {
            EvaluationFinding ef = new EvaluationFinding();
            ef.setEvaluationId(savedEvaluation.getId());
            ef.setSourceType(f.sourceType());
            ef.setRuleCode(f.ruleCode());
            ef.setCategory(f.category());
            ef.setSeverity(f.severity());
            ef.setTitleTr(f.titleTr());
            ef.setExplanationTr(f.explanationTr());
            ef.setRegulationDocName(f.regulationDocName());
            ef.setRegulationArticleRef(f.regulationArticleRef());
            ef.setVerbatimQuote(f.verbatimQuote());
            ef.setExpectedValue(f.expectedValue());
            ef.setActualValue(f.actualValue());
            ef.setOfficerOverrideStatus(f.officerOverrideStatus());
            ef.setOfficerOverrideReason(f.officerOverrideReason());

            EvaluationFinding savedEf = findingRepository.save(ef);
            savedFindingDtos.add(savedEf.toDto());
        }

        // 8. Transition submission to OFFICER_REVIEW
        submission.setStatus(SubmissionStatus.OFFICER_REVIEW);
        submission.setUpdatedAt(Instant.now());
        submissionRepository.save(submission);

        auditLogService.logAction(
                actorId,
                "EVALUATION_EXECUTED",
                "EVALUATION",
                savedEvaluation.getId().toString(),
                null,
                "{\"overall_status\":\"" + overallStatus + "\",\"findings_count\":" + savedFindingDtos.size() + "}"
        );

        return new EvaluationResultResponse(
                savedEvaluation.getId(),
                savedEvaluation.getVersionId(),
                savedEvaluation.getLlmProviderUsed(),
                savedEvaluation.getLlmModelName(),
                savedEvaluation.getOverallComplianceStatus(),
                savedEvaluation.getRevisionDraftTr(),
                savedEvaluation.getCommissionReferralSummaryTr(),
                savedFindingDtos,
                savedEvaluation.getStartedAt(),
                savedEvaluation.getCompletedAt()
        );
    }

    public EvaluationResultResponse getEvaluationResult(UUID submissionId, int versionNo) {
        SubmissionVersion version = versionRepository.findBySubmissionIdAndVersionNo(submissionId, versionNo)
                .orElseThrow(() -> new ResourceNotFoundException("Versiyon bulunamadı: v" + versionNo));

        Evaluation evaluation = evaluationRepository.findTopByVersionIdOrderByCompletedAtDesc(version.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Bu versiyon için henüz değerlendirme raporu oluşturulmamış: v" + versionNo));

        List<FindingDto> findings = findingRepository.findByEvaluationId(evaluation.getId()).stream()
                .map(EvaluationFinding::toDto)
                .toList();

        return new EvaluationResultResponse(
                evaluation.getId(),
                evaluation.getVersionId(),
                evaluation.getLlmProviderUsed(),
                evaluation.getLlmModelName(),
                evaluation.getOverallComplianceStatus(),
                evaluation.getRevisionDraftTr(),
                evaluation.getCommissionReferralSummaryTr(),
                findings,
                evaluation.getStartedAt(),
                evaluation.getCompletedAt()
        );
    }

    @Transactional
    public FindingDto overrideFinding(UUID submissionId, UUID findingId, FindingOverrideRequest request, UUID actorId) {
        EvaluationFinding finding = findingRepository.findById(findingId)
                .orElseThrow(() -> new ResourceNotFoundException("Bulgu kaydı bulunamadı: " + findingId));

        finding.setOfficerOverrideStatus(request.overrideStatus());
        finding.setOfficerOverrideReason(request.overrideReason());
        EvaluationFinding saved = findingRepository.save(finding);

        auditLogService.logAction(
                actorId,
                "FINDING_OVERRIDDEN",
                "EVALUATION_FINDING",
                findingId.toString(),
                null,
                "{\"status\":\"" + request.overrideStatus() + "\",\"reason\":\"" + request.overrideReason() + "\"}"
        );

        return saved.toDto();
    }

    private String computeOverallStatus(List<FindingDto> findings) {
        boolean hasAmbiguous = false;
        boolean hasViolation = false;
        boolean hasMissingDoc = false;

        for (FindingDto f : findings) {
            // If officer waived or approved the override, treat as compliant
            if ("WAIVED".equalsIgnoreCase(f.officerOverrideStatus()) || "APPROVED".equalsIgnoreCase(f.officerOverrideStatus())) {
                continue;
            }

            if (f.severity() == FindingSeverity.AMBIGUOUS_COMMISSION) {
                hasAmbiguous = true;
            } else if (f.severity() == FindingSeverity.VIOLATION) {
                hasViolation = true;
            } else if (f.severity() == FindingSeverity.MISSING_DOCUMENT) {
                hasMissingDoc = true;
            }
        }

        if (hasViolation) {
            return "FORMAL_REJECTION";
        }
        if (hasMissingDoc) {
            return "NEEDS_REVISION";
        }
        if (hasAmbiguous) {
            return "COMMISSION_REVIEW";
        }
        return "COMPLIANT";
    }

    private StructuredSubmissionDto parseStructuredData(SubmissionVersion version, Submission submission) {
        if (version.getExtractedMetadataJson() != null && !version.getExtractedMetadataJson().isBlank()) {
            try {
                return objectMapper.readValue(version.getExtractedMetadataJson(), StructuredSubmissionDto.class);
            } catch (Exception e) {
                log.warn("Failed to parse extractedMetadataJson: {}", e.getMessage());
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
                List.of(DocumentRole.APPLICATION_FORM),
                null
        );
    }

    private List<FindingDto> fallbackDeterministicChecks(StructuredSubmissionDto sub, List<LimitConfigDto> activeLimits) {
        List<FindingDto> list = new ArrayList<>();

        // Wet signature check
        if (!Boolean.TRUE.equals(sub.wetSignaturePresent())) {
            list.add(new FindingDto(
                    UUID.randomUUID(),
                    FindingSource.DETERMINISTIC_RULE,
                    "RULE_WET_SIGNATURE",
                    RuleCategory.FORMAL_SIGNATURE,
                    FindingSeverity.MISSING_DOCUMENT,
                    "Islak İmza Eksikliği",
                    "Teslim edilen başvuru formunda veya dilekçede yetkili yürütücü ıslak imzası tespit edilemedi.",
                    "23.01.2025 Tarihli Mersin Üniversitesi BAP Yönergesi",
                    "Madde 8 (Başvuru Şartları)",
                    "BAP başvuruları yürütücü tarafından ıslak imzalı veya güvenli elektronik imzalı olarak sunulmalıdır.",
                    "İmzalı",
                    "İmzasız / tespit edilemedi",
                    null,
                    null
            ));
        }

        // Duration cap check (missing/0 or max 36 months)
        if (sub.requestedDurationMonths() == null || sub.requestedDurationMonths() <= 0) {
            list.add(new FindingDto(
                    UUID.randomUUID(),
                    FindingSource.DETERMINISTIC_RULE,
                    "RULE_DURATION_MAX_36M",
                    RuleCategory.DURATION,
                    FindingSeverity.MISSING_DOCUMENT,
                    "Talep Edilen Proje Süresi Eksik / Belirtilmemiş",
                    "Başvuru formunda geçerli bir proje süresi (ay) tespit edilemedi.",
                    "23.01.2025 Tarihli Mersin Üniversitesi BAP Yönergesi",
                    "Madde 10 (Proje Süresi)",
                    "Projelerin başlangıç ve bitiş tarihleri ile talep edilen toplam süre formda açıkça belirtilmelidir.",
                    "1 - 36 ay",
                    sub.requestedDurationMonths() != null && sub.requestedDurationMonths() == 0 ? "0 ay (Eksik)" : "Belirtilmemiş",
                    null,
                    null
            ));
        } else if (sub.requestedDurationMonths() > 36) {
            list.add(new FindingDto(
                    UUID.randomUUID(),
                    FindingSource.DETERMINISTIC_RULE,
                    "RULE_DURATION_MAX_36M",
                    RuleCategory.DURATION,
                    FindingSeverity.VIOLATION,
                    "Azami Proje Süresi Aşımı",
                    "Talep edilen proje süresi (" + sub.requestedDurationMonths() + " ay) azami sınır olan 36 ayı aşmaktadır.",
                    "23.01.2025 Tarihli Mersin Üniversitesi BAP Yönergesi",
                    "Madde 10 (Proje Süresi)",
                    "BAP projeleri ek süreler dahil en çok otuz altı (36) ay süreyle desteklenebilir.",
                    "<= 36 ay",
                    sub.requestedDurationMonths() + " ay",
                    null,
                    null
            ));
        }

        return list;
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
