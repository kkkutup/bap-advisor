import { apiRequest } from './client';
import { ProviderStatusResponse, ProviderConfigDto } from '../types/api';
import { MOCK_PROVIDER_STATUS } from './mockData';

let localProviderStatus: ProviderStatusResponse = { ...MOCK_PROVIDER_STATUS };

export const providerApi = {
  async getProviderStatus(): Promise<ProviderStatusResponse> {
    try {
      return await apiRequest<ProviderStatusResponse>('/admin/providers/status');
    } catch {
      return localProviderStatus;
    }
  },

  async updateProviderConfig(config: ProviderConfigDto): Promise<{ success: boolean }> {
    try {
      await apiRequest('/admin/providers/config', {
        method: 'PUT',
        body: JSON.stringify(config),
      });
      return { success: true };
    } catch {
      localProviderStatus.active_provider = config.llm_provider;
      if (config.llm_provider === 'nanogpt') {
        localProviderStatus.active_model = config.nanogpt_model || 'openai/gpt-4.1-mini';
      } else if (config.llm_provider === 'ollama') {
        localProviderStatus.active_model = config.ollama_model || 'qwen2.5:14b-instruct';
      } else {
        localProviderStatus.active_model = config.vllm_model || 'qwen2.5:32b';
      }
      if (config.airgapped_mode !== undefined && config.airgapped_mode !== null) {
        localProviderStatus.airgapped_mode = config.airgapped_mode;
      }
      return { success: true };
    }
  },
};
