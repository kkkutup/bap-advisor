import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { submissionsApi } from '../api/submissionsApi';
import { evaluationApi } from '../api/evaluationApi';
import {
  SubmissionDetailResponse,
  SubmissionVersionResponse,
  StructuredSubmissionDto,
  BudgetLineItemDto,
  BudgetCategory,
  SubmissionDocumentDto,
} from '../types/api';
import { DocumentRoleBadge } from '../components/ui/Badge';
import {
  FileText,
  FileCheck,
  CheckCircle,
  AlertTriangle,
  FileSpreadsheet,
  Plus,
  Trash2,
  ArrowRight,
  ShieldCheck,
  Eye,
  Check,
  ExternalLink,
} from 'lucide-react';

export const ExtractionReviewPage: React.FC = () => {
  const { id, versionNo } = useParams<{ id: string; versionNo: string }>();
  const navigate = useNavigate();
  const vNum = parseInt(versionNo || '1', 10);

  const [submission, setSubmission] = useState<SubmissionDetailResponse | null>(null);
  const [version, setVersion] = useState<SubmissionVersionResponse | null>(null);
  const [selectedDoc, setSelectedDoc] = useState<SubmissionDocumentDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Form State (Editable Structured Data)
  const [projectCode, setProjectCode] = useState('TP2');
  const [projectTitle, setProjectTitle] = useState('');
  const [applicantName, setApplicantName] = useState('');
  const [applicantTitle, setApplicantTitle] = useState('Doç. Dr.');
  const [department, setDepartment] = useState('');
  const [requestedBudget, setRequestedBudget] = useState<number>(124000);
  const [existingBudget, setExistingBudget] = useState<number | ''>('');
  const [requestedDuration, setRequestedDuration] = useState<number>(18);
  const [existingDuration, setExistingDuration] = useState<number | ''>('');
  const [journalQuartile, setJournalQuartile] = useState<string>('');
  const [wetSignaturePresent, setWetSignaturePresent] = useState(true);
  const [ethicsRequired, setEthicsRequired] = useState(false);
  const [budgetItems, setBudgetItems] = useState<BudgetLineItemDto[]>([]);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([
      submissionsApi.getSubmissionById(id),
      submissionsApi.getSubmissionVersion(id, vNum),
    ])
      .then(([sub, ver]) => {
        setSubmission(sub);
        setVersion(ver);
        if (ver.documents && ver.documents.length > 0) {
          setSelectedDoc(ver.documents[0]);
        }

        const meta = ver.extracted_metadata;
        if (meta) {
          setProjectCode(meta.detected_project_code || sub.project_code || 'TP2');
          setProjectTitle(meta.project_title || sub.title);
          setApplicantName(meta.applicant_name || sub.applicant_name);
          setApplicantTitle(meta.applicant_title || sub.applicant_title || 'Doç. Dr.');
          setDepartment(meta.department || sub.department || '');
          setRequestedBudget(meta.requested_budget_total_tl || 0);
          setExistingBudget(meta.existing_approved_budget_tl ?? '');
          setRequestedDuration(meta.requested_duration_months || 12);
          setExistingDuration(meta.existing_duration_months ?? '');
          setJournalQuartile(meta.journal_quartile || '');
          setWetSignaturePresent(meta.wet_signature_present);
          setEthicsRequired(meta.ethics_required_keywords_detected);
          setBudgetItems(meta.budget_line_items || []);
        }
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [id, vNum]);

  // Live sum calculations
  const calculatedSum = budgetItems.reduce((acc, it) => acc + (Number(it.total_tl) || 0), 0);
  const isSumConsistent = Math.abs(calculatedSum - Number(requestedBudget)) < 0.01;
  const difference = Number(requestedBudget) - calculatedSum;

  const handleAddBudgetItem = () => {
    const newItem: BudgetLineItemDto = {
      category: 'CONSUMABLE',
      name: 'Yeni Harcama / Sarf Malzeme Kalemi',
      quantity: 1,
      unit: 'Adet',
      unit_price_tl: 1000,
      total_tl: 1000,
      vat_included: true,
      proforma_count: 1,
      has_technical_spec: false,
    };
    setBudgetItems([...budgetItems, newItem]);
  };

  const handleBudgetItemChange = (index: number, field: keyof BudgetLineItemDto, val: any) => {
    const updated = [...budgetItems];
    const item = { ...updated[index], [field]: val };

    // Auto calculate total if quantity and unit_price change
    if (field === 'quantity' || field === 'unit_price_tl') {
      const q = field === 'quantity' ? Number(val) : Number(item.quantity || 1);
      const u = field === 'unit_price_tl' ? Number(val) : Number(item.unit_price_tl || 0);
      item.total_tl = q * u;
    }

    updated[index] = item;
    setBudgetItems(updated);
  };

  const handleRemoveBudgetItem = (index: number) => {
    setBudgetItems(budgetItems.filter((_, i) => i !== index));
  };

  const handleConfirmAndEvaluate = async () => {
    if (!id) return;
    setSaving(true);
    setError(null);
    try {
      const payload: StructuredSubmissionDto = {
        detected_submission_type: submission?.submission_type || 'NEW_PROJECT',
        detected_project_code: projectCode,
        applicant_name: applicantName,
        applicant_title: applicantTitle,
        department,
        project_title: projectTitle,
        requested_budget_total_tl: requestedBudget,
        existing_approved_budget_tl: existingBudget === '' ? null : Number(existingBudget),
        requested_duration_months: requestedDuration,
        existing_duration_months: existingDuration === '' ? null : Number(existingDuration),
        journal_quartile: (journalQuartile as any) || null,
        wet_signature_present: wetSignaturePresent,
        ethics_required_keywords_detected: ethicsRequired,
        detected_attachments: version?.documents?.map((d) => d.doc_role) || ['APPLICATION_FORM'],
        budget_line_items: budgetItems,
      };

      // 1. Update extracted data
      await submissionsApi.updateExtractedData(id, vNum, payload);

      // 2. Trigger evaluation with modules
      await evaluationApi.evaluateSubmissionVersion(id, vNum);

      // 3. Navigate to Audit Report
      navigate(`/submissions/${id}/versions/${vNum}/report`);
    } catch (err: any) {
      setError(err?.message || 'Veriler onaylanırken hata oluştu.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-4 border-meu-600 border-t-transparent rounded-full animate-spin"></div>
          <p className="text-xs text-slate-500 font-medium">OCR çıkarım verileri ve belgeler yükleniyor...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Top Banner and Navigation Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 bg-white p-4 rounded-xl border border-slate-200 shadow-xs">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-sm font-bold text-meu-900">{submission?.reference_no}</span>
            <span className="px-2 py-0.5 rounded text-xs font-semibold bg-amber-100 text-amber-800 border border-amber-200">
              Versiyon v{vNum} — İnsan Doğrulamalı OCR Gözden Geçirme
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-0.5">
            Sol panelde orijinal belgeyi inceleyin; sağ panelde OCR'ın çıkardığı yapısal verileri doğrulayıp onaylayın.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            disabled={saving}
            onClick={handleConfirmAndEvaluate}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white text-xs font-bold shadow-md transition-colors cursor-pointer"
          >
            <Check className="w-4 h-4" />
            <span>{saving ? 'Denetim Başlatılıyor...' : 'Verileri Onayla ve Mevzuat Denetimini Başlat'}</span>
          </button>
        </div>
      </div>

      {error && (
        <div className="p-3 bg-rose-50 border border-rose-200 rounded-lg text-xs text-rose-700 flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Side-by-Side Split Pane */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 min-h-[calc(100vh-14rem)]">
        {/* Left Pane (5 cols): Embedded Document Preview & Files List */}
        <div className="lg:col-span-5 flex flex-col space-y-4">
          {/* Documents Tabs / List */}
          <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs space-y-3">
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700">
              Paketteki Belgeler ve OCR Algılama Durumu ({version?.documents.length || 0})
            </h3>
            <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
              {version?.documents.map((doc) => {
                const isSelected = selectedDoc?.id === doc.id;
                return (
                  <div
                    key={doc.id}
                    onClick={() => setSelectedDoc(doc)}
                    className={`p-2.5 rounded-lg border text-xs cursor-pointer transition-all ${
                      isSelected
                        ? 'border-meu-500 bg-meu-50/60 shadow-xs'
                        : 'border-slate-200 hover:border-slate-300 bg-slate-50/50'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-2 truncate">
                        <FileText className={`w-4 h-4 ${isSelected ? 'text-meu-700' : 'text-slate-400'}`} />
                        <span className="font-semibold text-slate-800 truncate" title={doc.file_name}>
                          {doc.file_name}
                        </span>
                      </div>
                      <DocumentRoleBadge role={doc.doc_role} />
                    </div>

                    <div className="flex items-center justify-between gap-2 mt-2 pt-1.5 border-t border-slate-200/50 text-[11px]">
                      <div className="flex items-center gap-1.5">
                        <span className="text-slate-500">OCR Güveni:</span>
                        <span className="font-mono font-bold text-meu-700">
                          {doc.ocr_confidence ? `%${Math.round(doc.ocr_confidence * 100)}` : '%98'}
                        </span>
                      </div>
                      <div>
                        {doc.has_signature_detected ? (
                          <span className="inline-flex items-center gap-1 font-semibold text-emerald-700">
                            <CheckCircle className="w-3.5 h-3.5" />
                            <span>Islak İmza Var</span>
                          </span>
                        ) : doc.has_signature_detected === false ? (
                          <span className="inline-flex items-center gap-1 font-semibold text-rose-600">
                            <AlertTriangle className="w-3.5 h-3.5" />
                            <span>İmza Yok / Eksik</span>
                          </span>
                        ) : (
                          <span className="text-slate-400">İmza Aranmıyor</span>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Document Viewer Pane */}
          <div className="bg-slate-900 rounded-xl border border-slate-800 shadow-md flex-1 flex flex-col overflow-hidden min-h-[420px]">
            <div className="px-4 py-2 bg-slate-950 border-b border-slate-800 flex items-center justify-between text-xs text-slate-300">
              <span className="truncate font-mono font-medium">
                {selectedDoc?.file_name || 'Belge Seçilmedi'}
              </span>
              <span className="text-[11px] text-slate-400">PDF / Görsel Önizleme</span>
            </div>
            <div className="flex-1 p-6 flex flex-col items-center justify-center text-center text-slate-400 space-y-4">
              <div className="w-16 h-16 rounded-2xl bg-slate-800 border border-slate-700 flex items-center justify-center text-meu-400">
                <FileText className="w-8 h-8" />
              </div>
              <div className="max-w-sm space-y-1">
                <p className="text-xs font-semibold text-slate-200">
                  {selectedDoc?.file_name}
                </p>
                <p className="text-[11px] text-slate-400">
                  Doküman depolama yolu: <span className="font-mono text-slate-300">{selectedDoc?.storage_path}</span>
                </p>
                <div className="pt-2">
                  <span className="px-2 py-1 rounded bg-slate-800 text-slate-300 text-[11px] font-mono">
                    Hibrit OCR: PyMuPDF + Docling Ayrıştırıldı
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Right Pane (7 cols): Editable Form Fields & Budget Table */}
        <div className="lg:col-span-7 space-y-4">
          <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-5">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div>
                <h3 className="text-sm font-bold text-slate-900">
                  Çıkarılan Yapısal Form Alanları (Düzenlenebilir)
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  OCR motorunun okuduğu alanları gerekirse el ile düzeltin; kural motoru bu değerlere göre çalışır
                </p>
              </div>
              <span className="px-2 py-0.5 rounded bg-blue-50 text-blue-700 text-xs font-semibold border border-blue-200">
                Canlı Senkronizasyon
              </span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {/* Project Code */}
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Proje / Destek Kodu
                </label>
                <select
                  value={projectCode}
                  onChange={(e) => setProjectCode(e.target.value)}
                  className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white font-mono font-bold"
                >
                  <option value="TP1">TP1 (Lisans — 30.000 TL)</option>
                  <option value="TP2">TP2 (Yüksek Lisans — 125.000 TL)</option>
                  <option value="TP3">TP3 (Doktora — 210.000 TL)</option>
                  <option value="AP1">AP1 (Kariyer — 250.000 TL)</option>
                  <option value="AP2">AP2 (Bireysel — 300.000 TL)</option>
                  <option value="AP3">AP3 (Öncelikli — 500.000 TL)</option>
                  <option value="AP4">AP4 (Kapsamlı — 800.000 TL)</option>
                  <option value="AP7">AP7 (Hızlı Destek — 100.000 TL)</option>
                  <option value="AP9">AP9 (Yarışma — 50.000 TL)</option>
                  <option value="WOS_Q1_TOP10">WOS_Q1_TOP10 (120.000 TL)</option>
                  <option value="WOS_Q1_OTHER">WOS_Q1_OTHER (80.000 TL)</option>
                  <option value="WOS_Q2">WOS_Q2 (60.000 TL)</option>
                </select>
              </div>

              {/* Declared Total Budget */}
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Talep Edilen Toplam Bütçe (TL)
                </label>
                <input
                  type="number"
                  step="0.01"
                  value={requestedBudget}
                  onChange={(e) => setRequestedBudget(parseFloat(e.target.value) || 0)}
                  className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white font-mono font-bold text-meu-900"
                />
              </div>

              {/* Requested Duration */}
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Talep Edilen Proje Süresi (Ay)
                </label>
                <input
                  type="number"
                  value={requestedDuration}
                  onChange={(e) => setRequestedDuration(parseInt(e.target.value, 10) || 0)}
                  className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white font-mono"
                />
              </div>

              {/* Journal Quartile (for Open Access) */}
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Dergi Dilimi (WoS Çeyreklik)
                </label>
                <select
                  value={journalQuartile}
                  onChange={(e) => setJournalQuartile(e.target.value)}
                  className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white font-mono"
                >
                  <option value="">Uygulanabilir Değil (Yeni Proje)</option>
                  <option value="Q1_TOP10">WoS Q1 — İlk %10 Dilim (120.000 TL)</option>
                  <option value="Q1_OTHER">WoS Q1 — Diğer (80.000 TL)</option>
                  <option value="Q2">WoS Q2 (60.000 TL)</option>
                  <option value="Q3">WoS Q3 (Destek Kapsamı Dışı)</option>
                  <option value="Q4">WoS Q4 (Destek Kapsamı Dışı)</option>
                </select>
              </div>

              {/* Checkboxes: Wet signature & Ethics */}
              <div className="sm:col-span-2 flex flex-col sm:flex-row sm:items-center gap-4 p-3 bg-slate-50 rounded-lg border border-slate-200">
                <label className="flex items-center gap-2 cursor-pointer text-xs font-semibold text-slate-800">
                  <input
                    type="checkbox"
                    checked={wetSignaturePresent}
                    onChange={(e) => setWetSignaturePresent(e.target.checked)}
                    className="w-4 h-4 text-meu-600 rounded border-slate-300 focus:ring-meu-500"
                  />
                  <span>Islak İmza / Kaşe Tespit Edildi</span>
                </label>

                <label className="flex items-center gap-2 cursor-pointer text-xs font-semibold text-slate-800">
                  <input
                    type="checkbox"
                    checked={ethicsRequired}
                    onChange={(e) => setEthicsRequired(e.target.checked)}
                    className="w-4 h-4 text-amber-600 rounded border-slate-300 focus:ring-amber-500"
                  />
                  <span>Etik Kurul İzni Gerektiren Çalışma (İnsan/Hayvan Deneyi/Anket)</span>
                </label>
              </div>
            </div>

            {/* Budget Line Items Section with Live Arithmetic Checker */}
            <div className="space-y-3 pt-2">
              <div className="flex items-center justify-between border-t border-slate-100 pt-4">
                <div className="flex items-center gap-2">
                  <FileSpreadsheet className="w-4 h-4 text-meu-700" />
                  <h4 className="text-xs font-bold uppercase tracking-wider text-slate-800">
                    Bütçe Kalemleri Tablosu ({budgetItems.length} Kalem)
                  </h4>
                </div>

                {/* Arithmetic Checker Badge */}
                <div className="flex items-center gap-2">
                  <div
                    className={`px-2.5 py-1 rounded-md text-xs font-semibold flex items-center gap-1.5 border ${
                      isSumConsistent
                        ? 'bg-emerald-50 text-emerald-800 border-emerald-300'
                        : 'bg-rose-50 text-rose-800 border-rose-300'
                    }`}
                  >
                    {isSumConsistent ? (
                      <>
                        <CheckCircle className="w-3.5 h-3.5 text-emerald-600" />
                        <span>Kalemler Toplamla Tutarlı ({calculatedSum.toLocaleString('tr-TR')} TL)</span>
                      </>
                    ) : (
                      <>
                        <AlertTriangle className="w-3.5 h-3.5 text-rose-600" />
                        <span>
                          Fark: {difference > 0 ? `+${difference.toLocaleString('tr-TR')}` : difference.toLocaleString('tr-TR')} TL
                          (Kalemler: {calculatedSum.toLocaleString('tr-TR')} TL vs Beyan: {Number(requestedBudget).toLocaleString('tr-TR')} TL)
                        </span>
                      </>
                    )}
                  </div>

                  <button
                    type="button"
                    onClick={handleAddBudgetItem}
                    className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold transition-colors"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Kalem Ekle</span>
                  </button>
                </div>
              </div>

              {/* Items Table */}
              <div className="overflow-x-auto border border-slate-200 rounded-lg">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="bg-slate-50 text-slate-500 font-semibold text-[11px] border-b border-slate-200">
                      <th className="py-2 px-2.5">KATEGORİ</th>
                      <th className="py-2 px-2.5">KALEM ADI / AÇIKLAMA</th>
                      <th className="py-2 px-2.5 text-right">MİKTAR</th>
                      <th className="py-2 px-2.5 text-right">BİRİM FİYAT (TL)</th>
                      <th className="py-2 px-2.5 text-right">TOPLAM (TL)</th>
                      <th className="py-2 px-2.5 text-center">İŞLEM</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {budgetItems.map((item, idx) => (
                      <tr key={idx} className="hover:bg-slate-50/60">
                        <td className="py-2 px-2.5">
                          <select
                            value={item.category}
                            onChange={(e) => handleBudgetItemChange(idx, 'category', e.target.value as BudgetCategory)}
                            className="py-1 px-2 text-[11px] border border-slate-300 rounded bg-white"
                          >
                            <option value="CONSUMABLE">Sarf Malzeme</option>
                            <option value="EQUIPMENT">Makine / Donanım</option>
                            <option value="SERVICE_PURCHASE">Hizmet Alımı</option>
                            <option value="TRAVEL">Yolluk / Seyahat</option>
                            <option value="PUBLICATION">Yayın / Basım</option>
                            <option value="OTHER">Diğer</option>
                          </select>
                        </td>
                        <td className="py-2 px-2.5">
                          <input
                            type="text"
                            value={item.name}
                            onChange={(e) => handleBudgetItemChange(idx, 'name', e.target.value)}
                            className="w-full py-1 px-2 text-xs border border-slate-300 rounded bg-white font-medium"
                          />
                        </td>
                        <td className="py-2 px-2.5 text-right">
                          <input
                            type="number"
                            value={item.quantity || 1}
                            onChange={(e) => handleBudgetItemChange(idx, 'quantity', parseFloat(e.target.value) || 1)}
                            className="w-16 py-1 px-2 text-xs border border-slate-300 rounded bg-white text-right font-mono"
                          />
                        </td>
                        <td className="py-2 px-2.5 text-right">
                          <input
                            type="number"
                            step="0.01"
                            value={item.unit_price_tl || 0}
                            onChange={(e) => handleBudgetItemChange(idx, 'unit_price_tl', parseFloat(e.target.value) || 0)}
                            className="w-24 py-1 px-2 text-xs border border-slate-300 rounded bg-white text-right font-mono"
                          />
                        </td>
                        <td className="py-2 px-2.5 text-right font-mono font-bold text-slate-900">
                          {Number(item.total_tl).toLocaleString('tr-TR', { minimumFractionDigits: 2 })} TL
                        </td>
                        <td className="py-2 px-2.5 text-center">
                          <button
                            type="button"
                            onClick={() => handleRemoveBudgetItem(idx)}
                            className="p-1 text-slate-400 hover:text-rose-600 rounded transition-colors"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
