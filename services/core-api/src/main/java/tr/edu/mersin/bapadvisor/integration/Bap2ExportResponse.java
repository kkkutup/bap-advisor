package tr.edu.mersin.bapadvisor.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.SubmissionStatus;

import java.util.List;
import java.util.UUID;

public record Bap2ExportResponse(
        @JsonProperty("submission_id") UUID submissionId,
        @JsonProperty("reference_no") String referenceNo,
        @JsonProperty("external_bap2_id") String externalBap2Id,
        @JsonProperty("status") SubmissionStatus status,
        @JsonProperty("overall_compliance_status") String overallComplianceStatus,
        @JsonProperty("revision_draft_tr") String revisionDraftTr,
        @JsonProperty("commission_referral_summary_tr") String commissionReferralSummaryTr,
        @JsonProperty("findings") List<FindingDto> findings
) {}
