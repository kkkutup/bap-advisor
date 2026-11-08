import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { submissionsApi } from '../api/submissionsApi';
import { evaluationApi } from '../api/evaluationApi';
import {
  SubmissionDetailResponse,
  SubmissionVersionResponse,
  EvaluationResultResponse,
  FindingDto,
  FindingSeverity,
  FindingSource,
} from '../types/api';
import { SeverityBadge, SourceBadge, SubmissionTypeBadge } from '../components/ui/Badge';
import { Modal } from '../components/ui/Modal';
import {
  ShieldCheck,
  AlertTriangle,
  FileCheck,
  Send,
  RotateCcw,
  Download,
  Copy,
  MessageSquare,
  ChevronDown,
  ChevronUp,
  Cpu,
  Clock,
  ExternalLink,
  Edit3,
  Check,
  GitCompare,
  X,
  FileText,
  UserCheck,
} from 'lucide-react';

export const AuditReportPage: React.FC = () => {
  const { id, versionNo } = useParams<{ id: string; versionNo: string }>();
  const vNum = parseInt(versionNo || '1', 10);

  const [submission, setSubmission] = useState<SubmissionDetailResponse | null>(null);
  const [version, setVersion] = useState<SubmissionVersionResponse | null>(null);
  const [evaluation, setEvaluation] = useState<EvaluationResultResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters for checklist
  const [severityFilter, setSeverityFilter] = useState<string>('ALL');
  const [sourceFilter, setSourceFilter] = useState<string>('ALL');
  const [expandedQuotes, setExpandedQuotes] = useState<Record<string, boolean>>({});

  // Override Modal state
  const [overrideModalOpen, setOverrideModalOpen] = useState(false);
  const [selectedFinding, setSelectedFinding] = useState<FindingDto | null>(null);
  const [overrideStatus, setOverrideStatus] = useState<'APPROVED' | 'REJECTED' | 'WAIVED'>('WAIVED');
  const [overrideReason, setOverrideReason] = useState('');
  const [overrideSaving, setOverrideSaving] = useState(false);

  // Drafts state (editable by officer)
  const [revisionDraft, setRevisionDraft] = useState('');
  const [referralSummary, setReferralSummary] = useState('');
  const [copiedDraft, setCopiedDraft] = useState<string | null>(null);

  // Contextual Q&A panel state
  const [qaOpen, setQaOpen] = useState(false);
  const [qaQuestion, setQaQuestion] = useState('');
  const [qaMessages, setQaMessages] = useState<
    Array<{
      role: 'user' | 'assistant';
      content: string;
      citations?: Array<{ regulation_doc_name: string; regulation_article_ref: string; verbatim_quote: string }>;
    }>
  >([]);
  const [qaLoading, setQaLoading] = useState(false);

  // Status Action state
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([
      submissionsApi.getSubmissionById(id),
      submissionsApi.getSubmissionVersion(id, vNum),
      evaluationApi.getEvaluationResult(id, vNum),
    ])
      .then(([sub, ver, evalRes]) => {
        setSubmission(sub);
        setVersion(ver);
        setEvaluation(evalRes);
        setRevisionDraft(evalRes.revision_draft_tr || '');
        setReferralSummary(evalRes.commission_referral_summary_tr || '');
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [id, vNum]);

  const toggleQuote = (key: string) => {
    setExpandedQuotes((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const handleOpenOverride = (finding: FindingDto) => {
    setSelectedFinding(finding);
    setOverrideStatus('WAIVED');
    setOverrideReason('');
    setOverrideModalOpen(true);
  };

  const handleSaveOverride = () => {
    if (!selectedFinding || !overrideReason.trim()) return;
    setOverrideSaving(true);
    // Update locally
    if (evaluation) {
      const updatedFindings = evaluation.findings.map((f) => {
        if (f.rule_code === selectedFinding.rule_code) {
          return {
            ...f,
            officer_override_status: overrideStatus,
            officer_override_reason: overrideReason,
          };
        }
        return f;
      });
      setEvaluation({ ...evaluation, findings: updatedFindings });
    }
    setOverrideSaving(false);
    setOverrideModalOpen(false);
  };

  const handleCopy = (text: string, type: string) => {
    navigator.clipboard.writeText(text);
    setCopiedDraft(type);
    setTimeout(() => setCopiedDraft(null), 2500);
  };

  const handleExport = async (format: 'pdf' | 'docx') => {
    if (!id) return;
    try {
      const blob = await evaluationApi.exportAuditReport(id, vNum, format);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `MEU_BAP_On_Inceleme_Tutanagi_${submission?.reference_no || id}_v${vNum}.${format}`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err: any) {
      alert(`Rapor indirme hatası: ${err?.message}`);
    }
  };

  const handleUpdateStatus = async (targetStatus: any) => {
    if (!id) return;
    try {
      await submissionsApi.updateSubmissionStatus(id, {
        target_status: targetStatus,
        officer_notes: 'Ön inceleme denetim raporu onaylandı.',
      });
      setActionSuccess(`Başvuru durumu başarıyla güncellendi: ${targetStatus}`);
      setTimeout(() => setActionSuccess(null), 3000);
      // Reload sub
      const refreshed = await submissionsApi.getSubmissionById(id);
      setSubmission(refreshed);
    } catch (err: any) {
      alert(`Durum geçiş hatası: ${err?.message}`);
    }
  };

  const handleSendQa = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id || !qaQuestion.trim()) return;
    const q = qaQuestion;
    setQaQuestion('');
    setQaMessages((prev) => [...prev, { role: 'user', content: q }]);
    setQaLoading(true);

    try {
      const res = await evaluationApi.submitContextualQa(id, vNum, q);
      setQaMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          content: res.answer_tr,
          citations: res.citations,
        },
      ]);
    } catch (err: any) {
      setQaMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          content: `Hata: ${err?.message || 'Mevzuat sorgusu yanıtlanamadı.'}`,
        },
      ]);
    } finally {
      setQaLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-4 border-meu-600 border-t-transparent rounded-full animate-spin"></div>
          <p className="text-xs text-slate-500 font-medium">Ön inceleme raporu ve mevzuat bulguları derleniyor...</p>
        </div>
      </div>
    );
  }

  // Filter findings
  const findings = evaluation?.findings || [];
  const filteredFindings = findings.filter((f) => {
    if (severityFilter !== 'ALL' && f.severity !== severityFilter) return false;
    if (sourceFilter !== 'ALL' && f.source_type !== sourceFilter) return false;
    return true;
  });

  const violationCount = findings.filter((f) => f.severity === 'VIOLATION').length;
  const missingCount = findings.filter((f) => f.severity === 'MISSING_DOCUMENT').length;
  const commissionCount = findings.filter((f) => f.severity === 'AMBIGUOUS_COMMISSION').length;
  const compliantCount = findings.filter((f) => f.severity === 'COMPLIANT').length;

  const overallStatus = evaluation?.overall_compliance_status || 'NEEDS_REVISION';

  return (
    <div className="space-y-6">
      {/* Action Notification */}
      {actionSuccess && (
        <div className="p-3 bg-emerald-50 border border-emerald-300 rounded-xl text-xs font-semibold text-emerald-800 flex items-center justify-between shadow-xs">
          <div className="flex items-center gap-2">
            <Check className="w-4 h-4 text-emerald-600" />
            <span>{actionSuccess}</span>
          </div>
          <button onClick={() => setActionSuccess(null)} className="text-emerald-700 hover:text-emerald-900">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Top Header & Workflow Bar */}
      <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-sm font-bold text-meu-900">{submission?.reference_no}</span>
            <span className="px-2 py-0.5 rounded text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-200 font-mono">
              v{vNum} Raporu
            </span>
            <SubmissionTypeBadge
              type={submission?.submission_type || 'NEW_PROJECT'}
              projectCode={submission?.project_code}
            />
          </div>
          <h2 className="text-base font-bold text-slate-900 mt-1">{submission?.title}</h2>
          <p className="text-xs text-slate-500 mt-0.5">
            Yürütücü: <strong>{submission?.applicant_title} {submission?.applicant_name}</strong> • {submission?.department}
          </p>
        </div>

        {/* Workflow Action Buttons */}
        <div className="flex flex-wrap items-center gap-2">
          {/* Version Diff button if v > 1 */}
          {vNum > 1 && (
            <Link
              to={`/submissions/${id}/diff?fromVersion=1&toVersion=${vNum}`}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-300 hover:bg-slate-50 text-slate-700 text-xs font-semibold transition-colors"
            >
              <GitCompare className="w-3.5 h-3.5 text-slate-500" />
              <span>v1 &harr; v{vNum} Karşılaştır</span>
            </Link>
          )}

          {/* Export PDF / DOCX */}
          <div className="inline-flex rounded-lg border border-slate-300 p-0.5 bg-slate-50">
            <button
              onClick={() => handleExport('pdf')}
              className="px-2.5 py-1 text-xs font-semibold text-slate-700 hover:bg-white rounded-md transition-colors flex items-center gap-1"
            >
              <Download className="w-3.5 h-3.5 text-rose-600" />
              <span>PDF Tutanak</span>
            </button>
            <button
              onClick={() => handleExport('docx')}
              className="px-2.5 py-1 text-xs font-semibold text-slate-700 hover:bg-white rounded-md transition-colors flex items-center gap-1"
            >
              <Download className="w-3.5 h-3.5 text-blue-600" />
              <span>DOCX</span>
            </button>
          </div>

          {/* Return for Revision */}
          <button
            onClick={() => handleUpdateStatus('REVISION_REQUESTED')}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-700 text-white text-xs font-semibold shadow-xs transition-colors"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Revizyon İste (İade)</span>
          </button>

          {/* Refer to Commission */}
          <button
            onClick={() => handleUpdateStatus('COMMISSION_REFERRED')}
            className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white text-xs font-bold shadow-xs transition-colors"
          >
            <Send className="w-3.5 h-3.5" />
            <span>Profesör Kuruluna Sevk Et</span>
          </button>
        </div>
      </div>

      {/* Overall Compliance Status Banner */}
      <div
        className={`p-5 rounded-xl border shadow-xs ${
          overallStatus === 'COMPLIANT'
            ? 'bg-emerald-50/80 border-emerald-300 text-emerald-950'
            : overallStatus === 'NEEDS_REVISION'
            ? 'bg-amber-50/80 border-amber-300 text-amber-950'
            : overallStatus === 'COMMISSION_REVIEW'
            ? 'bg-purple-50/80 border-purple-300 text-purple-950'
            : 'bg-rose-50/80 border-rose-300 text-rose-950'
        }`}
      >
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div className="flex items-start gap-3.5">
            <div className="p-2.5 rounded-xl bg-white shadow-xs shrink-0">
              {overallStatus === 'COMPLIANT' ? (
                <ShieldCheck className="w-6 h-6 text-emerald-600" />
              ) : (
                <AlertTriangle className="w-6 h-6 text-amber-600" />
              )}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
                  Genel Ön İnceleme Mevzuat Kararı:
                </span>
                <span className="text-sm font-extrabold tracking-tight">
                  {overallStatus === 'COMPLIANT' && 'ŞEKLİ MEVZUATA UYGUN (KOMİSYONA SEVKE HAZIR)'}
                  {overallStatus === 'NEEDS_REVISION' && 'REVİZYON / EKSİK BELGE GEREKLİ (İADE EDİLMELİ)'}
                  {overallStatus === 'COMMISSION_REVIEW' && 'PROFESÖR KURULU TAKDİRİNE SUNULMALI'}
                  {overallStatus === 'FORMAL_REJECTION' && 'KESİN MEVZUAT ENGELİ (REDDİ GEREKLİ)'}
                </span>
              </div>
              <p className="text-xs mt-1 text-slate-700 max-w-4xl">
                {overallStatus === 'COMPLIANT'
                  ? 'Başvuru dosyası 2026 MEÜ BAP bütçe tavanı, süre kısıtlaması, aritmetik tutarlılık ve zorunlu belgeler bakımından idari mevzuata tam uygundur.'
                  : 'Tespit edilen kural ihlalleri veya eksik belgeler giderilmeden BAP Komisyonuna sevk edilemez. Aşağıdaki aksiyon metnini kullanarak yürütücüye iade ediniz.'}
              </p>
            </div>
          </div>

          {/* Model metadata */}
          <div className="text-[11px] text-slate-500 space-y-1 sm:text-right border-t sm:border-t-0 pt-2 sm:pt-0 border-slate-200">
            <div className="flex items-center sm:justify-end gap-1.5 font-mono">
              <Cpu className="w-3.5 h-3.5 text-meu-600" />
              <span>{evaluation?.llm_provider_used} / {evaluation?.llm_model_name}</span>
            </div>
            <div className="flex items-center sm:justify-end gap-1 text-[10px]">
              <Clock className="w-3 h-3" />
              <span>Tamamlanma: {new Date(evaluation?.completed_at || '').toLocaleTimeString('tr-TR')}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Main Grid: Checklist (8 cols) + Drafts & QA Drawer (4 cols) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column (8 cols): Filterable Findings Checklist */}
        <div className="lg:col-span-8 space-y-4">
          {/* Checklist Filter Bar */}
          <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs space-y-3">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <FileCheck className="w-4 h-4 text-meu-700" />
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-800">
                  Madde Atıflı Denetim Bulguları ({findings.length})
                </h3>
              </div>
              <div className="flex items-center gap-2 text-xs font-semibold">
                {violationCount > 0 && <span className="text-rose-600 font-bold">{violationCount} İhlal</span>}
                {missingCount > 0 && <span className="text-amber-600 font-bold">{missingCount} Eksik</span>}
                <span className="text-emerald-600 font-bold">{compliantCount} Uygun</span>
              </div>
            </div>

            {/* Severity Tabs */}
            <div className="flex flex-wrap items-center gap-1.5 text-xs">
              <button
                onClick={() => setSeverityFilter('ALL')}
                className={`px-2.5 py-1 rounded-md font-semibold transition-colors ${
                  severityFilter === 'ALL'
                    ? 'bg-slate-900 text-white'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                Tümü ({findings.length})
              </button>
              <button
                onClick={() => setSeverityFilter('VIOLATION')}
                className={`px-2.5 py-1 rounded-md font-semibold transition-colors ${
                  severityFilter === 'VIOLATION'
                    ? 'bg-rose-600 text-white'
                    : 'bg-rose-50 text-rose-700 hover:bg-rose-100'
                }`}
              >
                İhlaller ({violationCount})
              </button>
              <button
                onClick={() => setSeverityFilter('MISSING_DOCUMENT')}
                className={`px-2.5 py-1 rounded-md font-semibold transition-colors ${
                  severityFilter === 'MISSING_DOCUMENT'
                    ? 'bg-amber-600 text-white'
                    : 'bg-amber-50 text-amber-800 hover:bg-amber-100'
                }`}
              >
                Eksik Belgeler ({missingCount})
              </button>
              <button
                onClick={() => setSeverityFilter('COMPLIANT')}
                className={`px-2.5 py-1 rounded-md font-semibold transition-colors ${
                  severityFilter === 'COMPLIANT'
                    ? 'bg-emerald-600 text-white'
                    : 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100'
                }`}
              >
                Uygunluklar ({compliantCount})
              </button>

              <div className="ml-auto flex items-center gap-1 text-[11px] text-slate-500 font-medium">
                <span>Kaynak:</span>
                <select
                  value={sourceFilter}
                  onChange={(e) => setSourceFilter(e.target.value)}
                  className="py-0.5 px-2 text-[11px] border border-slate-300 rounded bg-white"
                >
                  <option value="ALL">Tüm Kaynaklar</option>
                  <option value="DETERMINISTIC_RULE">Yalnızca Deterministik Kural</option>
                  <option value="RAG_LLM">Yalnızca RAG Mevzuat</option>
                </select>
              </div>
            </div>
          </div>

          {/* Findings List */}
          <div className="space-y-3">
            {filteredFindings.map((finding, idx) => {
              const quoteKey = `quote-${idx}`;
              const isQuoteExpanded = !!expandedQuotes[quoteKey];
              return (
                <div
                  key={idx}
                  className={`bg-white rounded-xl border p-4.5 shadow-xs transition-shadow space-y-3 ${
                    finding.severity === 'VIOLATION'
                      ? 'border-rose-200 hover:border-rose-300'
                      : finding.severity === 'MISSING_DOCUMENT'
                      ? 'border-amber-200 hover:border-amber-300'
                      : 'border-slate-200 hover:border-slate-300'
                  }`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="space-y-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <SeverityBadge severity={finding.severity} />
                        <SourceBadge source={finding.source_type} />
                        <span className="font-mono text-[11px] font-bold text-slate-600">
                          {finding.rule_code}
                        </span>
                        {finding.officer_override_status && (
                          <span className="px-1.5 py-0.2 bg-purple-100 text-purple-800 text-[10px] font-bold rounded">
                            MEMUR OVERRIDE: {finding.officer_override_status}
                          </span>
                        )}
                      </div>
                      <h4 className="text-sm font-bold text-slate-900">{finding.title_tr}</h4>
                    </div>

                    {/* Override Button for Officer */}
                    <button
                      onClick={() => handleOpenOverride(finding)}
                      className="inline-flex items-center gap-1 px-2 py-1 rounded text-[11px] font-semibold text-slate-600 hover:text-meu-700 hover:bg-slate-100 transition-colors shrink-0"
                    >
                      <Edit3 className="w-3.5 h-3.5" />
                      <span>Bulguyu Değiştir</span>
                    </button>
                  </div>

                  <p className="text-xs text-slate-700 leading-relaxed">{finding.explanation_tr}</p>

                  {/* Expected vs Actual Values */}
                  {(finding.expected_value || finding.actual_value) && (
                    <div className="grid grid-cols-2 gap-2 p-2.5 rounded-lg bg-slate-50 border border-slate-200/80 text-xs font-mono">
                      <div>
                        <span className="text-[10px] uppercase font-bold text-slate-400 block">
                          Beklenen Mevzuat Değeri
                        </span>
                        <span className="font-semibold text-slate-700">{finding.expected_value || '-'}</span>
                      </div>
                      <div>
                        <span className="text-[10px] uppercase font-bold text-slate-400 block">
                          Formdaki Tespit Edilen Değer
                        </span>
                        <span
                          className={`font-bold ${
                            finding.severity === 'VIOLATION' ? 'text-rose-600' : 'text-slate-900'
                          }`}
                        >
                          {finding.actual_value || '-'}
                        </span>
                      </div>
                    </div>
                  )}

                  {/* Legal Citation Footnote */}
                  <div className="flex items-center justify-between pt-1 text-[11px] text-slate-500 border-t border-slate-100">
                    <div className="flex items-center gap-1.5 font-medium">
                      <span className="font-bold text-meu-800">{finding.regulation_doc_name}</span>
                      <span>&bull;</span>
                      <span className="font-mono text-meu-700 font-semibold">{finding.regulation_article_ref}</span>
                    </div>

                    {finding.verbatim_quote && (
                      <button
                        onClick={() => toggleQuote(quoteKey)}
                        className="inline-flex items-center gap-1 text-meu-600 hover:text-meu-800 font-medium"
                      >
                        <span>{isQuoteExpanded ? 'Alıntıyı Gizle' : 'Mevzuat Metnini Gör'}</span>
                        {isQuoteExpanded ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                      </button>
                    )}
                  </div>

                  {/* Collapsible Verbatim Quote */}
                  {isQuoteExpanded && finding.verbatim_quote && (
                    <div className="p-3 rounded-lg bg-blue-50/60 border border-blue-200 text-xs text-slate-800 italic font-serif leading-relaxed">
                      {finding.verbatim_quote}
                    </div>
                  )}

                  {/* If override reason exists, show it */}
                  {finding.officer_override_reason && (
                    <div className="p-2.5 rounded-lg bg-purple-50 border border-purple-200 text-xs text-purple-900">
                      <strong>Memur İstisna Gerekçesi:</strong> {finding.officer_override_reason}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {/* Right Column (4 cols): Action Drafts & Interactive Q&A */}
        <div className="lg:col-span-4 space-y-4">
          {/* Action Draft 1: Yürütücü İade Notu */}
          <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs space-y-3">
            <div className="flex items-center justify-between border-b border-slate-100 pb-2.5">
              <h4 className="text-xs font-bold uppercase tracking-wider text-slate-800">
                Yürütücü İade / Revizyon Yazısı
              </h4>
              <button
                onClick={() => handleCopy(revisionDraft, 'revision')}
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors"
              >
                {copiedDraft === 'revision' ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copiedDraft === 'revision' ? 'Kopyalandı' : 'Kopyala'}</span>
              </button>
            </div>
            <textarea
              rows={8}
              value={revisionDraft}
              onChange={(e) => setRevisionDraft(e.target.value)}
              placeholder="İhlal durumunda yürütücüye gönderilecek resmi iade metni buraya üretilir..."
              className="w-full p-2.5 text-xs font-mono border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-slate-50/50 leading-relaxed"
            />
            <p className="text-[11px] text-slate-400">
              Bu metin otomasyon sisteminde yürütücünün e-posta bildirimine ve portalına doğrudan yapıştırılabilir.
            </p>
          </div>

          {/* Action Draft 2: Komisyon Sevk Özeti */}
          <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs space-y-3">
            <div className="flex items-center justify-between border-b border-slate-100 pb-2.5">
              <h4 className="text-xs font-bold uppercase tracking-wider text-slate-800">
                BAP Komisyonuna Sevk Özeti
              </h4>
              <button
                onClick={() => handleCopy(referralSummary, 'referral')}
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors"
              >
                {copiedDraft === 'referral' ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copiedDraft === 'referral' ? 'Kopyalandı' : 'Kopyala'}</span>
              </button>
            </div>
            <textarea
              rows={8}
              value={referralSummary}
              onChange={(e) => setReferralSummary(e.target.value)}
              placeholder="Profesör Kuruluna sunulacak resmi ön inceleme sevk özeti..."
              className="w-full p-2.5 text-xs font-mono border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-slate-50/50 leading-relaxed"
            />
            <p className="text-[11px] text-slate-400">
              Komisyon gündem maddesine eklenecek antetli şekli özet formatıdır.
            </p>
          </div>

          {/* Contextual Q&A Card & Drawer trigger */}
          <div className="bg-meu-900 text-white rounded-xl p-4 shadow-md space-y-3">
            <div className="flex items-center gap-2">
              <MessageSquare className="w-4 h-4 text-cyan-400" />
              <h4 className="text-xs font-bold uppercase tracking-wider">
                Mevzuat ve Dosya Soru-Cevap Paneli
              </h4>
            </div>
            <p className="text-xs text-slate-300 leading-relaxed">
              Bu başvuru ve MEÜ mevzuatına dair aklınıza takılan soruları yapay zekâya sorun; madde atıflı yanıt alın.
            </p>
            <button
              type="button"
              onClick={() => setQaOpen(true)}
              className="w-full py-2 px-3 rounded-lg bg-meu-700 hover:bg-meu-600 text-white text-xs font-bold shadow-xs flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
            >
              <MessageSquare className="w-4 h-4" />
              <span>Soru-Cevap Çekmecesini Aç</span>
            </button>
          </div>
        </div>
      </div>

      {/* Officer Finding Override Modal */}
      <Modal
        isOpen={overrideModalOpen}
        onClose={() => setOverrideModalOpen(false)}
        title="Bulgu İstisna / Override Kararı"
        description="Ön inceleme görevlisi veya birim koordinatörü olarak bu bulguya müdahale edin"
      >
        <div className="space-y-4">
          <div className="p-3 bg-slate-50 rounded-lg border border-slate-200 text-xs">
            <span className="font-mono text-slate-400">{selectedFinding?.rule_code}</span>
            <h5 className="font-bold text-slate-800 mt-0.5">{selectedFinding?.title_tr}</h5>
            <p className="text-slate-600 mt-1">{selectedFinding?.explanation_tr}</p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Override İşlemi
            </label>
            <select
              value={overrideStatus}
              onChange={(e) => setOverrideStatus(e.target.value as any)}
              className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 bg-white"
            >
              <option value="WAIVED">İhlali İstisna Say / Muaf Tut (WAIVED)</option>
              <option value="APPROVED">Bulguyu Onayla ve İadeye Dahil Et (APPROVED)</option>
              <option value="REJECTED">Bulguyu Geçersiz Say / Sil (REJECTED)</option>
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Gerekçe Notu (Zorunlu — Denetim Kaydına Yazılır)
            </label>
            <textarea
              rows={3}
              required
              value={overrideReason}
              onChange={(e) => setOverrideReason(e.target.value)}
              placeholder="Ör. Koordinatör oluru ve Enstitü Yönetim Kurulu ek kararı doğrultusunda bu ihlal muaf tutulmuştur."
              className="w-full p-2.5 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 bg-white"
            />
          </div>

          <div className="flex justify-end gap-2 pt-3 border-t border-slate-100">
            <button
              type="button"
              onClick={() => setOverrideModalOpen(false)}
              className="px-3 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-lg"
            >
              İptal
            </button>
            <button
              type="button"
              disabled={overrideSaving || !overrideReason.trim()}
              onClick={handleSaveOverride}
              className="px-4 py-1.5 text-xs font-bold text-white bg-meu-900 hover:bg-meu-800 disabled:opacity-40 rounded-lg transition-colors cursor-pointer"
            >
              Kararı Kaydet
            </button>
          </div>
        </div>
      </Modal>

      {/* Contextual Q&A Slide-over Drawer */}
      {qaOpen && (
        <div className="fixed inset-0 z-50 overflow-hidden">
          <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs" onClick={() => setQaOpen(false)} />
          <div className="fixed inset-y-0 right-0 max-w-full flex pl-10">
            <div className="w-screen max-w-md bg-white shadow-2xl flex flex-col">
              {/* Drawer Header */}
              <div className="p-4 bg-meu-900 text-white flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <MessageSquare className="w-4 h-4 text-cyan-400" />
                  <div>
                    <h3 className="text-xs font-bold">MEÜ BAP Mevzuat & Dosya Q&A</h3>
                    <p className="text-[10px] text-slate-300 truncate">
                      {submission?.reference_no} • {submission?.project_code}
                    </p>
                  </div>
                </div>
                <button
                  onClick={() => setQaOpen(false)}
                  className="p-1 rounded-lg hover:bg-meu-800 text-slate-300 hover:text-white"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Chat Message List */}
              <div className="flex-1 p-4 overflow-y-auto space-y-3 bg-slate-50">
                {qaMessages.length === 0 ? (
                  <div className="text-center py-12 text-slate-400 text-xs space-y-2">
                    <p className="font-semibold text-slate-600">Henüz soru sorulmadı</p>
                    <p className="text-[11px] max-w-xs mx-auto">
                      Örnek sorular:
                    </p>
                    <div className="space-y-1.5 pt-2">
                      <button
                        onClick={() => setQaQuestion('Bu projede dizüstü bilgisayar kalemi desteklenebilir mi?')}
                        className="block w-full text-left p-2 rounded bg-white border border-slate-200 hover:border-meu-500 text-[11px] text-slate-700"
                      >
                        "Bu projede dizüstü bilgisayar kalemi desteklenebilir mi?"
                      </button>
                      <button
                        onClick={() => setQaQuestion('Tez projesinde enstitü onay belgesi zorunlu mudur?')}
                        className="block w-full text-left p-2 rounded bg-white border border-slate-200 hover:border-meu-500 text-[11px] text-slate-700"
                      >
                        "Tez projesinde enstitü onay belgesi zorunlu mudur?"
                      </button>
                    </div>
                  </div>
                ) : (
                  qaMessages.map((msg, i) => (
                    <div
                      key={i}
                      className={`flex flex-col ${msg.role === 'user' ? 'items-end' : 'items-start'}`}
                    >
                      <div
                        className={`max-w-[85%] rounded-xl p-3 text-xs leading-relaxed ${
                          msg.role === 'user'
                            ? 'bg-meu-900 text-white rounded-br-xs'
                            : 'bg-white border border-slate-200 text-slate-800 shadow-xs rounded-bl-xs'
                        }`}
                      >
                        {msg.content}
                      </div>

                      {/* Citations block for assistant */}
                      {msg.citations && msg.citations.length > 0 && (
                        <div className="mt-1.5 space-y-1 max-w-[85%]">
                          {msg.citations.map((cite, cIdx) => (
                            <div
                              key={cIdx}
                              className="p-2 rounded bg-blue-50 border border-blue-200 text-[11px] text-slate-800"
                            >
                              <div className="font-bold text-meu-800">
                                {cite.regulation_doc_name} — {cite.regulation_article_ref}
                              </div>
                              <div className="italic text-slate-600 mt-0.5">{cite.verbatim_quote}</div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  ))
                )}
                {qaLoading && (
                  <div className="flex items-center gap-2 text-xs text-slate-500 p-2">
                    <div className="w-3.5 h-3.5 border-2 border-meu-600 border-t-transparent rounded-full animate-spin"></div>
                    <span>Mevzuat indeksinde aranıyor ve yanıt üretiliyor...</span>
                  </div>
                )}
              </div>

              {/* Chat Input */}
              <form onSubmit={handleSendQa} className="p-3 bg-white border-t border-slate-200 flex gap-2">
                <input
                  type="text"
                  value={qaQuestion}
                  onChange={(e) => setQaQuestion(e.target.value)}
                  placeholder="Mevzuat veya başvuruya dair bir soru sorun..."
                  className="flex-1 py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white"
                />
                <button
                  type="submit"
                  disabled={qaLoading || !qaQuestion.trim()}
                  className="px-3 py-2 bg-meu-900 text-white rounded-lg text-xs font-semibold hover:bg-meu-800 disabled:opacity-40 transition-colors"
                >
                  <Send className="w-4 h-4" />
                </button>
              </form>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
