import { apiRequest } from './client';
import { RegulationDocumentDto } from '../types/api';
import { MOCK_REGULATIONS } from './mockData';

let localRegulations = [...MOCK_REGULATIONS];

export const regulationsApi = {
  async listRegulations(): Promise<RegulationDocumentDto[]> {
    try {
      return await apiRequest<RegulationDocumentDto[]>('/regulations');
    } catch {
      return localRegulations;
    }
  },

  async uploadRegulation(formData: FormData): Promise<RegulationDocumentDto> {
    try {
      return await apiRequest<RegulationDocumentDto>('/regulations/upload', {
        method: 'POST',
        body: formData,
      });
    } catch {
      const title = (formData.get('title_tr') as string) || 'Yeni Mevzuat Belgesi';
      const docType = (formData.get('doc_type') as any) || 'YONERGE';
      const newDoc: RegulationDocumentDto = {
        id: `reg-${Date.now()}`,
        slug: title.toLowerCase().replace(/[^a-z0-9]/g, '-'),
        title_tr: title,
        doc_type: docType,
        version_label: (formData.get('version_label') as string) || '2026-Güncel',
        source_url: null,
        is_active: true,
        ingested_at: new Date().toISOString(),
      };
      localRegulations.unshift(newDoc);
      return newDoc;
    }
  },

  async toggleRegulationActive(id: string, isActive: boolean): Promise<RegulationDocumentDto> {
    try {
      return await apiRequest<RegulationDocumentDto>(`/regulations/${id}/toggle`, {
        method: 'PATCH',
        body: JSON.stringify({ is_active: isActive }),
      });
    } catch {
      const doc = localRegulations.find((r) => r.id === id);
      if (doc) {
        doc.is_active = isActive;
        return doc;
      }
      throw new Error('Mevzuat dokümanı bulunamadı');
    }
  },
};
