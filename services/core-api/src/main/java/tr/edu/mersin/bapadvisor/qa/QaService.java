package tr.edu.mersin.bapadvisor.qa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.mersin.bapadvisor.client.AiEngineClient;
import tr.edu.mersin.bapadvisor.common.DocumentRole;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.document.SubmissionDocument;
import tr.edu.mersin.bapadvisor.document.SubmissionDocumentRepository;
import tr.edu.mersin.bapadvisor.exception.ResourceNotFoundException;
import tr.edu.mersin.bapadvisor.submission.Submission;
import tr.edu.mersin.bapadvisor.submission.SubmissionRepository;
import tr.edu.mersin.bapadvisor.submission.SubmissionVersion;
import tr.edu.mersin.bapadvisor.submission.SubmissionVersionRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class QaService {

    private final QaMessageRepository qaMessageRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;
    private final SubmissionDocumentRepository documentRepository;
    private final AiEngineClient aiEngineClient;
    private final ObjectMapper objectMapper;

    public QaService(
            QaMessageRepository qaMessageRepository,
            SubmissionRepository submissionRepository,
            SubmissionVersionRepository versionRepository,
            SubmissionDocumentRepository documentRepository,
            AiEngineClient aiEngineClient,
            ObjectMapper objectMapper
    ) {
        this.qaMessageRepository = qaMessageRepository;
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
        this.documentRepository = documentRepository;
        this.aiEngineClient = aiEngineClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QaAnswerResponse askQuestion(UUID submissionId, int versionNo, String question, UUID userId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Başvuru bulunamadı: " + submissionId));

        SubmissionVersion version = versionRepository.findBySubmissionIdAndVersionNo(submissionId, versionNo)
                .orElseThrow(() -> new ResourceNotFoundException("Versiyon bulunamadı: v" + versionNo));

        StructuredSubmissionDto context = null;
        if (version.getExtractedMetadataJson() != null && !version.getExtractedMetadataJson().isBlank()) {
            try {
                context = objectMapper.readValue(version.getExtractedMetadataJson(), StructuredSubmissionDto.class);
            } catch (Exception ignored) {}
        }

        // Enrich context with uploaded document markdown excerpt so Q&A can answer file-specific questions
        List<SubmissionDocument> docs = documentRepository != null
                ? documentRepository.findByVersionId(version.getId())
                : Collections.emptyList();
        String docsMarkdownSnippet = docs.stream()
                .map(SubmissionDocument::getParsedMarkdown)
                .filter(md -> md != null && !md.isBlank())
                .collect(Collectors.joining("\n\n"));
        if (docsMarkdownSnippet.length() > 6000) {
            docsMarkdownSnippet = docsMarkdownSnippet.substring(0, 6000);
        }

        if (context == null) {
            context = new StructuredSubmissionDto(
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
                    docsMarkdownSnippet.isBlank() ? null : docsMarkdownSnippet
            );
        } else if (!docsMarkdownSnippet.isBlank()) {
            String combinedNotes = (context.notes() != null ? context.notes() + "\n\n" : "")
                    + "[DOSYA İÇERİĞİ ÖZETİ]:\n" + docsMarkdownSnippet;
            context = new StructuredSubmissionDto(
                    context.detectedSubmissionType() != null ? context.detectedSubmissionType() : submission.getSubmissionType(),
                    context.detectedProjectCode() != null ? context.detectedProjectCode() : submission.getProjectCode(),
                    context.applicantName() != null ? context.applicantName() : submission.getApplicantName(),
                    context.applicantTitle() != null ? context.applicantTitle() : submission.getApplicantTitle(),
                    context.department() != null ? context.department() : submission.getDepartment(),
                    context.projectTitle() != null ? context.projectTitle() : submission.getTitle(),
                    context.requestedBudgetTotalTl(),
                    context.existingApprovedBudgetTl(),
                    context.requestedDurationMonths(),
                    context.existingDurationMonths(),
                    context.budgetLineItems(),
                    context.journalQuartile(),
                    context.wetSignaturePresent(),
                    context.ethicsRequiredKeywordsDetected(),
                    context.detectedAttachments(),
                    combinedNotes
            );
        }

        List<QaMessage> previousMessages = qaMessageRepository.findByVersionIdOrderByCreatedAtAsc(version.getId());
        List<Map<String, String>> chatHistory = new ArrayList<>();
        for (QaMessage m : previousMessages) {
            String normalizedRole = m.getRole() != null ? m.getRole().toLowerCase(Locale.ROOT) : "user";
            if (!normalizedRole.equals("user") && !normalizedRole.equals("assistant") && !normalizedRole.equals("system")) {
                normalizedRole = "user";
            }
            chatHistory.add(Map.of("role", normalizedRole, "content", m.getContent() != null ? m.getContent() : ""));
        }

        // Save user question
        QaMessage userMsg = new QaMessage(version.getId(), userId, "user", question, null, null);
        qaMessageRepository.save(userMsg);

        // Call AI Engine
        AiEngineClient.QaResult qaResult = aiEngineClient.askQa(version.getId(), question, context, chatHistory);

        String citationsJson = writeJson(qaResult.citations());

        // Save assistant response
        QaMessage assistantMsg = new QaMessage(
                version.getId(),
                null,
                "assistant",
                qaResult.answerTr(),
                citationsJson,
                qaResult.llmProviderUsed()
        );
        qaMessageRepository.save(assistantMsg);

        return new QaAnswerResponse(qaResult.answerTr(), qaResult.citations(), qaResult.llmProviderUsed());
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
