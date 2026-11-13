import { apiRequest, isForceOffline } from './client';
import {
  SubmissionDetailResponse,
  SubmissionVersionResponse,
  PageSubmissionResponse,
  SubmissionStatus,
  SubmissionType,
  StructuredSubmissionDto,
  StatusTransitionRequest,
} from '../types/api';
import { MOCK_SUBMISSIONS } from './mockData';

// Mutable mock submissions store for offline/demo persistence in current session
let localSubmissions = [...MOCK_SUBMISSIONS];

export interface SubmissionsFilterParams {
  status?: SubmissionStatus;
  submissionType?: SubmissionType;
  projectCode?: string;
  assignedOfficerId?: string;
  search?: string;
  page?: number;
  size?: number;
}

export const submissionsApi = {
  async listSubmissions(params: SubmissionsFilterParams = {}): Promise<PageSubmissionResponse> {
    try {
      return await apiRequest<PageSubmissionResponse>('/submissions', {
        params: params as Record<string, any>,
      });
    } catch (err) {
      if (!isForceOffline()) throw err;
      // Mock filtering fallback
      let filtered = [...localSubmissions];

      if (params.status) {
        filtered = filtered.filter((s) => s.status === params.status);
      }
      if (params.submissionType) {
        filtered = filtered.filter((s) => s.submission_type === params.submissionType);
      }
      if (params.projectCode) {
        filtered = filtered.filter((s) => s.project_code === params.projectCode);
      }
      if (params.search) {
        const q = params.search.toLowerCase();
        filtered = filtered.filter(
          (s) =>
            s.reference_no.toLowerCase().includes(q) ||
            s.title.toLowerCase().includes(q) ||
            s.applicant_name.toLowerCase().includes(q)
        );
      }

      const page = params.page || 0;
      const size = params.size || 15;
      const start = page * size;
      const paginated = filtered.slice(start, start + size);

      return {
        content: paginated,
        total_elements: filtered.length,
        total_pages: Math.ceil(filtered.length / size) || 1,
        number: page,
        size,
      };
    }
  },

  async getSubmissionById(id: string): Promise<SubmissionDetailResponse> {
    try {
      return await apiRequest<SubmissionDetailResponse>(`/submissions/${id}`);
    } catch (err) {
      if (!isForceOffline()) throw err;
      const found = localSubmissions.find((s) => s.id === id);
      if (!found) {
        throw new Error(`Başvuru bulunamadı: ${id}`);
      }
      return found;
    }
  },

  async createSubmission(data: {
    submission_type: SubmissionType;
    project_code?: string | null;
    title: string;
    applicant_name: string;
    applicant_title?: string | null;
    department?: string | null;
    external_bap2_id?: string | null;
  }): Promise<SubmissionDetailResponse> {
    try {
      return await apiRequest<SubmissionDetailResponse>('/submissions', {
        method: 'POST',
        body: JSON.stringify(data),
      });
    } catch (err) {
      if (!isForceOffline()) throw err;
      const newRef = `BAP-2026-${String(Math.floor(Math.random() * 90000) + 10000)}`;
      const newSub: SubmissionDetailResponse = {
        id: `sub-${Date.now()}`,
        reference_no: newRef,
        external_bap2_id: data.external_bap2_id || null,
        submission_type: data.submission_type,
        project_code: data.project_code || null,
        title: data.title,
        applicant_name: data.applicant_name,
        applicant_title: data.applicant_title || 'Dr. Öğr. Üyesi',
        department: data.department || 'BAP Birimi',
        current_version_no: 1,
        status: 'EXTRACTION_REVIEW',
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
        latest_version: {
          id: `ver-${Date.now()}-v1`,
          submission_id: `sub-${Date.now()}`,
          version_no: 1,
          submitted_at: new Date().toISOString(),
          officer_notes: 'İlk başvuru belgesi yüklendi.',
          documents: [
            {
              id: `doc-${Date.now()}`,
              file_name: 'BAP_Basvuru_Formu_Dijital.pdf',
              mime_type: 'application/pdf',
              file_size_bytes: 1450200,
              doc_role: 'APPLICATION_FORM',
              ocr_confidence: 0.99,
              has_signature_detected: true,
              storage_path: `submissions/sub-${Date.now()}/v1/BAP_Basvuru_Formu.pdf`,
            },
          ],
          extracted_metadata: {
            detected_submission_type: data.submission_type,
            detected_project_code: data.project_code,
            applicant_name: data.applicant_name,
            applicant_title: data.applicant_title,
            department: data.department,
            project_title: data.title,
            requested_budget_total_tl: 118000.0,
            existing_approved_budget_tl: null,
            requested_duration_months: 18,
            existing_duration_months: null,
            wet_signature_present: true,
            ethics_required_keywords_detected: false,
            detected_attachments: ['APPLICATION_FORM', 'PROFORMA_INVOICE'],
            budget_line_items: [
              {
                category: 'CONSUMABLE',
                name: 'Laboratuvar Analiz Reaktifleri',
                quantity: 2,
                unit: 'Kutu',
                unit_price_tl: 45000.0,
                total_tl: 90000.0,
                vat_included: true,
                proforma_count: 3,
                has_technical_spec: true,
              },
              {
                category: 'TRAVEL',
                name: 'Saha Çalışması Yolluk ve Gündelik (H-Cetveli Uyumlu)',
                days: 20,
                daily_rate_tl: 1400.0,
                total_tl: 28000.0,
                vat_included: true,
              },
            ],
          },
        },
      };

      localSubmissions.unshift(newSub);
      return newSub;
    }
  },

  async createSubmissionVersion(
    submissionId: string,
    formData: FormData
  ): Promise<SubmissionVersionResponse> {
    try {
      return await apiRequest<SubmissionVersionResponse>(`/submissions/${submissionId}/versions`, {
        method: 'POST',
        body: formData,
      });
    } catch (err) {
      if (!isForceOffline()) throw err;
      // Mock version creation
      const sub = localSubmissions.find((s) => s.id === submissionId);
      const nextVersionNo = (sub?.current_version_no || 1) + 1;
      const newVersion: SubmissionVersionResponse = {
        id: `ver-${submissionId}-v${nextVersionNo}`,
        submission_id: submissionId,
        version_no: nextVersionNo,
        submitted_at: new Date().toISOString(),
        officer_notes: 'Yeni revizyon yüklendi.',
        documents: [
          {
            id: `doc-${Date.now()}`,
            file_name: 'Revize_Basvuru_Paketi.pdf',
            mime_type: 'application/pdf',
            file_size_bytes: 2100000,
            doc_role: 'APPLICATION_FORM',
            ocr_confidence: 0.98,
            has_signature_detected: true,
            storage_path: `submissions/${submissionId}/v${nextVersionNo}/doc.pdf`,
          },
        ],
        extracted_metadata: sub?.latest_version?.extracted_metadata,
      };

      if (sub) {
        sub.current_version_no = nextVersionNo;
        sub.latest_version = newVersion;
        sub.status = 'EXTRACTION_REVIEW';
      }

      return newVersion;
    }
  },

  async getSubmissionVersion(
    submissionId: string,
    versionNo: number
  ): Promise<SubmissionVersionResponse> {
    try {
      return await apiRequest<SubmissionVersionResponse>(
        `/submissions/${submissionId}/versions/${versionNo}`
      );
    } catch (err) {
      if (!isForceOffline()) throw err;
      const sub = localSubmissions.find((s) => s.id === submissionId);
      if (sub?.latest_version && sub.latest_version.version_no === versionNo) {
        return sub.latest_version;
      }
      // If requesting v1 for sub-001
      if (submissionId === 'sub-001' && versionNo === 1) {
        return {
          id: 'ver-001-v1',
          submission_id: 'sub-001',
          version_no: 1,
          submitted_at: '2026-02-14T09:12:00Z',
          officer_notes: 'İlk başvuru versiyonu.',
          documents: [
            {
              id: 'doc-001-v1',
              file_name: 'TP2_Proje_Basvuru_Formu_Ilk.pdf',
              mime_type: 'application/pdf',
              file_size_bytes: 2300000,
              doc_role: 'APPLICATION_FORM',
              ocr_confidence: 0.97,
              has_signature_detected: true,
              storage_path: 'submissions/sub-001/v1/TP2_Form.pdf',
            },
          ],
          extracted_metadata: {
            detected_submission_type: 'NEW_PROJECT',
            detected_project_code: 'TP2',
            applicant_name: 'Doç. Dr. Selim Aktaş',
            applicant_title: 'Doç. Dr.',
            department: 'Su Ürünleri Fakültesi',
            project_title: 'Akdeniz Kıyılarında Mikroplastik Kirliliği Analizi',
            requested_budget_total_tl: 132500.0, // LIMIT OVERAGE IN V1
            existing_approved_budget_tl: null,
            requested_duration_months: 18,
            existing_duration_months: null,
            wet_signature_present: true,
            ethics_required_keywords_detected: false,
            detected_attachments: ['APPLICATION_FORM'],
            budget_line_items: [
              {
                category: 'CONSUMABLE',
                name: 'FTIR Spektrometre Uyumlu Kimyasal Filtre ve Reaktifler',
                quantity: 3,
                unit: 'Paket',
                unit_price_tl: 30833.33,
                total_tl: 92500.0,
                vat_included: true,
                proforma_count: 1, // Only 1 proforma in v1
              },
              {
                category: 'TRAVEL',
                name: 'Mersin Körfezi Arazi Örnekleme Yolluk ve Ulaşım',
                days: 10,
                daily_rate_tl: 1200.0,
                total_tl: 12000.0,
                vat_included: true,
              },
              {
                category: 'SERVICE_PURCHASE',
                name: 'Akredite Laboratuvar Taramalı Elektron Mikroskobu (SEM) Analizi',
                quantity: 1,
                unit: 'Adet',
                unit_price_tl: 28000.0,
                total_tl: 28000.0,
                vat_included: true,
              },
            ],
          },
        };
      }

      return (
        sub?.latest_version || {
          id: `ver-${submissionId}-${versionNo}`,
          submission_id: submissionId,
          version_no: versionNo,
          submitted_at: new Date().toISOString(),
          documents: [],
        }
      );
    }
  },

  async updateExtractedData(
    submissionId: string,
    versionNo: number,
    data: StructuredSubmissionDto
  ): Promise<SubmissionVersionResponse> {
    try {
      return await apiRequest<SubmissionVersionResponse>(
        `/submissions/${submissionId}/versions/${versionNo}/extracted-data`,
        {
          method: 'PATCH',
          body: JSON.stringify(data),
        }
      );
    } catch (err) {
      if (!isForceOffline()) throw err;
      const sub = localSubmissions.find((s) => s.id === submissionId);
      if (sub && sub.latest_version) {
        sub.latest_version.extracted_metadata = data;
        sub.status = 'OFFICER_REVIEW';
        return sub.latest_version;
      }
      throw new Error('Başvuru versiyonu bulunamadı.');
    }
  },

  async updateSubmissionStatus(
    submissionId: string,
    payload: StatusTransitionRequest
  ): Promise<SubmissionDetailResponse> {
    try {
      return await apiRequest<SubmissionDetailResponse>(`/submissions/${submissionId}/status`, {
        method: 'PATCH',
        body: JSON.stringify(payload),
      });
    } catch (err) {
      if (!isForceOffline()) throw err;
      const sub = localSubmissions.find((s) => s.id === submissionId);
      if (sub) {
        sub.status = payload.target_status;
        sub.updated_at = new Date().toISOString();
        return sub;
      }
      throw new Error('Başvuru bulunamadı.');
    }
  },
};
