import { apiRequest, isForceOffline } from './client';
import { ProviderStatusResponse, ProviderConfigDto } from '../types/api';
import { MOCK_PROVIDER_STATUS } from './mockData';

let localProviderStatus: ProviderStatusResponse = { ...MOCK_PROVIDER_STATUS };

export const providerApi = {
  async getProviderStatus(): Promise<ProviderStatusResponse> {
    try {
      const res = await apiRequest<ProviderStatusResponse>('/admin/providers/status');
      localProviderStatus = { ...localProviderStatus, ...res };
      return localProviderStatus;
    } catch (err) {
      if (!isForceOffline()) throw err;
      return localProviderStatus;
    }
  },

  async updateProviderConfig(config: ProviderConfigDto): Promise<ProviderStatusResponse> {
    try {
      const res = await apiRequest<ProviderStatusResponse>('/admin/providers/config', {
        method: 'PUT',
        body: JSON.stringify(config),
      });
      localProviderStatus = { ...localProviderStatus, ...res };
      return localProviderStatus;
    } catch (err) {
      if (!isForceOffline()) throw err;
      localProviderStatus.active_provider = config.llm_provider;
      if (config.llm_provider === 'nanogpt') {
        localProviderStatus.active_model = config.nanogpt_model || localProviderStatus.active_model || 'gpt-4o-mini';
      } else if (config.llm_provider === 'ollama') {
        localProviderStatus.active_model = config.ollama_model || 'qwen2.5:14b-instruct';
      } else {
        localProviderStatus.active_model = config.vllm_model || 'qwen2.5:32b';
      }
      if (config.airgapped_mode !== undefined && config.airgapped_mode !== null) {
        localProviderStatus.airgapped_mode = config.airgapped_mode;
      }
      return localProviderStatus;
    }
  },
};
