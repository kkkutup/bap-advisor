package tr.edu.mersin.bapadvisor.submission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tr.edu.mersin.bapadvisor.audit.AuditLogService;
import tr.edu.mersin.bapadvisor.client.AiEngineClient;
import tr.edu.mersin.bapadvisor.common.*;
import tr.edu.mersin.bapadvisor.document.DocumentStorageService;
import tr.edu.mersin.bapadvisor.document.SubmissionDocument;
import tr.edu.mersin.bapadvisor.document.SubmissionDocumentDto;
import tr.edu.mersin.bapadvisor.document.SubmissionDocumentRepository;
import tr.edu.mersin.bapadvisor.evaluation.EvaluationFinding;
import tr.edu.mersin.bapadvisor.evaluation.EvaluationFindingRepository;
import tr.edu.mersin.bapadvisor.exception.BadRequestException;
import tr.edu.mersin.bapadvisor.exception.ResourceNotFoundException;
import tr.edu.mersin.bapadvisor.limit.LimitConfigService;
import tr.edu.mersin.bapadvisor.rules.DeterministicRuleService;
import tr.edu.mersin.bapadvisor.user.User;
import tr.edu.mersin.bapadvisor.user.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class SubmissionService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);

    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;
    private final SubmissionDocumentRepository documentRepository;
    private final EvaluationFindingRepository findingRepository;
    private final UserRepository userRepository;
    private final DocumentStorageService documentStorageService;
    private final AiEngineClient aiEngineClient;
    private final AuditLogService auditLogService;
    private final LimitConfigService limitConfigService;
    private final ObjectProvider<DeterministicRuleService> deterministicRuleServiceProvider;
    private final ObjectMapper objectMapper;

    public SubmissionService(
            SubmissionRepository submissionRepository,
            SubmissionVersionRepository versionRepository,
            SubmissionDocumentRepository documentRepository,
            EvaluationFindingRepository findingRepository,
            UserRepository userRepository,
            DocumentStorageService documentStorageService,
            AiEngineClient aiEngineClient,
            AuditLogService auditLogService,
            LimitConfigService limitConfigService,
            ObjectProvider<DeterministicRuleService> deterministicRuleServiceProvider,
            ObjectMapper objectMapper
    ) {
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
        this.documentRepository = documentRepository;
        this.findingRepository = findingRepository;
        this.userRepository = userRepository;
        this.documentStorageService = documentStorageService;
        this.aiEngineClient = aiEngineClient;
        this.auditLogService = auditLogService;
        this.limitConfigService = limitConfigService;
        this.deterministicRuleServiceProvider = deterministicRuleServiceProvider;
        this.objectMapper = objectMapper;
    }

    private synchronized String generateNextReferenceNo() {
        int year = LocalDate.now().getYear();
        String prefix = "BAP-" + year + "-";
        long count = submissionRepository.countByReferenceNoStartingWith(prefix + "%");
        return String.format("%s%05d", prefix, count + 1);
    }

    @Transactional
    public SubmissionDetailResponse createSubmission(CreateSubmissionRequest request, UUID creatorId) {
        Submission submission = new Submission();
        submission.setReferenceNo(generateNextReferenceNo());
        submission.setExternalBap2Id(request.externalBap2Id());
        submission.setSubmissionType(request.submissionType());
        submission.setProjectCode(request.projectCode());
        submission.setTitle(request.title().trim());
        submission.setApplicantName(request.applicantName().trim());
        submission.setApplicantTitle(request.applicantTitle());
        submission.setDepartment(request.department());
        submission.setCurrentVersionNo(1);
        submission.setStatus(SubmissionStatus.DRAFT);
        submission.setCreatedBy(creatorId);

        Submission saved = submissionRepository.save(submission);

        // Create empty version 1
        SubmissionVersion v1 = new SubmissionVersion();
        v1.setSubmissionId(saved.getId());
        v1.setVersionNo(1);
        v1.setSelectedModulesJson(writeJson(EvaluationModuleFlagsDto.defaultFlags()));
        SubmissionVersion savedV1 = versionRepository.save(v1);

        auditLogService.logAction(
                creatorId,
                "SUBMISSION_CREATED",
                "SUBMISSION",
                saved.getId().toString(),
                null,
                "{\"reference_no\":\"" + saved.getReferenceNo() + "\"}"
        );

        SubmissionVersionResponse vResponse = mapToVersionResponse(savedV1, Collections.emptyList());
        return mapToDetailResponse(saved, vResponse);
    }

    public SubmissionDetailResponse getSubmissionById(UUID id) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Başvuru bulunamadı: " + id));

        SubmissionVersion latestVersion = versionRepository
                .findBySubmissionIdAndVersionNo(id, submission.getCurrentVersionNo())
                .orElse(null);

        SubmissionVersionResponse vResponse = null;
        if (latestVersion != null) {
            List<SubmissionDocument> docs = documentRepository.findByVersionId(latestVersion.getId());
            vResponse = mapToVersionResponse(latestVersion, docs);
        }

        return mapToDetailResponse(submission, vResponse);
    }

    public PageSubmissionResponse listSubmissions(
            SubmissionStatus status,
            SubmissionType submissionType,
            String projectCode,
            UUID assignedOfficerId,
            String search,
            Pageable pageable
    ) {
        String safeProjectCode = (projectCode == null || projectCode.isBlank()) ? "" : projectCode.trim();
        String safeSearchPattern = (search == null || search.isBlank())
                ? "%"
                : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";

        Page<Submission> page = submissionRepository.findWithFilters(
                status,
                submissionType,
                safeProjectCode,
                assignedOfficerId,
                safeSearchPattern,
                pageable
        );

        Page<SubmissionDetailResponse> detailPage = page.map(s -> {
            SubmissionVersion lv = versionRepository.findBySubmissionIdAndVersionNo(s.getId(), s.getCurrentVersionNo()).orElse(null);
            SubmissionVersionResponse vr = null;
            if (lv != null) {
                List<SubmissionDocument> docs = documentRepository.findByVersionId(lv.getId());
                vr = mapToVersionResponse(lv, docs);
            }
            return mapToDetailResponse(s, vr);
        });

        return PageSubmissionResponse.fromPage(detailPage);
    }

    @Transactional
    public SubmissionVersionResponse createSubmissionVersion(
            UUID submissionId,
            List<MultipartFile> files,
            String rawText,
            String officerNotes,
            Boolean enableVisionFallback,
            UUID actorId
    ) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Başvuru bulunamadı: " + submissionId));

        // Determine new version number: if v1 is DRAFT without documents, reuse v1; else increment
        int nextVersionNo;
        Optional<SubmissionVersion> currentVerOpt = versionRepository
                .findBySubmissionIdAndVersionNo(submissionId, submission.getCurrentVersionNo());

        if (currentVerOpt.isPresent() && documentRepository.findByVersionId(currentVerOpt.get().getId()).isEmpty()) {
            nextVersionNo = submission.getCurrentVersionNo();
        } else {
            nextVersionNo = submission.getCurrentVersionNo() + 1;
        }

        SubmissionVersion version = currentVerOpt.filter(v -> v.getVersionNo() == nextVersionNo)
                .orElseGet(() -> {
                    SubmissionVersion v = new SubmissionVersion();
                    v.setSubmissionId(submissionId);
                    v.setVersionNo(nextVersionNo);
                    return v;
                });

        version.setOfficerNotes(officerNotes);
        version.setSelectedModulesJson(writeJson(EvaluationModuleFlagsDto.defaultFlags()));
        SubmissionVersion savedVersion = versionRepository.save(version);

        List<DocumentStorageService.StoredFileInfo> storedFiles = new ArrayList<>();
        List<AiEngineClient.DocumentFileParam> ocrParams = new ArrayList<>();

        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                DocumentStorageService.StoredFileInfo info = documentStorageService.storeFile(file, submissionId, nextVersionNo);
                storedFiles.add(info);

                SubmissionDocument doc = new SubmissionDocument();
                doc.setVersionId(savedVersion.getId());
                doc.setFileName(info.originalFileName());
                doc.setMimeType(info.mimeType());
                doc.setFileSizeBytes(info.sizeBytes());
                doc.setStoragePath(info.storagePath());
                doc.setSha256Hash(info.sha256Hash());
                doc.setDocRole(DocumentRole.OTHER);
                SubmissionDocument savedDoc = documentRepository.save(doc);

                ocrParams.add(new AiEngineClient.DocumentFileParam(
                        savedDoc.getId(),
                        info.storagePath(),
                        info.mimeType(),
                        info.originalFileName()
                ));
            }
        }

        if (rawText != null && !rawText.isBlank()) {
            DocumentStorageService.StoredFileInfo textInfo = documentStorageService.storeRawText(
                    rawText, submissionId, nextVersionNo, "aciklama_metni.txt"
            );
            storedFiles.add(textInfo);

            SubmissionDocument doc = new SubmissionDocument();
            doc.setVersionId(savedVersion.getId());
            doc.setFileName(textInfo.originalFileName());
            doc.setMimeType(textInfo.mimeType());
            doc.setFileSizeBytes(textInfo.sizeBytes());
            doc.setStoragePath(textInfo.storagePath());
            doc.setSha256Hash(textInfo.sha256Hash());
            doc.setDocRole(DocumentRole.APPLICATION_FORM);
            doc.setParsedMarkdown(rawText);
            SubmissionDocument savedDoc = documentRepository.save(doc);

            ocrParams.add(new AiEngineClient.DocumentFileParam(
                    savedDoc.getId(),
                    textInfo.storagePath(),
                    textInfo.mimeType(),
                    textInfo.originalFileName()
            ));
        }

        // Call AI Engine OCR extraction
        boolean visionFallback = Boolean.TRUE.equals(enableVisionFallback);
        AiEngineClient.OcrExtractionResult ocrResult = aiEngineClient.extractOcr(savedVersion.getId(), ocrParams, visionFallback);

        // Update document classifications and markdown from OCR result
        if (ocrResult.documents() != null) {
            for (AiEngineClient.ExtractedDocumentItem item : ocrResult.documents()) {
                if (item.docId() != null) {
                    documentRepository.findById(item.docId()).ifPresent(d -> {
                        if (item.docRole() != null) d.setDocRole(item.docRole());
                        if (item.ocrConfidence() != null) d.setOcrConfidence(item.ocrConfidence());
                        if (item.hasSignatureDetected() != null) d.setHasSignatureDetected(item.hasSignatureDetected());
                        if (item.parsedMarkdown() != null) d.setParsedMarkdown(item.parsedMarkdown());
                        if (item.parsedTablesJson() != null) d.setParsedTablesJson(item.parsedTablesJson());
                        d.setOcrStatus("COMPLETED");
                        documentRepository.save(d);
                    });
                }
            }
        }

        StructuredSubmissionDto structuredData = ocrResult.structuredData();
        if (structuredData == null) {
            structuredData = createInitialStructuredData(submission, ocrParams);
        }

        // Two-way synchronization between user form input and OCR structured data:
        String resolvedTitle = (structuredData.projectTitle() != null && !structuredData.projectTitle().isBlank())
                ? structuredData.projectTitle().trim()
                : (submission.getTitle() != null ? submission.getTitle().trim() : "");
        String resolvedApplicant = (structuredData.applicantName() != null && !structuredData.applicantName().isBlank())
                ? structuredData.applicantName().trim()
                : (submission.getApplicantName() != null ? submission.getApplicantName().trim() : "");
        String resolvedApplicantTitle = (structuredData.applicantTitle() != null && !structuredData.applicantTitle().isBlank())
                ? structuredData.applicantTitle().trim()
                : submission.getApplicantTitle();
        String resolvedDept = (structuredData.department() != null && !structuredData.department().isBlank())
                ? structuredData.department().trim()
                : submission.getDepartment();
        String resolvedCode = (structuredData.detectedProjectCode() != null && !structuredData.detectedProjectCode().isBlank())
                ? structuredData.detectedProjectCode().trim()
                : submission.getProjectCode();
        SubmissionType resolvedType = (structuredData.detectedSubmissionType() != null)
                ? structuredData.detectedSubmissionType()
                : submission.getSubmissionType();

        // If user typed a specific title, but OCR produced placeholder or empty
        if ((resolvedTitle.isBlank() || resolvedTitle.equalsIgnoreCase("Belgeden Otomatik Çıkarılacak"))
                && submission.getTitle() != null && !submission.getTitle().equalsIgnoreCase("Belgeden Otomatik Çıkarılacak")) {
            resolvedTitle = submission.getTitle().trim();
        }
        if ((resolvedApplicant.isBlank() || resolvedApplicant.equalsIgnoreCase("Belgeden Otomatik Çıkarılacak"))
                && submission.getApplicantName() != null && !submission.getApplicantName().equalsIgnoreCase("Belgeden Otomatik Çıkarılacak")) {
            resolvedApplicant = submission.getApplicantName().trim();
        }

        // Update submission fields if they were placeholders or empty and OCR found real values
        if ((submission.getTitle() == null || submission.getTitle().isBlank() || submission.getTitle().equalsIgnoreCase("Belgeden Otomatik Çıkarılacak"))
                && !resolvedTitle.isBlank() && !resolvedTitle.equalsIgnoreCase("Belgeden Otomatik Çıkarılacak")) {
            submission.setTitle(resolvedTitle);
        }
        if ((submission.getApplicantName() == null || submission.getApplicantName().isBlank() || submission.getApplicantName().equalsIgnoreCase("Belgeden Otomatik Çıkarılacak"))
                && !resolvedApplicant.isBlank() && !resolvedApplicant.equalsIgnoreCase("Belgeden Otomatik Çıkarılacak")) {
            submission.setApplicantName(resolvedApplicant);
        }
        if ((submission.getApplicantTitle() == null || submission.getApplicantTitle().isBlank()) && resolvedApplicantTitle != null) {
            submission.setApplicantTitle(resolvedApplicantTitle);
        }
        if ((submission.getDepartment() == null || submission.getDepartment().isBlank()) && resolvedDept != null) {
            submission.setDepartment(resolvedDept);
        }
        if ((submission.getProjectCode() == null || submission.getProjectCode().isBlank()) && resolvedCode != null) {
            submission.setProjectCode(resolvedCode);
        }
        if (submission.getSubmissionType() == null && resolvedType != null) {
            submission.setSubmissionType(resolvedType);
        }

        structuredData = new StructuredSubmissionDto(
                resolvedType,
                resolvedCode,
                resolvedApplicant,
                resolvedApplicantTitle,
                resolvedDept,
                resolvedTitle,
                structuredData.requestedBudgetTotalTl(),
                structuredData.existingApprovedBudgetTl(),
                structuredData.requestedDurationMonths(),
                structuredData.existingDurationMonths(),
                structuredData.budgetLineItems(),
                structuredData.journalQuartile(),
                structuredData.wetSignaturePresent(),
                structuredData.ethicsRequiredKeywordsDetected(),
                structuredData.detectedAttachments(),
                structuredData.notes()
        );

        savedVersion.setExtractedMetadataJson(writeJson(structuredData));
        versionRepository.save(savedVersion);

        submission.setCurrentVersionNo(nextVersionNo);
        submission.setStatus(SubmissionStatus.EXTRACTION_REVIEW);
        submission.setUpdatedAt(Instant.now());
        submissionRepository.save(submission);

        auditLogService.logAction(
                actorId,
                "VERSION_UPLOADED",
                "SUBMISSION_VERSION",
                savedVersion.getId().toString(),
                null,
                "{\"version_no\":" + nextVersionNo + ",\"files_count\":" + storedFiles.size() + "}"
        );

        List<SubmissionDocument> allDocs = documentRepository.findByVersionId(savedVersion.getId());
        return mapToVersionResponse(savedVersion, allDocs);
    }

    private StructuredSubmissionDto createInitialStructuredData(Submission submission, List<AiEngineClient.DocumentFileParam> files) {
        List<DocumentRole> roles = new ArrayList<>();
        roles.add(DocumentRole.APPLICATION_FORM);
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
                roles,
                "OCR ayrıştırma tamamlandı."
        );
    }

    public SubmissionVersionResponse getSubmissionVersion(UUID submissionId, int versionNo) {
        SubmissionVersion version = versionRepository.findBySubmissionIdAndVersionNo(submissionId, versionNo)
                .orElseThrow(() -> new ResourceNotFoundException("Belirtilen versiyon bulunamadı: v" + versionNo));

        List<SubmissionDocument> docs = documentRepository.findByVersionId(version.getId());
        return mapToVersionResponse(version, docs);
    }

    @Transactional
    public SubmissionVersionResponse updateExtractedData(
            UUID submissionId,
            int versionNo,
            StructuredSubmissionDto dto,
            UUID actorId
    ) {
        SubmissionVersion version = versionRepository.findBySubmissionIdAndVersionNo(submissionId, versionNo)
                .orElseThrow(() -> new ResourceNotFoundException("Versiyon bulunamadı: v" + versionNo));

        String before = version.getExtractedMetadataJson();
        String after = writeJson(dto);
        version.setExtractedMetadataJson(after);
        SubmissionVersion updatedVersion = versionRepository.save(version);

        auditLogService.logAction(
                actorId,
                "EXTRACTED_DATA_UPDATED",
                "SUBMISSION_VERSION",
                version.getId().toString(),
                before,
                after
        );

        // Also update submission core fields if provided in confirmed dto
        submissionRepository.findById(submissionId).ifPresent(sub -> {
            if (dto.detectedSubmissionType() != null) sub.setSubmissionType(dto.detectedSubmissionType());
            if (dto.detectedProjectCode() != null && !dto.detectedProjectCode().isBlank()) sub.setProjectCode(dto.detectedProjectCode());
            if (dto.applicantName() != null && !dto.applicantName().isBlank()) sub.setApplicantName(dto.applicantName());
            if (dto.projectTitle() != null && !dto.projectTitle().isBlank()) sub.setTitle(dto.projectTitle());
            sub.setUpdatedAt(Instant.now());
            submissionRepository.save(sub);
        });

        List<SubmissionDocument> docs = documentRepository.findByVersionId(updatedVersion.getId());
        return mapToVersionResponse(updatedVersion, docs);
    }

    @Transactional
    public SubmissionDetailResponse updateSubmissionStatus(UUID submissionId, StatusTransitionRequest request, UUID actorId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Başvuru bulunamadı: " + submissionId));

        SubmissionStatus beforeStatus = submission.getStatus();
        SubmissionStatus targetStatus = request.targetStatus();

        if (beforeStatus == SubmissionStatus.REVISION_REQUESTED && targetStatus == SubmissionStatus.COMMISSION_REFERRED) {
            throw new BadRequestException(
                    "Revizyon (iade) istenmiş bir başvuru, yürütücü tarafından yeni revize versiyon (v"
                            + (submission.getCurrentVersionNo() + 1)
                            + ") yüklenip tekrar değerlendirilmeden Profesör Kuruluna (BAP Komisyonuna) sevk edilemez."
            );
        }

        submission.setStatus(targetStatus);
        submission.setUpdatedAt(Instant.now());
        Submission saved = submissionRepository.save(submission);

        // Apply finding overrides if provided in request
        if (request.findingOverrides() != null && !request.findingOverrides().isEmpty()) {
            for (FindingOverrideItem item : request.findingOverrides()) {
                findingRepository.findById(item.findingId()).ifPresent(finding -> {
                    finding.setOfficerOverrideStatus(item.overrideStatus());
                    finding.setOfficerOverrideReason(item.overrideReason());
                    findingRepository.save(finding);

                    auditLogService.logAction(
                            actorId,
                            "FINDING_OVERRIDDEN",
                            "EVALUATION_FINDING",
                            finding.getId().toString(),
                            null,
                            "{\"status\":\"" + item.overrideStatus() + "\",\"reason\":\"" + item.overrideReason() + "\"}"
                    );
                });
            }
        }

        auditLogService.logAction(
                actorId,
                "STATUS_TRANSITIONED",
                "SUBMISSION",
                submissionId.toString(),
                "{\"status\":\"" + beforeStatus + "\"}",
                "{\"status\":\"" + request.targetStatus() + "\",\"notes\":\"" + (request.officerNotes() != null ? request.officerNotes() : "") + "\"}"
        );

        SubmissionVersion latestVersion = versionRepository.findBySubmissionIdAndVersionNo(saved.getId(), saved.getCurrentVersionNo()).orElse(null);
        SubmissionVersionResponse vResponse = null;
        if (latestVersion != null) {
            List<SubmissionDocument> docs = documentRepository.findByVersionId(latestVersion.getId());
            vResponse = mapToVersionResponse(latestVersion, docs);
        }

        return mapToDetailResponse(saved, vResponse);
    }

    // --- Helper Mappers ---

    public SubmissionVersionResponse mapToVersionResponse(SubmissionVersion v, List<SubmissionDocument> docs) {
        StructuredSubmissionDto metadata = null;
        if (v.getExtractedMetadataJson() != null && !v.getExtractedMetadataJson().isBlank()) {
            try {
                metadata = objectMapper.readValue(v.getExtractedMetadataJson(), StructuredSubmissionDto.class);
            } catch (Exception e) {
                log.warn("Failed to parse extracted_metadata_json: {}", e.getMessage());
            }
        }

        EvaluationModuleFlagsDto flags = null;
        if (v.getSelectedModulesJson() != null && !v.getSelectedModulesJson().isBlank()) {
            try {
                flags = objectMapper.readValue(v.getSelectedModulesJson(), EvaluationModuleFlagsDto.class);
            } catch (Exception e) {
                flags = EvaluationModuleFlagsDto.defaultFlags();
            }
        } else {
            flags = EvaluationModuleFlagsDto.defaultFlags();
        }

        List<SubmissionDocumentDto> docDtos = docs != null
                ? docs.stream().map(SubmissionDocument::toDto).toList()
                : Collections.emptyList();

        return new SubmissionVersionResponse(
                v.getId(),
                v.getSubmissionId(),
                v.getVersionNo(),
                metadata,
                flags,
                v.getOfficerNotes(),
                v.getSubmittedAt(),
                docDtos
        );
    }

    public SubmissionDetailResponse mapToDetailResponse(Submission s, SubmissionVersionResponse latestVersion) {
        String officerName = null;
        if (s.getAssignedOfficerId() != null) {
            officerName = userRepository.findById(s.getAssignedOfficerId())
                    .map(User::getFullName)
                    .orElse(null);
        }

        return new SubmissionDetailResponse(
                s.getId(),
                s.getReferenceNo(),
                s.getExternalBap2Id(),
                s.getSubmissionType(),
                s.getProjectCode(),
                s.getTitle(),
                s.getApplicantName(),
                s.getApplicantTitle(),
                s.getDepartment(),
                s.getCurrentVersionNo(),
                s.getStatus(),
                s.getAssignedOfficerId(),
                officerName,
                s.getCreatedAt(),
                s.getUpdatedAt(),
                latestVersion
        );
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
