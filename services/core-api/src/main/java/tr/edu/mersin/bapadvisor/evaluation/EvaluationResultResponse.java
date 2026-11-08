package tr.edu.mersin.bapadvisor.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import tr.edu.mersin.bapadvisor.common.FindingDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EvaluationResultResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("version_id") UUID versionId,
        @JsonProperty("llm_provider_used") String llmProviderUsed,
        @JsonProperty("llm_model_name") String llmModelName,
        @JsonProperty("overall_compliance_status") String overallComplianceStatus,
        @JsonProperty("revision_draft_tr") String revisionDraftTr,
        @JsonProperty("commission_referral_summary_tr") String commissionReferralSummaryTr,
        @JsonProperty("findings") List<FindingDto> findings,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("completed_at") Instant completedAt
) {}
