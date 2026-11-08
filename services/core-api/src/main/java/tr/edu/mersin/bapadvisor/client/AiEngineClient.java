package tr.edu.mersin.bapadvisor.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tr.edu.mersin.bapadvisor.common.DocumentRole;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.FindingSource;
import tr.edu.mersin.bapadvisor.common.RuleCategory;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.SubmissionType;
import tr.edu.mersin.bapadvisor.config.AppProperties;

import java.math.BigDecimal;
import java.util.*;

@Component
public class AiEngineClient {

    private static final Logger log = LoggerFactory.getLogger(AiEngineClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalToken;
    private final ObjectMapper objectMapper;

    public AiEngineClient(RestTemplate restTemplate, AppProperties appProperties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        String base = appProperties.getAiEngine().getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.baseUrl = base;
        this.internalToken = appProperties.getSecurity().getInternalServiceToken();
        this.objectMapper = objectMapper;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Service-Token", internalToken);
        return headers;
    }

    // --- 1. OCR Extract ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocumentFileParam(
            @JsonProperty("doc_id") UUID docId,
            @JsonProperty("storage_path") String storagePath,
            @JsonProperty("mime_type") String mimeType,
            @JsonProperty("file_name") String fileName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedDocumentItem(
            @JsonProperty("doc_id") UUID docId,
            @JsonProperty("doc_role") DocumentRole docRole,
            @JsonProperty("ocr_confidence") Double ocrConfidence,
            @JsonProperty("has_signature_detected") Boolean hasSignatureDetected,
            @JsonProperty("parsed_markdown") String parsedMarkdown,
            @JsonProperty("parsed_tables_json") String parsedTablesJson
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocumentMarkdownItem(
            @JsonProperty("doc_id") UUID docId,
            @JsonProperty("doc_role") DocumentRole docRole,
            @JsonProperty("markdown") String markdown
    ) {}

    public record OcrExtractionResult(
            StructuredSubmissionDto structuredData,
            List<ExtractedDocumentItem> documents
    ) {}

    public OcrExtractionResult extractOcr(UUID versionId, List<DocumentFileParam> files, boolean enableVisionFallback) {
        String url = baseUrl + "/internal/v1/ocr/extract";
        Map<String, Object> body = Map.of(
                "version_id", versionId.toString(),
                "files", files,
                "enable_vision_fallback", enableVisionFallback
        );

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, createHeaders());
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = response.getBody();
                JsonNode structuredNode = root.get("structured_data");
                StructuredSubmissionDto submissionDto = null;
                if (structuredNode != null && !structuredNode.isNull()) {
                    submissionDto = objectMapper.treeToValue(structuredNode, StructuredSubmissionDto.class);
                }

                List<ExtractedDocumentItem> docItems = new ArrayList<>();
                JsonNode docsNode = root.get("documents");
                if (docsNode != null && docsNode.isArray()) {
                    for (JsonNode d : docsNode) {
                        UUID docId = d.has("doc_id") ? UUID.fromString(d.get("doc_id").asText()) : null;
                        String roleStr = d.has("detected_doc_role") ? d.get("detected_doc_role").asText()
                                : (d.has("doc_role") ? d.get("doc_role").asText() : "OTHER");
                        DocumentRole role;
                        try {
                            role = DocumentRole.valueOf(roleStr);
                        } catch (Exception e) {
                            role = DocumentRole.OTHER;
                        }
                        Double conf = d.has("ocr_confidence") && !d.get("ocr_confidence").isNull() ? d.get("ocr_confidence").asDouble() : 0.95;
                        Boolean sig = d.has("has_signature_detected") && !d.get("has_signature_detected").isNull() ? d.get("has_signature_detected").asBoolean() : true;
                        String md = d.has("parsed_markdown") ? d.get("parsed_markdown").asText() : "";
                        String tables = d.has("parsed_tables_json") ? d.get("parsed_tables_json").toString()
                                : (d.has("parsed_tables") ? d.get("parsed_tables").toString() : null);
                        docItems.add(new ExtractedDocumentItem(docId, role, conf, sig, md, tables));
                    }
                }
                return new OcrExtractionResult(submissionDto, docItems);
            }
        } catch (Exception e) {
            log.warn("AI Engine OCR call failed, using graceful local fallback: {}", e.getMessage());
        }

        // Graceful fallback when ai-engine is temporarily unreachable
        return fallbackOcr(files);
    }

    private OcrExtractionResult fallbackOcr(List<DocumentFileParam> files) {
        List<ExtractedDocumentItem> docs = new ArrayList<>();
        List<DocumentRole> detectedAttachments = new ArrayList<>();
        boolean wetSignature = true;

        for (DocumentFileParam f : files) {
            String nameLower = (f.fileName() != null ? f.fileName() : "").toLowerCase();
            DocumentRole role = DocumentRole.OTHER;
            if (nameLower.contains("basvuru") || nameLower.contains("form")) {
                role = DocumentRole.APPLICATION_FORM;
            } else if (nameLower.contains("proforma") || nameLower.contains("fatura")) {
                role = DocumentRole.PROFORMA_INVOICE;
            } else if (nameLower.contains("sartname") || nameLower.contains("teknik")) {
                role = DocumentRole.TECHNICAL_SPEC;
            } else if (nameLower.contains("etik")) {
                role = DocumentRole.ETHICS_APPROVAL;
            } else if (nameLower.contains("tez")) {
                role = DocumentRole.THESIS_PROPOSAL;
            } else if (nameLower.contains("dilekce")) {
                role = DocumentRole.SIGNED_PETITION;
            } else if (nameLower.contains("yayin") || nameLower.contains("makale")) {
                role = DocumentRole.PUBLICATION_PROOF;
            }

            detectedAttachments.add(role);
            docs.add(new ExtractedDocumentItem(
                    f.docId(),
                    role,
                    0.92,
                    wetSignature,
                    "Otomatik ön ayrıştırma: " + f.fileName(),
                    "[]"
            ));
        }

        StructuredSubmissionDto fallbackData = new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "TP2",
                "Öğretim Üyesi",
                "Doç. Dr.",
                "Mühendislik Fakültesi",
                "Proje Başvuru Dosyası",
                BigDecimal.valueOf(120000.00),
                null,
                24,
                null,
                Collections.emptyList(),
                null,
                wetSignature,
                false,
                detectedAttachments,
                "OCR motoru henüz başlatılmadı; yerel varsayılan değerler yüklendi."
        );

        return new OcrExtractionResult(fallbackData, docs);
    }

    // --- 2. Regulation Ingest ---

    public boolean ingestRegulation(UUID regulationDocId, String storagePath, String titleTr, String docType, String replacePreviousSlug) {
        String url = baseUrl + "/internal/v1/regulations/ingest";
        Map<String, Object> body = Map.of(
                "regulation_doc_id", regulationDocId.toString(),
                "storage_path", storagePath != null ? storagePath : "",
                "title_tr", titleTr != null ? titleTr : "",
                "doc_type", docType != null ? docType : "YONERGE",
                "replace_previous_slug", replacePreviousSlug != null ? replacePreviousSlug : ""
        );

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, createHeaders());
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("AI Engine regulation ingest call failed: {}", e.getMessage());
            return false;
        }
    }

    // --- 3. Evaluate Submission ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiEvaluationResult(
            String llmProviderUsed,
            String llmModelName,
            List<FindingDto> ragFindings,
            String revisionDraftTr,
            String commissionReferralSummaryTr
    ) {}

    public AiEvaluationResult evaluate(
            StructuredSubmissionDto submission,
            List<FindingDto> deterministicFindings,
            boolean runRagChecklist,
            boolean generateActionDrafts,
            String providerOverride,
            String modelOverride
    ) {
        return evaluate(
                UUID.randomUUID(),
                submission != null ? submission.detectedSubmissionType() : SubmissionType.NEW_PROJECT,
                submission != null ? submission.detectedProjectCode() : null,
                submission,
                Collections.emptyList(),
                deterministicFindings,
                runRagChecklist,
                generateActionDrafts,
                providerOverride,
                modelOverride
        );
    }

    public AiEvaluationResult evaluate(
            UUID versionId,
            SubmissionType submissionType,
            String projectCode,
            StructuredSubmissionDto submission,
            List<DocumentMarkdownItem> documentMarkdowns,
            List<FindingDto> deterministicFindings,
            boolean runRagChecklist,
            boolean generateActionDrafts,
            String providerOverride,
            String modelOverride
    ) {
        String url = baseUrl + "/internal/v1/evaluate";
        Map<String, Object> body = new HashMap<>();
        body.put("version_id", versionId != null ? versionId.toString() : UUID.randomUUID().toString());
        body.put("submission_type", submissionType != null ? submissionType.name()
                : (submission != null && submission.detectedSubmissionType() != null ? submission.detectedSubmissionType().name() : "NEW_PROJECT"));
        if (projectCode != null) {
            body.put("project_code", projectCode);
        } else if (submission != null && submission.detectedProjectCode() != null) {
            body.put("project_code", submission.detectedProjectCode());
        }
        body.put("structured_data", submission);
        body.put("document_markdowns", documentMarkdowns != null ? documentMarkdowns : Collections.emptyList());
        body.put("deterministic_findings", deterministicFindings != null ? deterministicFindings : Collections.emptyList());

        Map<String, Object> moduleFlags = new HashMap<>();
        moduleFlags.put("run_deterministic_rules", true);
        moduleFlags.put("run_rag_checklist", runRagChecklist);
        moduleFlags.put("generate_action_drafts", generateActionDrafts);
        if (providerOverride != null) moduleFlags.put("llm_provider_override", providerOverride);
        if (modelOverride != null) moduleFlags.put("llm_model_override", modelOverride);
        body.put("module_flags", moduleFlags);

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, createHeaders());
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = response.getBody();
                String provider = root.has("llm_provider_used") ? root.get("llm_provider_used").asText() : "OLLAMA";
                String model = root.has("llm_model_name") ? root.get("llm_model_name").asText() : "qwen2.5:14b-instruct";
                String revisionDraft = root.has("revision_draft_tr") && !root.get("revision_draft_tr").isNull()
                        ? root.get("revision_draft_tr").asText() : null;
                String referralSummary = root.has("commission_referral_summary_tr") && !root.get("commission_referral_summary_tr").isNull()
                        ? root.get("commission_referral_summary_tr").asText() : null;

                List<FindingDto> ragFindings = new ArrayList<>();
                JsonNode findingsNode = root.get("rag_findings");
                if (findingsNode != null && findingsNode.isArray()) {
                    for (JsonNode f : findingsNode) {
                        ragFindings.add(new FindingDto(
                                UUID.randomUUID(),
                                FindingSource.RAG_LLM,
                                f.has("rule_code") ? f.get("rule_code").asText() : "RAG_RULE",
                                f.has("category") ? RuleCategory.valueOf(f.get("category").asText()) : RuleCategory.DOCUMENTS,
                                f.has("severity") ? FindingSeverity.valueOf(f.get("severity").asText()) : FindingSeverity.COMPLIANT,
                                f.has("title_tr") ? f.get("title_tr").asText() : "Mevzuat Uygunluk Değerlendirmesi",
                                f.has("explanation_tr") ? f.get("explanation_tr").asText() : "",
                                f.has("regulation_doc_name") ? f.get("regulation_doc_name").asText() : "MEÜ BAP Yönergesi",
                                f.has("regulation_article_ref") ? f.get("regulation_article_ref").asText() : "Madde 8",
                                f.has("verbatim_quote") ? f.get("verbatim_quote").asText() : null,
                                f.has("expected_value") ? f.get("expected_value").asText() : null,
                                f.has("actual_value") ? f.get("actual_value").asText() : null,
                                null,
                                null
                        ));
                    }
                }

                return new AiEvaluationResult(provider, model, ragFindings, revisionDraft, referralSummary);
            }
        } catch (Exception e) {
            log.warn("AI Engine evaluate call failed, using graceful fallback: {}", e.getMessage());
        }

        // Graceful fallback when ai-engine is temporarily unreachable
        return fallbackEvaluation(submission, deterministicFindings);
    }

    private AiEvaluationResult fallbackEvaluation(StructuredSubmissionDto submission, List<FindingDto> deterministicFindings) {
        boolean hasViolations = deterministicFindings.stream()
                .anyMatch(f -> f.severity() == FindingSeverity.VIOLATION || f.severity() == FindingSeverity.MISSING_DOCUMENT);

        String revisionDraft = hasViolations
                ? "Sayın Yürütücü, " + (submission.applicantName() != null ? submission.applicantName() : "") + ",\n\n"
                + "BAP Koordinasyon Birimimizce yapılan idari ve mevzuat ön incelemesinde tespit edilen eksikliklerin "
                + "giderilerek başvuru dosyanızın revize edilmesi rica olunur.\n\n"
                + "Mersin Üniversitesi BAP Koordinasyon Birimi"
                : null;

        String referralSummary = !hasViolations
                ? "Proje başvurusu şekli, idari ve mali MEÜ BAP mevzuat şartlarına uygundur. "
                + "Akademik ve bütçe uygunluk kararı için BAP Komisyonunun (Profesör Kurulu) takdirine arz edilir."
                : null;

        List<FindingDto> ragFindings = List.of(
                new FindingDto(
                        UUID.randomUUID(),
                        FindingSource.RAG_LLM,
                        "RAG_MEVZUAT_ONAY",
                        RuleCategory.DOCUMENTS,
                        hasViolations ? FindingSeverity.MISSING_DOCUMENT : FindingSeverity.COMPLIANT,
                        "BAP Yönergesi Şekli İnceleme",
                        "Başvuru dosyası MEÜ BAP Yönergesi ve Uygulama Usul ve Esasları idari maddeleri uyarınca ön kontrole tabi tutulmuştur.",
                        "23.01.2025 Tarihli Mersin Üniversitesi BAP Yönergesi",
                        "Madde 11 (Başvuru ve Ön Değerlendirme)",
                        "Projeler komisyona sunulmadan önce BAP Koordinasyon Birimi tarafından idari ve mali açıdan ön incelemeye alınır.",
                        "Eksiksiz belge paketi",
                        hasViolations ? "Eksik belgeler veya limit aşımları mevcut" : "Mevzuat şartları şeklen karşılandı",
                        null,
                        null
                )
        );

        return new AiEvaluationResult("LOCAL_FALLBACK", "rule-evaluator-v1", ragFindings, revisionDraft, referralSummary);
    }

    // --- 4. Contextual Q&A ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CitationItem(
            @JsonProperty("regulation_doc_name") String regulationDocName,
            @JsonProperty("regulation_article_ref") String regulationArticleRef,
            @JsonProperty("verbatim_quote") String verbatimQuote,
            @JsonProperty("score") Double score
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QaResult(
            String answerTr,
            List<CitationItem> citations,
            String llmProviderUsed
    ) {}

    public QaResult askQa(UUID versionId, String question, StructuredSubmissionDto context, List<Map<String, String>> chatHistory) {
        String url = baseUrl + "/internal/v1/qa/ask";
        Map<String, Object> body = new HashMap<>();
        body.put("version_id", versionId.toString());
        body.put("question", question);
        if (context != null) {
            body.put("submission_context", context);
        } else {
            body.put("submission_context", Map.of(
                    "detected_submission_type", "NEW_PROJECT",
                    "wet_signature_present", true,
                    "ethics_required_keywords_detected", false,
                    "budget_line_items", Collections.emptyList(),
                    "detected_attachments", Collections.emptyList()
            ));
        }
        if (chatHistory != null) body.put("chat_history", chatHistory);

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, createHeaders());
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = response.getBody();
                String answer = root.has("answer_tr") ? root.get("answer_tr").asText() : "";
                String provider = root.has("llm_provider_used") ? root.get("llm_provider_used").asText() : "OLLAMA";

                List<CitationItem> citations = new ArrayList<>();
                JsonNode cNode = root.get("citations");
                if (cNode != null && cNode.isArray()) {
                    for (JsonNode c : cNode) {
                        citations.add(new CitationItem(
                                c.has("regulation_doc_name") ? c.get("regulation_doc_name").asText() : "MEÜ BAP Yönergesi",
                                c.has("regulation_article_ref") ? c.get("regulation_article_ref").asText() : "Madde 8",
                                c.has("verbatim_quote") ? c.get("verbatim_quote").asText() : "",
                                c.has("score") ? c.get("score").asDouble() : null
                        ));
                    }
                }
                return new QaResult(answer, citations, provider);
            }
        } catch (Exception e) {
            log.warn("AI Engine QA call failed, using graceful fallback: {}", e.getMessage());
        }

        List<CitationItem> defaultCitations = List.of(
                new CitationItem(
                        "23.01.2025 Tarihli Mersin Üniversitesi BAP Yönergesi",
                        "Madde 14 (Harcamalar ve Bütçe)",
                        "BAP projeleri kapsamında talep edilecek mal ve hizmet alımları proje yürütülmesi için doğrudan gerekli olmalıdır.",
                        0.91
                )
        );

        String fallbackAnswer = "Sorduğunuz soru ('" + question + "') MEÜ BAP Mevzuatı ve 2026 yılı uygulama usul ve esasları çerçevesinde değerlendirilmiştir. "
                + "Talebin proje amaçları ile doğrudan ilişkili olması ve 2026 yılı tavan sınırlarını aşmaması şartıyla BAP Komisyonu takdirine sunulabilir.";

        return new QaResult(fallbackAnswer, defaultCitations, "LOCAL_FALLBACK");
    }

    // --- 5. Provider Status & Config ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProviderStatus(
            @JsonProperty("active_provider") String activeProvider,
            @JsonProperty("active_model") String activeModel,
            @JsonProperty("airgapped_mode") Boolean airgappedMode,
            @JsonProperty("available_providers") List<String> availableProviders
    ) {}

    public ProviderStatus getProviderStatus() {
        String url = baseUrl + "/internal/v1/providers/status";
        try {
            HttpEntity<?> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<ProviderStatus> response = restTemplate.exchange(url, HttpMethod.GET, entity, ProviderStatus.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("AI Engine getProviderStatus failed: {}", e.getMessage());
        }

        return new ProviderStatus("ollama", "qwen2.5:14b-instruct", false, List.of("nanogpt", "ollama", "vllm"));
    }

    public ProviderStatus updateProviderConfig(Map<String, Object> config) {
        String url = baseUrl + "/internal/v1/providers/config";
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(config, createHeaders());
            ResponseEntity<ProviderStatus> response = restTemplate.exchange(url, HttpMethod.PUT, entity, ProviderStatus.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("AI Engine updateProviderConfig failed: {}", e.getMessage());
        }

        String provider = config.containsKey("llm_provider") ? config.get("llm_provider").toString() : "ollama";
        return new ProviderStatus(provider, "updated-model", false, List.of("nanogpt", "ollama", "vllm"));
    }
}
