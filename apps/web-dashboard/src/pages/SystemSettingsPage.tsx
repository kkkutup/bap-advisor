import React, { useState, useEffect } from 'react';
import { providerApi } from '../api/providerApi';
import { bap2Api } from '../api/bap2Api';
import { ProviderStatusResponse, ProviderConfigDto } from '../types/api';
import {
  Settings,
  Cpu,
  Shield,
  ShieldAlert,
  Server,
  CheckCircle,
  AlertCircle,
  Save,
  RefreshCw,
  ExternalLink,
  Code2,
  Send,
  Database,
} from 'lucide-react';

export const SystemSettingsPage: React.FC = () => {
  const [providerStatus, setProviderStatus] = useState<ProviderStatusResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState<string | null>(null);

  // Form State
  const [selectedProvider, setSelectedProvider] = useState<'nanogpt' | 'ollama' | 'vllm'>('nanogpt');
  const [modelName, setModelName] = useState('openai/gpt-4.1-mini');
  const [apiKey, setApiKey] = useState('');
  const [baseUrl, setBaseUrl] = useState('http://localhost:11434/v1');
  const [airgappedMode, setAirgappedMode] = useState(false);

  // bap2 integration testing state
  const [bap2Payload, setBap2Payload] = useState(
    JSON.stringify(
      {
        external_bap2_id: 'BAP2-TEST-2026',
        submission_type: 'NEW_PROJECT',
        project_code: 'TP2',
        title: 'bap2 Entegrasyon Test Başvurusu',
        applicant_name: 'Dr. Test Yürütücü',
        applicant_title: 'Dr. Öğr. Üyesi',
        department: 'Mühendislik Fakültesi',
        structured_data: {
          detected_submission_type: 'NEW_PROJECT',
          detected_project_code: 'TP2',
          requested_budget_total_tl: 115000,
          requested_duration_months: 18,
          wet_signature_present: true,
          ethics_required_keywords_detected: false,
          detected_attachments: ['APPLICATION_FORM'],
          budget_line_items: [
            {
              category: 'CONSUMABLE',
              name: 'Test Sarf Malzemesi',
              quantity: 1,
              unit_price_tl: 115000,
              total_tl: 115000,
            },
          ],
        },
      },
      null,
      2
    )
  );
  const [bap2Response, setBap2Response] = useState<string | null>(null);
  const [bap2Loading, setBap2Loading] = useState(false);

  const loadProviderStatus = () => {
    setLoading(true);
    providerApi
      .getProviderStatus()
      .then((res) => {
        setProviderStatus(res);
        setSelectedProvider(res.active_provider);
        setModelName(res.active_model);
        setAirgappedMode(res.airgapped_mode);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadProviderStatus();
  }, []);

  const handleProviderSelect = (prov: 'nanogpt' | 'ollama' | 'vllm') => {
    if (prov === selectedProvider) return;
    setSelectedProvider(prov);
    if (prov === 'nanogpt') {
      if (providerStatus?.active_provider === 'nanogpt' && providerStatus.active_model) {
        setModelName(providerStatus.active_model);
      } else if (!modelName || modelName.startsWith('qwen')) {
        setModelName('gpt-4o-mini');
      }
    } else if (prov === 'ollama') {
      setModelName(
        providerStatus?.active_provider === 'ollama' && providerStatus.active_model
          ? providerStatus.active_model
          : 'qwen2.5:14b-instruct'
      );
      setBaseUrl('http://host.docker.internal:11434/v1');
    } else {
      setModelName(
        providerStatus?.active_provider === 'vllm' && providerStatus.active_model
          ? providerStatus.active_model
          : 'qwen2.5:32b'
      );
      setBaseUrl('http://host.docker.internal:8000/v1');
    }
  };

  const handleSaveConfig = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setSaveSuccess(null);
    try {
      const trimmedModel = modelName.trim();
      const config: ProviderConfigDto = {
        llm_provider: selectedProvider,
        airgapped_mode: airgappedMode,
      };

      if (selectedProvider === 'nanogpt') {
        config.nanogpt_model = trimmedModel;
        if (apiKey.trim()) config.nanogpt_api_key = apiKey.trim();
      } else if (selectedProvider === 'ollama') {
        config.ollama_model = trimmedModel;
        config.ollama_base_url = baseUrl.trim();
      } else {
        config.vllm_model = trimmedModel;
        config.vllm_base_url = baseUrl.trim();
      }

      const updated = await providerApi.updateProviderConfig(config);
      setProviderStatus(updated);
      setSelectedProvider(updated.active_provider);
      setModelName(updated.active_model || trimmedModel);
      setAirgappedMode(Boolean(updated.airgapped_mode));
      setSaveSuccess(`LLM Sağlayıcı (${updated.active_provider.toUpperCase()}) ve Model (${updated.active_model || trimmedModel}) başarıyla kaydedildi.`);
    } catch (err: any) {
      alert(`Ayarlar kaydedilemedi: ${err?.message}`);
    } finally {
      setSaving(false);
    }
  };

  const handleTestBap2Import = async () => {
    setBap2Loading(true);
    setBap2Response(null);
    try {
      const parsed = JSON.parse(bap2Payload);
      const res = await bap2Api.importBap2(parsed);
      setBap2Response(JSON.stringify(res, null, 2));
    } catch (err: any) {
      setBap2Response(JSON.stringify({ error: err.message, status: 'FAILED' }, null, 2));
    } finally {
      setBap2Loading(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Page Title */}
      <div>
        <h2 className="text-xl font-bold text-slate-900 tracking-tight">
          Sistem Sağlayıcı, Güvenlik ve Airgap Yapılandırması
        </h2>
        <p className="text-xs text-slate-500 mt-1">
          NanoGPT Test Fazı &harr; %100 Kurum İçi Lokal Model (Ollama / vLLM) geçişi ve bap2 entegrasyon ayarları
        </p>
      </div>

      {saveSuccess && (
        <div className="p-3 bg-emerald-50 border border-emerald-300 rounded-lg text-xs font-semibold text-emerald-800 flex items-center gap-2">
          <CheckCircle className="w-4 h-4 text-emerald-600 shrink-0" />
          <span>{saveSuccess}</span>
        </div>
      )}

      {/* Mode Switcher Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Card 1: NanoGPT Test Mode */}
        <div
          onClick={() => handleProviderSelect('nanogpt')}
          className={`rounded-xl border p-5 cursor-pointer transition-all ${
            selectedProvider === 'nanogpt'
              ? 'border-meu-600 bg-meu-50/40 ring-2 ring-meu-500 shadow-sm'
              : 'border-slate-200 bg-white hover:border-slate-300'
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-meu-800">Faz 1: Test Modu</span>
            <Cpu className="w-5 h-5 text-meu-600" />
          </div>
          <h3 className="text-sm font-bold text-slate-900 mt-2">NanoGPT Cloud API</h3>
          <p className="text-xs text-slate-500 mt-1">
            Geliştirme ve hızlı testler için OpenAI uyumlu bulut API (api.nano-gpt.com).
          </p>
          <div className="mt-3">
            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-blue-100 text-blue-800">
              Bulut Test Modeli
            </span>
          </div>
        </div>

        {/* Card 2: Ollama Local Mode */}
        <div
          onClick={() => handleProviderSelect('ollama')}
          className={`rounded-xl border p-5 cursor-pointer transition-all ${
            selectedProvider === 'ollama'
              ? 'border-meu-600 bg-meu-50/40 ring-2 ring-meu-500 shadow-sm'
              : 'border-slate-200 bg-white hover:border-slate-300'
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-emerald-800">Faz 2: Lokal Üretim</span>
            <Server className="w-5 h-5 text-emerald-600" />
          </div>
          <h3 className="text-sm font-bold text-slate-900 mt-2">Ollama (%100 Kurum İçi)</h3>
          <p className="text-xs text-slate-500 mt-1">
            Yerel sunucu veya Docker konteynerinde Qwen 2.5 / Gemma modelleri. Sıfır dış veri sızıntısı.
          </p>
          <div className="mt-3">
            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800">
              %100 Airgapped Uyumlu
            </span>
          </div>
        </div>

        {/* Card 3: vLLM High-Throughput */}
        <div
          onClick={() => handleProviderSelect('vllm')}
          className={`rounded-xl border p-5 cursor-pointer transition-all ${
            selectedProvider === 'vllm'
              ? 'border-meu-600 bg-meu-50/40 ring-2 ring-meu-500 shadow-sm'
              : 'border-slate-200 bg-white hover:border-slate-300'
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-purple-800">Yüksek Başarım</span>
            <Database className="w-5 h-5 text-purple-600" />
          </div>
          <h3 className="text-sm font-bold text-slate-900 mt-2">vLLM GPU Kümesi</h3>
          <p className="text-xs text-slate-500 mt-1">
            Mersin Üniversitesi kurumsal GPU sunucusunda yüksek eşzamanlı vLLM servisi.
          </p>
          <div className="mt-3">
            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-purple-100 text-purple-800">
              Kurum Sunucusu
            </span>
          </div>
        </div>
      </div>

      {/* Main Settings Form */}
      <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-xs space-y-6">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-800">
            Sağlayıcı ve Bağlantı Parametreleri
          </h3>
          <span className="font-mono text-xs font-semibold text-meu-700">
            Aktif: {selectedProvider.toUpperCase()}
          </span>
        </div>

        <form onSubmit={handleSaveConfig} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Model Tanımlayıcısı (Model Name)
              </label>
              <input
                type="text"
                required
                value={modelName}
                onChange={(e) => setModelName(e.target.value)}
                placeholder="Ör. qwen2.5:14b-instruct veya openai/gpt-4.1-mini"
                className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 bg-white font-mono"
              />
            </div>

            {selectedProvider === 'nanogpt' ? (
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  NanoGPT API Anahtarı (Gizli)
                </label>
                <input
                  type="password"
                  value={apiKey}
                  onChange={(e) => setApiKey(e.target.value)}
                  placeholder="nano-..."
                  className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 bg-white font-mono"
                />
              </div>
            ) : (
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Yerel Servis Base URL
                </label>
                <input
                  type="text"
                  value={baseUrl}
                  onChange={(e) => setBaseUrl(e.target.value)}
                  placeholder="http://host.docker.internal:11434/v1"
                  className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 bg-white font-mono"
                />
              </div>
            )}
          </div>

          {/* Airgap Security Lock Toggle */}
          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 flex items-start justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <Shield className={`w-4 h-4 ${airgappedMode ? 'text-emerald-600' : 'text-slate-400'}`} />
                <h4 className="text-xs font-bold text-slate-900">
                  AIRGAPPED_MODE (Dış İnternet Çıkış Kilidi)
                </h4>
                {airgappedMode && (
                  <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800">
                    KİLİTLİ (GÜVENLİ)
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-600">
                Açık olduğunda Python ai-engine içerisinden dış internete (api.nano-gpt.com dâhil) yapılacak tüm HTTP çıkışları kod seviyesinde bloke edilir. Yalnızca yerel ağ / Docker içi Ollama ile çalışır.
              </p>
            </div>

            <label className="relative inline-flex items-center cursor-pointer shrink-0">
              <input
                type="checkbox"
                checked={airgappedMode}
                onChange={(e) => setAirgappedMode(e.target.checked)}
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-slate-300 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-600"></div>
            </label>
          </div>

          <div className="flex justify-end pt-2">
            <button
              type="submit"
              disabled={saving}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-meu-900 hover:bg-meu-800 text-white text-xs font-bold shadow-xs transition-colors cursor-pointer"
            >
              <Save className="w-4 h-4" />
              <span>{saving ? 'Kaydediliyor...' : 'Sağlayıcı Yapılandırmasını Kaydet'}</span>
            </button>
          </div>
        </form>
      </div>

      {/* bap2.mersin.edu.tr Interoperability Panel */}
      <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-xs space-y-4">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <div className="flex items-center gap-2">
            <Code2 className="w-5 h-5 text-meu-700" />
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-900">
              bap2.mersin.edu.tr Otomasyon Entegrasyon Test Paneli
            </h3>
          </div>
          <span className="text-xs font-mono text-slate-400">POST /api/v1/integrations/bap2/import</span>
        </div>

        <p className="text-xs text-slate-600">
          Mersin Üniversitesi BAP Otomasyon Sistemi (<span className="font-mono">bap2.mersin.edu.tr</span>) ileride doğrudan REST JSON paketi aktardığında kullanılacak içe aktarma arayüzü:
        </p>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div className="space-y-1.5">
            <label className="block text-xs font-semibold text-slate-700">
              Örnek bap2 JSON Yükü (Import Payload)
            </label>
            <textarea
              rows={12}
              value={bap2Payload}
              onChange={(e) => setBap2Payload(e.target.value)}
              className="w-full p-2.5 text-xs font-mono border border-slate-300 rounded-lg bg-slate-900 text-emerald-400"
            />
            <button
              type="button"
              disabled={bap2Loading}
              onClick={handleTestBap2Import}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-meu-800 hover:bg-meu-900 text-white text-xs font-bold transition-colors cursor-pointer"
            >
              <Send className="w-3.5 h-3.5" />
              <span>{bap2Loading ? 'İçe Aktarılıyor...' : 'bap2 İçe Aktarımını Test Et'}</span>
            </button>
          </div>

          <div className="space-y-1.5">
            <label className="block text-xs font-semibold text-slate-700">
              Sistem Yanıtı (Oluşturulan Submission JSON)
            </label>
            <textarea
              readOnly
              rows={12}
              value={bap2Response || 'Test çalıştırmak için sol butona tıklayınız...'}
              className="w-full p-2.5 text-xs font-mono border border-slate-300 rounded-lg bg-slate-50 text-slate-700"
            />
          </div>
        </div>
      </div>
    </div>
  );
};
