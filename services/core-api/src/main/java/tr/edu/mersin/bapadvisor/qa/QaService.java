package tr.edu.mersin.bapadvisor.qa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.mersin.bapadvisor.client.AiEngineClient;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.exception.ResourceNotFoundException;
import tr.edu.mersin.bapadvisor.submission.Submission;
import tr.edu.mersin.bapadvisor.submission.SubmissionRepository;
import tr.edu.mersin.bapadvisor.submission.SubmissionVersion;
import tr.edu.mersin.bapadvisor.submission.SubmissionVersionRepository;

import java.util.*;

@Service
public class QaService {

    private final QaMessageRepository qaMessageRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;
    private final AiEngineClient aiEngineClient;
    private final ObjectMapper objectMapper;

    public QaService(
            QaMessageRepository qaMessageRepository,
            SubmissionRepository submissionRepository,
            SubmissionVersionRepository versionRepository,
            AiEngineClient aiEngineClient,
            ObjectMapper objectMapper
    ) {
        this.qaMessageRepository = qaMessageRepository;
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
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

        List<QaMessage> previousMessages = qaMessageRepository.findByVersionIdOrderByCreatedAtAsc(version.getId());
        List<Map<String, String>> chatHistory = new ArrayList<>();
        for (QaMessage m : previousMessages) {
            chatHistory.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }

        // Save user question
        QaMessage userMsg = new QaMessage(version.getId(), userId, "USER", question, null, null);
        qaMessageRepository.save(userMsg);

        // Call AI Engine
        AiEngineClient.QaResult qaResult = aiEngineClient.askQa(version.getId(), question, context, chatHistory);

        String citationsJson = writeJson(qaResult.citations());

        // Save assistant response
        QaMessage assistantMsg = new QaMessage(
                version.getId(),
                null,
                "ASSISTANT",
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
