import { apiRequest } from './client';
import { AnalyticsOverviewResponse } from '../types/api';
import { MOCK_ANALYTICS } from './mockData';

export const analyticsApi = {
  async getAnalyticsOverview(): Promise<AnalyticsOverviewResponse> {
    try {
      return await apiRequest<AnalyticsOverviewResponse>('/analytics/overview');
    } catch {
      return MOCK_ANALYTICS;
    }
  },
};
