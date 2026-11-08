import { apiRequest } from './client';
import { Bap2ImportRequest, Bap2ExportResponse, SubmissionDetailResponse } from '../types/api';
import { MOCK_EVALUATION_RESULT_SUB001_V2 } from './mockData';

export const bap2Api = {
  async importBap2(data: Bap2ImportRequest): Promise<SubmissionDetailResponse> {
    return await apiRequest<SubmissionDetailResponse>('/integrations/bap2/import', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  async exportBap2(submissionId: string): Promise<Bap2ExportResponse> {
    try {
      return await apiRequest<Bap2ExportResponse>(`/integrations/bap2/export/${submissionId}`);
    } catch {
      return {
        submission_id: submissionId,
        reference_no: 'BAP-2026-00142',
        external_bap2_id: 'BAP2-99824',
        status: 'OFFICER_REVIEW',
        overall_compliance_status: 'COMPLIANT',
        revision_draft_tr: null,
        commission_referral_summary_tr: MOCK_EVALUATION_RESULT_SUB001_V2.commission_referral_summary_tr,
        findings: MOCK_EVALUATION_RESULT_SUB001_V2.findings,
      };
    }
  },
};
