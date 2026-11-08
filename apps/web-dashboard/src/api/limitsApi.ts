import { apiRequest } from './client';
import { LimitConfigDto } from '../types/api';
import { MOCK_LIMITS } from './mockData';

let localLimits = [...MOCK_LIMITS];

export const limitsApi = {
  async getLimits(year = 2026): Promise<LimitConfigDto[]> {
    try {
      return await apiRequest<LimitConfigDto[]>('/limits', {
        params: { year },
      });
    } catch {
      return localLimits.filter((l) => l.fiscal_year === year);
    }
  },

  async updateLimit(
    id: string,
    data: Partial<Pick<LimitConfigDto, 'title_tr' | 'max_amount_tl' | 'max_duration_months' | 'vat_included' | 'is_active'>>
  ): Promise<LimitConfigDto> {
    try {
      return await apiRequest<LimitConfigDto>(`/limits/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      });
    } catch {
      const limit = localLimits.find((l) => l.id === id);
      if (limit) {
        Object.assign(limit, data);
        return limit;
      }
      throw new Error('Limit kaydı bulunamadı');
    }
  },

  async cloneYearLimits(sourceYear: number, targetYear: number): Promise<LimitConfigDto[]> {
    try {
      return await apiRequest<LimitConfigDto[]>('/limits/clone-year', {
        method: 'POST',
        body: JSON.stringify({ source_year: sourceYear, target_year: targetYear }),
      });
    } catch {
      const source = localLimits.filter((l) => l.fiscal_year === sourceYear);
      const cloned = source.map((l) => ({
        ...l,
        id: `lim-${targetYear}-${l.code.toLowerCase()}`,
        fiscal_year: targetYear,
        effective_from: `${targetYear}-01-01`,
      }));
      localLimits.push(...cloned);
      return cloned;
    }
  },
};
