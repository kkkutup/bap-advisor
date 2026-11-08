import React, { useState, useEffect } from 'react';
import { regulationsApi } from '../api/regulationsApi';
import { limitsApi } from '../api/limitsApi';
import { RegulationDocumentDto, LimitConfigDto } from '../types/api';
import {
  Scale,
  FileText,
  UploadCloud,
  CheckCircle,
  AlertCircle,
  Copy,
  Plus,
  RefreshCw,
  ExternalLink,
  Edit2,
  Check,
  Save,
  Calendar,
} from 'lucide-react';

export const RegulationsAdminPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'regulations' | 'limits'>('regulations');

  // Tab 1: Regulations state
  const [regulations, setRegulations] = useState<RegulationDocumentDto[]>([]);
  const [regLoading, setRegLoading] = useState(true);
  const [uploadTitle, setUploadTitle] = useState('');
  const [uploadDocType, setUploadDocType] = useState<string>('YONERGE');
  const [uploadVersionLabel, setUploadVersionLabel] = useState('2026-Revize');
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadSuccess, setUploadSuccess] = useState<string | null>(null);

  // Tab 2: Limits state
  const [limits, setLimits] = useState<LimitConfigDto[]>([]);
  const [limitsLoading, setLimitsLoading] = useState(true);
  const [selectedYear, setSelectedYear] = useState(2026);
  const [editingLimitId, setEditingLimitId] = useState<string | null>(null);
  const [editAmount, setEditAmount] = useState<number>(0);
  const [editDuration, setEditDuration] = useState<number>(0);
  const [cloneLoading, setCloneLoading] = useState(false);
  const [limitActionMsg, setLimitActionMsg] = useState<string | null>(null);

  // Fetch regulations
  const loadRegulations = () => {
    setRegLoading(true);
    regulationsApi
      .listRegulations()
      .then((res) => setRegulations(res))
      .finally(() => setRegLoading(false));
  };

  // Fetch limits
  const loadLimits = (year: number) => {
    setLimitsLoading(true);
    limitsApi
      .getLimits(year)
      .then((res) => setLimits(res))
      .finally(() => setLimitsLoading(false));
  };

  useEffect(() => {
    loadRegulations();
    loadLimits(selectedYear);
  }, [selectedYear]);

  const handleToggleRegulation = async (id: string, currentState: boolean) => {
    try {
      await regulationsApi.toggleRegulationActive(id, !currentState);
      loadRegulations();
    } catch (err: any) {
      alert(`Durum değiştirilemedi: ${err?.message}`);
    }
  };

  const handleUploadRegulation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!uploadTitle.trim() || !uploadFile) return;

    setUploading(true);
    setUploadSuccess(null);
    try {
      const formData = new FormData();
      formData.append('file', uploadFile);
      formData.append('title_tr', uploadTitle);
      formData.append('doc_type', uploadDocType);
      formData.append('version_label', uploadVersionLabel);

      await regulationsApi.uploadRegulation(formData);
      setUploadSuccess(`"${uploadTitle}" başarıyla yüklendi ve RAG indeksine aktarıldı.`);
      setUploadTitle('');
      setUploadFile(null);
      loadRegulations();
    } catch (err: any) {
      alert(`Mevzuat yükleme hatası: ${err?.message}`);
    } finally {
      setUploading(false);
    }
  };

  const handleStartEditLimit = (limit: LimitConfigDto) => {
    setEditingLimitId(limit.id);
    setEditAmount(limit.max_amount_tl);
    setEditDuration(limit.max_duration_months || 0);
  };

  const handleSaveLimit = async (id: string) => {
    try {
      await limitsApi.updateLimit(id, {
        max_amount_tl: editAmount,
        max_duration_months: editDuration || null,
      });
      setLimitActionMsg('Limit başarıyla güncellendi.');
      setEditingLimitId(null);
      loadLimits(selectedYear);
      setTimeout(() => setLimitActionMsg(null), 3000);
    } catch (err: any) {
      alert(`Limit güncellenemedi: ${err?.message}`);
    }
  };

  const handleCloneToNextYear = async () => {
    const targetYear = selectedYear + 1;
    if (!window.confirm(`${selectedYear} yılı limitlerini ${targetYear} mali yılına kopyalamak istiyor musunuz?`)) {
      return;
    }
    setCloneLoading(true);
    try {
      await limitsApi.cloneYearLimits(selectedYear, targetYear);
      setSelectedYear(targetYear);
      setLimitActionMsg(`${targetYear} yılı limitleri başarıyla oluşturuldu ve yüklendi.`);
      setTimeout(() => setLimitActionMsg(null), 3500);
    } catch (err: any) {
      alert(`Limit kopyalama hatası: ${err?.message}`);
    } finally {
      setCloneLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div>
        <h2 className="text-xl font-bold text-slate-900 tracking-tight">
          Koordinatör Mevzuat & Yıllık Limit Yönetim Paneli
        </h2>
        <p className="text-xs text-slate-500 mt-1">
          MEÜ BAP Yönergesi, Uygulama Usul ve Esasları ve 2026 Destek Tavanlarının Dinamik Yönetimi
        </p>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-slate-200">
        <button
          onClick={() => setActiveTab('regulations')}
          className={`flex items-center gap-2 px-5 py-2.5 text-xs font-bold border-b-2 transition-colors ${
            activeTab === 'regulations'
              ? 'border-meu-800 text-meu-900'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
        >
          <FileText className="w-4 h-4" />
          <span>Mevzuat Bilgi Tabanı ve PDF Yükleyici ({regulations.length})</span>
        </button>

        <button
          onClick={() => setActiveTab('limits')}
          className={`flex items-center gap-2 px-5 py-2.5 text-xs font-bold border-b-2 transition-colors ${
            activeTab === 'limits'
              ? 'border-meu-800 text-meu-900'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
        >
          <Scale className="w-4 h-4" />
          <span>{selectedYear} Yılı Bütçe & Açık Erişim Limitleri Editörü</span>
        </button>
      </div>

      {/* TAB 1: REGULATIONS KNOWLEDGE BASE */}
      {activeTab === 'regulations' && (
        <div className="space-y-6">
          {/* Prominent Upload Box for Coordinator */}
          <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-xs space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <UploadCloud className="w-5 h-5 text-meu-700" />
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-900">
                  Yeni Mevzuat Belgesi / PDF Yükle & İndeksle
                </h3>
              </div>
              <span className="text-xs text-slate-400">
                Lokal bge-m3 vektörleme ve madde-parçalama anında uygulanır
              </span>
            </div>

            {uploadSuccess && (
              <div className="p-3 bg-emerald-50 border border-emerald-300 rounded-lg text-xs font-medium text-emerald-800 flex items-center gap-2">
                <CheckCircle className="w-4 h-4 text-emerald-600 shrink-0" />
                <span>{uploadSuccess}</span>
              </div>
            )}

            <form onSubmit={handleUploadRegulation} className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Mevzuat Belge Başlığı <span className="text-rose-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={uploadTitle}
                  onChange={(e) => setUploadTitle(e.target.value)}
                  placeholder="Ör. 2026 MEÜ BAP Uygulama Usul ve Esasları"
                  className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 bg-white"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Doküman Türü <span className="text-rose-500">*</span>
                </label>
                <select
                  value={uploadDocType}
                  onChange={(e) => setUploadDocType(e.target.value)}
                  className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 bg-white"
                >
                  <option value="YONERGE">Yönerge (Temel Senato Kararı)</option>
                  <option value="ESASLAR">Uygulama Usul ve Esasları</option>
                  <option value="KILAVUZ">Başvuru ve Değerlendirme Kılavuzu</option>
                  <option value="YONETMELIK">YÖK Yönetmeliği</option>
                  <option value="CETVEL">Bütçe / Harcırah H-Cetveli</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Sürüm / Tarih Etiketi
                </label>
                <input
                  type="text"
                  value={uploadVersionLabel}
                  onChange={(e) => setUploadVersionLabel(e.target.value)}
                  placeholder="Ör. 18.02.2025 (Değişiklik: 2026)"
                  className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 bg-white"
                />
              </div>

              <div className="sm:col-span-2">
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Mevzuat PDF Dosyası <span className="text-rose-500">*</span>
                </label>
                <input
                  type="file"
                  required
                  accept=".pdf,.md,.txt"
                  onChange={(e) => setUploadFile(e.target.files?.[0] || null)}
                  className="w-full py-1.5 px-3 text-xs border border-slate-300 rounded-lg bg-white file:mr-3 file:py-1 file:px-2 file:rounded file:border-0 file:text-xs file:font-semibold file:bg-meu-100 file:text-meu-800"
                />
              </div>

              <div className="flex items-end">
                <button
                  type="submit"
                  disabled={uploading || !uploadFile || !uploadTitle.trim()}
                  className="w-full py-2 px-4 rounded-lg bg-meu-900 hover:bg-meu-800 disabled:opacity-40 text-white text-xs font-bold shadow-xs transition-colors cursor-pointer"
                >
                  {uploading ? 'İndeksleniyor...' : 'Belgeyi Sisteme Yükle ve İndeksle'}
                </button>
              </div>
            </form>
          </div>

          {/* Regulations Knowledge Base Table */}
          <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-800">
                Aktif ve İndekslenmiş MEÜ Mevzuat Kaynakları ({regulations.length})
              </h3>
              <button
                onClick={loadRegulations}
                className="inline-flex items-center gap-1 text-xs text-meu-600 hover:underline font-semibold"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                <span>Yenile</span>
              </button>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-200 text-slate-500 font-semibold text-[11px] bg-slate-50/70">
                    <th className="py-2.5 px-3">TÜR</th>
                    <th className="py-2.5 px-3">MEVZUAT BAŞLIĞI</th>
                    <th className="py-2.5 px-3">SÜRÜM ETİKETİ</th>
                    <th className="py-2.5 px-3">İNDEKSLEME ZAMANI</th>
                    <th className="py-2.5 px-3 text-center">DURUM</th>
                    <th className="py-2.5 px-3 text-right">İŞLEMLER</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {regulations.map((reg) => (
                    <tr key={reg.id} className="hover:bg-slate-50">
                      <td className="py-3 px-3">
                        <span className="px-2 py-0.5 rounded font-mono text-[10px] font-bold bg-slate-100 text-slate-700">
                          {reg.doc_type}
                        </span>
                      </td>
                      <td className="py-3 px-3">
                        <div className="font-semibold text-slate-900">{reg.title_tr}</div>
                        <span className="text-[10px] text-slate-400 font-mono">{reg.slug}</span>
                      </td>
                      <td className="py-3 px-3 text-slate-600 font-mono text-[11px]">
                        {reg.version_label || '-'}
                      </td>
                      <td className="py-3 px-3 text-slate-500 text-[11px]">
                        {reg.ingested_at ? new Date(reg.ingested_at).toLocaleDateString('tr-TR') : '-'}
                      </td>
                      <td className="py-3 px-3 text-center">
                        <span
                          className={`inline-flex px-2 py-0.5 rounded text-[11px] font-bold ${
                            reg.is_active
                              ? 'bg-emerald-100 text-emerald-800'
                              : 'bg-slate-200 text-slate-600'
                          }`}
                        >
                          {reg.is_active ? 'Aktif' : 'Devre Dışı'}
                        </span>
                      </td>
                      <td className="py-3 px-3 text-right">
                        <button
                          onClick={() => handleToggleRegulation(reg.id, reg.is_active)}
                          className="px-2.5 py-1 text-xs font-semibold rounded border border-slate-300 hover:bg-slate-100 text-slate-700 transition-colors"
                        >
                          {reg.is_active ? 'Pasife Al' : 'Aktif Et'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: LIMITS EDITOR */}
      {activeTab === 'limits' && (
        <div className="space-y-6">
          <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 border-b border-slate-100 pb-3">
              <div className="flex items-center gap-3">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-700">
                  Mali Yıl:
                </span>
                <select
                  value={selectedYear}
                  onChange={(e) => setSelectedYear(parseInt(e.target.value, 10))}
                  className="py-1 px-3 text-xs border border-slate-300 rounded-lg bg-white font-bold text-meu-900"
                >
                  <option value={2026}>2026 Mali Yılı</option>
                  <option value={2027}>2027 Mali Yılı</option>
                </select>
              </div>

              {/* Clone Year Button */}
              <button
                type="button"
                disabled={cloneLoading}
                onClick={handleCloneToNextYear}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold shadow-xs transition-colors"
              >
                <Copy className="w-3.5 h-3.5" />
                <span>{cloneLoading ? 'Kopyalanıyor...' : `${selectedYear + 1} Yılına Kopyala`}</span>
              </button>
            </div>

            {limitActionMsg && (
              <div className="p-3 bg-emerald-50 border border-emerald-300 rounded-lg text-xs font-semibold text-emerald-800 flex items-center gap-2">
                <Check className="w-4 h-4 text-emerald-600" />
                <span>{limitActionMsg}</span>
              </div>
            )}

            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-200 text-slate-500 font-semibold text-[11px] bg-slate-50/70">
                    <th className="py-2.5 px-3">KOD</th>
                    <th className="py-2.5 px-3">TANIM</th>
                    <th className="py-2.5 px-3 text-right">2026 ÜST LİMİTİ (TL)</th>
                    <th className="py-2.5 px-3 text-right">AZAMİ SÜRE (AY)</th>
                    <th className="py-2.5 px-3 text-center">KDV DÂHİL</th>
                    <th className="py-2.5 px-3 text-right">DÜZENLE</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {limits.map((l) => {
                    const isEditing = editingLimitId === l.id;
                    return (
                      <tr key={l.id} className="hover:bg-slate-50">
                        <td className="py-3 px-3 font-mono font-bold text-meu-800">{l.code}</td>
                        <td className="py-3 px-3 font-medium text-slate-800">{l.title_tr}</td>
                        <td className="py-3 px-3 text-right font-mono font-bold">
                          {isEditing ? (
                            <input
                              type="number"
                              step="1000"
                              value={editAmount}
                              onChange={(e) => setEditAmount(parseFloat(e.target.value) || 0)}
                              className="w-32 py-1 px-2 text-xs border border-meu-500 rounded bg-white text-right font-mono"
                            />
                          ) : (
                            <span className="text-slate-900">
                              {l.max_amount_tl.toLocaleString('tr-TR', { minimumFractionDigits: 2 })} TL
                            </span>
                          )}
                        </td>
                        <td className="py-3 px-3 text-right font-mono">
                          {isEditing ? (
                            <input
                              type="number"
                              value={editDuration}
                              onChange={(e) => setEditDuration(parseInt(e.target.value, 10) || 0)}
                              className="w-20 py-1 px-2 text-xs border border-meu-500 rounded bg-white text-right font-mono"
                            />
                          ) : (
                            <span>{l.max_duration_months ? `${l.max_duration_months} Ay` : '-'}</span>
                          )}
                        </td>
                        <td className="py-3 px-3 text-center">
                          <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-700">
                            {l.vat_included ? 'Dâhil' : 'Hariç'}
                          </span>
                        </td>
                        <td className="py-3 px-3 text-right">
                          {isEditing ? (
                            <div className="inline-flex items-center gap-1">
                              <button
                                onClick={() => handleSaveLimit(l.id)}
                                className="px-2 py-1 bg-emerald-700 hover:bg-emerald-800 text-white rounded text-[11px] font-bold flex items-center gap-1"
                              >
                                <Save className="w-3 h-3" />
                                <span>Kaydet</span>
                              </button>
                              <button
                                onClick={() => setEditingLimitId(null)}
                                className="px-2 py-1 bg-slate-200 hover:bg-slate-300 text-slate-700 rounded text-[11px] font-medium"
                              >
                                İptal
                              </button>
                            </div>
                          ) : (
                            <button
                              onClick={() => handleStartEditLimit(l)}
                              className="inline-flex items-center gap-1 px-2.5 py-1 text-xs text-meu-700 hover:text-meu-900 hover:bg-meu-50 rounded transition-colors font-semibold"
                            >
                              <Edit2 className="w-3.5 h-3.5" />
                              <span>Değiştir</span>
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
