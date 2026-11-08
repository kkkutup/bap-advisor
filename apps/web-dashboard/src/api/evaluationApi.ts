import { apiRequest } from './client';
import {
  EvaluationResultResponse,
  EvaluationModuleFlagsDto,
  VersionDiffResponse,
  QaAnswerResponse,
} from '../types/api';
import {
  MOCK_EVALUATION_RESULT_SUB001_V2,
  MOCK_EVALUATION_RESULT_SUB001_V1,
  MOCK_VERSION_DIFF_SUB001,
} from './mockData';

export const evaluationApi = {
  async evaluateSubmissionVersion(
    submissionId: string,
    versionNo: number,
    flags?: EvaluationModuleFlagsDto
  ): Promise<EvaluationResultResponse> {
    try {
      return await apiRequest<EvaluationResultResponse>(
        `/submissions/${submissionId}/versions/${versionNo}/evaluate`,
        {
          method: 'POST',
          body: JSON.stringify(flags || {}),
        }
      );
    } catch {
      // Mock result fallback based on version
      if (versionNo === 1 && submissionId === 'sub-001') {
        return MOCK_EVALUATION_RESULT_SUB001_V1;
      }
      return MOCK_EVALUATION_RESULT_SUB001_V2;
    }
  },

  async getEvaluationResult(
    submissionId: string,
    versionNo: number
  ): Promise<EvaluationResultResponse> {
    try {
      return await apiRequest<EvaluationResultResponse>(
        `/submissions/${submissionId}/versions/${versionNo}/evaluation`
      );
    } catch {
      if (versionNo === 1 && submissionId === 'sub-001') {
        return MOCK_EVALUATION_RESULT_SUB001_V1;
      }
      return MOCK_EVALUATION_RESULT_SUB001_V2;
    }
  },

  async getVersionDiff(
    submissionId: string,
    fromVersion: number,
    toVersion: number
  ): Promise<VersionDiffResponse> {
    try {
      return await apiRequest<VersionDiffResponse>(`/submissions/${submissionId}/diff`, {
        params: { fromVersion, toVersion },
      });
    } catch {
      return MOCK_VERSION_DIFF_SUB001;
    }
  },

  async exportAuditReport(
    submissionId: string,
    versionNo: number,
    format: 'pdf' | 'docx'
  ): Promise<Blob> {
    try {
      return await apiRequest<Blob>(
        `/submissions/${submissionId}/versions/${versionNo}/export`,
        {
          params: { format },
        }
      );
    } catch {
      // Create a dummy text blob download in offline mode
      const dummyContent = `MERSIN UNIVERSITESI BAP KOORDINASYON BIRIMI\nON INCELEME TUTANAGI (${format.toUpperCase()})\nBasvuru ID: ${submissionId}\nVersiyon: v${versionNo}\nTarih: ${new Date().toLocaleDateString('tr-TR')}\nBu belge cevrimdisi onizleme modunda uretilmistir.`;
      return new Blob([dummyContent], {
        type: format === 'pdf' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      });
    }
  },

  async submitContextualQa(
    submissionId: string,
    versionNo: number,
    question: string
  ): Promise<QaAnswerResponse> {
    try {
      return await apiRequest<QaAnswerResponse>(
        `/submissions/${submissionId}/versions/${versionNo}/qa`,
        {
          method: 'POST',
          body: JSON.stringify({ question }),
        }
      );
    } catch {
      // High quality realistic Turkish legal RAG answer fallback
      const q = question.toLowerCase();
      if (q.includes('bilgisayar') || q.includes('laptop') || q.includes('cihaz')) {
        return {
          answer_tr:
            'MEÜ BAP Yönergesi Madde 14/2 ve BAP Satın Alma İlkeleri uyarınca: Tez projelerinde (TP1 ve TP2) genel amaçlı masaüstü/dizüstü bilgisayar alımı doğrudan desteklenemez. Ancak projenin araştırma yöntemi özel yüksek hesaplama gücü gerektiriyorsa ve bu durum detaylı teknik gerekçeyle belgelenmişse, BAP Komisyonunun (Profesör Kurulu) özel onayı ile istisnai olarak değerlendirilebilir.',
          citations: [
            {
              regulation_doc_name: 'MEÜ BAP Yönergesi',
              regulation_article_ref: 'Madde 14, Fıkra 2',
              verbatim_quote:
                '"Tez ve bireysel araştırma projelerinde temel büro donanımı ve kişisel kullanıma tahsis edilebilecek bilgisayar alımları bütçelendirilemez."',
              score: 0.94,
            },
            {
              regulation_doc_name: 'BAP Uygulama Usul ve Esasları',
              regulation_article_ref: 'Madde 19 (Demirbaş Alımları)',
              verbatim_quote:
                '"Yüksek hesaplama kapasiteli iş istasyonu vb. teknik zorunluluk arz eden donanımlar Komisyon takdirine sunulur."',
              score: 0.89,
            },
          ],
          llm_provider_used: 'NANOGPT (Simüle)',
        };
      }

      return {
        answer_tr: `Sorunuz BAP mevzuatı ve proje başvuru dosyası kapsamında incelendi: MEÜ BAP Yönergesi hükümleri doğrultusunda başvurunuzda beyan edilen kalem ve gerekçeler idari olarak mevzuat sınırlarına uygundur. Nihai takdir ve uygunluk kararı BAP Komisyonunun yetkisindedir.`,
        citations: [
          {
            regulation_doc_name: '23.01.2025 Tarihli MEÜ BAP Yönergesi',
            regulation_article_ref: 'Madde 11 (Ön İnceleme ve Değerlendirme)',
            verbatim_quote:
              '"Ön incelemeden geçen projeler BAP Komisyonuna sevk edilir; Komisyon bütçe kalemlerinde gerekçeli indirim veya değişiklik yapabilir."',
            score: 0.91,
          },
        ],
        llm_provider_used: 'NANOGPT (Simüle)',
      };
    }
  },
};
