import React, { useState, useEffect } from 'react';
import { useParams, useSearchParams, Link } from 'react-router-dom';
import { submissionsApi } from '../api/submissionsApi';
import { evaluationApi } from '../api/evaluationApi';
import {
  SubmissionDetailResponse,
  VersionDiffResponse,
  FindingDto,
} from '../types/api';
import { SeverityBadge, SourceBadge } from '../components/ui/Badge';
import {
  GitCompare,
  ArrowRight,
  CheckCircle,
  AlertTriangle,
  FileCheck,
  TrendingDown,
  TrendingUp,
  FilePlus,
  FileMinus,
  FileText,
  Clock,
  Sparkles,
} from 'lucide-react';

export const VersionDiffPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [searchParams, setSearchParams] = useSearchParams();

  const fromV = parseInt(searchParams.get('fromVersion') || '1', 10);
  const toV = parseInt(searchParams.get('toVersion') || '2', 10);

  const [submission, setSubmission] = useState<SubmissionDetailResponse | null>(null);
  const [diff, setDiff] = useState<VersionDiffResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([
      submissionsApi.getSubmissionById(id),
      evaluationApi.getVersionDiff(id, fromV, toV),
    ])
      .then(([sub, diffRes]) => {
        setSubmission(sub);
        setDiff(diffRes);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [id, fromV, toV]);

  const maxVersion = submission?.current_version_no || 2;

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-4 border-meu-600 border-t-transparent rounded-full animate-spin"></div>
          <p className="text-xs text-slate-500 font-medium">Versiyon karşılaştırma analizi hesaplanıyor...</p>
        </div>
      </div>
    );
  }

  if (error || !diff) {
    return (
      <div className="p-6 bg-white rounded-xl border border-slate-200 text-center space-y-3">
        <AlertTriangle className="w-8 h-8 text-rose-500 mx-auto" />
        <h3 className="text-sm font-bold text-slate-900">Versiyon Farkı Alınamadı</h3>
        <p className="text-xs text-slate-500">{error || 'Bu başvuru için geçerli revizyon bulunamadı.'}</p>
        <Link to={`/submissions/${id}`} className="text-xs text-meu-600 font-semibold hover:underline">
          &larr; Başvuru Detayına Dön
        </Link>
      </div>
    );
  }

  const isBudgetDecreased = diff.budget_diff_tl < 0;

  return (
    <div className="space-y-6">
      {/* Top Header & Version Selector */}
      <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-sm font-bold text-meu-900">{submission?.reference_no}</span>
            <span className="px-2 py-0.5 rounded text-xs font-semibold bg-blue-50 text-blue-800 border border-blue-200">
              Revizyon Fark ve İyileştirme Analizi
            </span>
          </div>
          <h2 className="text-base font-bold text-slate-900 mt-1">{submission?.title}</h2>
          <p className="text-xs text-slate-500 mt-0.5">
            v{fromV} ile v{toV} arasındaki bütçe, süre, belge ve mevzuat uyum değişimleri
          </p>
        </div>

        {/* Version Switcher Controls */}
        <div className="flex items-center gap-2 bg-slate-50 p-1.5 rounded-lg border border-slate-200 text-xs">
          <div className="flex items-center gap-1">
            <span className="text-slate-500 font-medium">Kaynak:</span>
            <select
              value={fromV}
              onChange={(e) => setSearchParams({ fromVersion: e.target.value, toVersion: String(toV) })}
              className="py-1 px-2 border border-slate-300 rounded bg-white font-mono font-bold"
            >
              {Array.from({ length: maxVersion }, (_, i) => i + 1).map((v) => (
                <option key={v} value={v}>
                  v{v}
                </option>
              ))}
            </select>
          </div>

          <ArrowRight className="w-4 h-4 text-slate-400" />

          <div className="flex items-center gap-1">
            <span className="text-slate-500 font-medium">Hedef:</span>
            <select
              value={toV}
              onChange={(e) => setSearchParams({ fromVersion: String(fromV), toVersion: e.target.value })}
              className="py-1 px-2 border border-slate-300 rounded bg-white font-mono font-bold"
            >
              {Array.from({ length: maxVersion }, (_, i) => i + 1).map((v) => (
                <option key={v} value={v}>
                  v{v}
                </option>
              ))}
            </select>
          </div>

          <Link
            to={`/submissions/${id}/versions/${toV}/report`}
            className="ml-2 px-3 py-1 rounded-md bg-meu-800 hover:bg-meu-900 text-white font-semibold transition-colors"
          >
            v{toV} Raporunu Aç
          </Link>
        </div>
      </div>

      {/* Summary Delta Banner */}
      <div className="bg-meu-900 text-white p-5 rounded-xl shadow-md space-y-3">
        <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-cyan-300">
          <Sparkles className="w-4 h-4 text-cyan-400" />
          <span>Revizyon Değerlendirme Özeti</span>
        </div>
        <p className="text-xs text-slate-200 leading-relaxed font-sans">{diff.summary_tr}</p>
      </div>

      {/* Numerical & Metric Deltas */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Budget Delta */}
        <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs space-y-1">
          <p className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
            Net Bütçe Değişimi
          </p>
          <div className="flex items-center gap-2">
            <span
              className={`text-xl font-bold font-mono ${
                diff.budget_diff_tl < 0
                  ? 'text-emerald-600'
                  : diff.budget_diff_tl > 0
                  ? 'text-rose-600'
                  : 'text-slate-800'
              }`}
            >
              {diff.budget_diff_tl > 0 ? `+${diff.budget_diff_tl.toLocaleString('tr-TR')} TL` : `${diff.budget_diff_tl.toLocaleString('tr-TR')} TL`}
            </span>
            {isBudgetDecreased ? (
              <TrendingDown className="w-4 h-4 text-emerald-600" />
            ) : (
              <TrendingUp className="w-4 h-4 text-rose-600" />
            )}
          </div>
          <p className="text-[11px] text-slate-500">
            {isBudgetDecreased ? 'Bütçe mevzuat tavanına çekildi' : 'Değişiklik yok / Artış'}
          </p>
        </div>

        {/* Duration Delta */}
        <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs space-y-1">
          <p className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
            Süre Değişimi
          </p>
          <div className="flex items-center gap-2">
            <span className="text-xl font-bold font-mono text-slate-800">
              {diff.duration_diff_months > 0 ? `+${diff.duration_diff_months} Ay` : `${diff.duration_diff_months} Ay`}
            </span>
            <Clock className="w-4 h-4 text-slate-400" />
          </div>
          <p className="text-[11px] text-slate-500">Talep edilen proje süresi farkı</p>
        </div>

        {/* Resolved Findings Count */}
        <div className="bg-white rounded-xl border border-emerald-200 p-4 shadow-xs space-y-1 bg-emerald-50/20">
          <p className="text-[11px] font-bold uppercase tracking-wider text-emerald-700">
            Giderilen İhlaller
          </p>
          <div className="flex items-center gap-2">
            <span className="text-xl font-bold font-mono text-emerald-600">
              {diff.resolved_findings.length} Adet
            </span>
            <CheckCircle className="w-4 h-4 text-emerald-600" />
          </div>
          <p className="text-[11px] text-emerald-700">Başarıyla çözülen mevzuat engeli</p>
        </div>

        {/* Remaining Unresolved Findings */}
        <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs space-y-1">
          <p className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
            Kalan Uyumsuzluklar
          </p>
          <div className="flex items-center gap-2">
            <span
              className={`text-xl font-bold font-mono ${
                diff.unresolved_findings.length > 0 ? 'text-rose-600' : 'text-emerald-600'
              }`}
            >
              {diff.unresolved_findings.length} Adet
            </span>
          </div>
          <p className="text-[11px] text-slate-500">Hâlâ devam eden maddeler</p>
        </div>
      </div>

      {/* 3-Column Compliance Delta Matrix */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Column 1: Giderilen İhlaller (Yeşil) */}
        <div className="bg-white rounded-xl border border-emerald-200 p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-emerald-100 pb-3">
            <div className="flex items-center gap-2">
              <CheckCircle className="w-4 h-4 text-emerald-600" />
              <h3 className="text-xs font-bold uppercase tracking-wider text-emerald-900">
                Giderilen İhlaller ({diff.resolved_findings.length})
              </h3>
            </div>
            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800">
              Yeşil
            </span>
          </div>

          {diff.resolved_findings.length === 0 ? (
            <p className="text-xs text-slate-400 py-6 text-center italic">
              Bu sürümde giderilen ihlal bulunmamaktadır.
            </p>
          ) : (
            <div className="space-y-3">
              {diff.resolved_findings.map((f, i) => (
                <div key={i} className="p-3 rounded-lg border border-emerald-200 bg-emerald-50/40 text-xs space-y-1.5">
                  <div className="flex items-center gap-1.5 font-mono text-[11px] font-bold text-emerald-800">
                    <CheckCircle className="w-3.5 h-3.5 text-emerald-600" />
                    <span>{f.rule_code}</span>
                  </div>
                  <h4 className="font-semibold text-slate-900">{f.title_tr}</h4>
                  <p className="text-[11px] text-slate-600">{f.explanation_tr}</p>
                  <div className="pt-1 text-[10px] text-emerald-700 font-medium">
                    {f.regulation_doc_name} &bull; {f.regulation_article_ref}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Column 2: Devam Eden İhlaller (Kırmızı) */}
        <div className="bg-white rounded-xl border border-rose-200 p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-rose-100 pb-3">
            <div className="flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-rose-600" />
              <h3 className="text-xs font-bold uppercase tracking-wider text-rose-900">
                Devam Eden İhlaller ({diff.unresolved_findings.length})
              </h3>
            </div>
            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-100 text-rose-800">
              Kırmızı
            </span>
          </div>

          {diff.unresolved_findings.length === 0 ? (
            <div className="text-center py-6 text-xs text-emerald-600 font-semibold space-y-1">
              <CheckCircle className="w-6 h-6 mx-auto text-emerald-500" />
              <p>Harika! Hiçbir ihlal kalmadı.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {diff.unresolved_findings.map((f, i) => (
                <div key={i} className="p-3 rounded-lg border border-rose-200 bg-rose-50/40 text-xs space-y-1.5">
                  <div className="flex items-center gap-1.5 font-mono text-[11px] font-bold text-rose-800">
                    <AlertTriangle className="w-3.5 h-3.5 text-rose-600" />
                    <span>{f.rule_code}</span>
                  </div>
                  <h4 className="font-semibold text-slate-900">{f.title_tr}</h4>
                  <p className="text-[11px] text-slate-600">{f.explanation_tr}</p>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Column 3: Yeni Bulgular (Turuncu) */}
        <div className="bg-white rounded-xl border border-amber-200 p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-amber-100 pb-3">
            <div className="flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-amber-600" />
              <h3 className="text-xs font-bold uppercase tracking-wider text-amber-900">
                Yeni Bulgular ({diff.new_findings.length})
              </h3>
            </div>
            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-100 text-amber-800">
              Turuncu
            </span>
          </div>

          {diff.new_findings.length === 0 ? (
            <p className="text-xs text-slate-400 py-6 text-center italic">
              Revizyonda yeni ihlal veya kural sorunu tespit edilmedi.
            </p>
          ) : (
            <div className="space-y-3">
              {diff.new_findings.map((f, i) => (
                <div key={i} className="p-3 rounded-lg border border-amber-200 bg-amber-50/40 text-xs space-y-1.5">
                  <div className="flex items-center gap-1.5 font-mono text-[11px] font-bold text-amber-800">
                    <AlertTriangle className="w-3.5 h-3.5 text-amber-600" />
                    <span>{f.rule_code}</span>
                  </div>
                  <h4 className="font-semibold text-slate-900">{f.title_tr}</h4>
                  <p className="text-[11px] text-slate-600">{f.explanation_tr}</p>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Line Item & Document Changes Detail Table */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Modified Budget Line Items */}
        <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-3">
          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-800">
            Değiştirilen Bütçe Kalemleri ({diff.budget_items_modified.length})
          </h3>
          {diff.budget_items_modified.length === 0 ? (
            <p className="text-xs text-slate-400 italic py-4">Kalem tutarlarında değişiklik yok.</p>
          ) : (
            <div className="space-y-2">
              {diff.budget_items_modified.map((item, idx) => (
                <div key={idx} className="p-3 rounded-lg border border-slate-200 bg-slate-50/50 text-xs space-y-1">
                  <div className="font-semibold text-slate-900">{item.item_name}</div>
                  <div className="flex items-center gap-2 font-mono text-[11px]">
                    <span className="line-through text-slate-400">{item.old_total_tl.toLocaleString('tr-TR')} TL</span>
                    <ArrowRight className="w-3.5 h-3.5 text-slate-400" />
                    <span className="font-bold text-emerald-700">{item.new_total_tl.toLocaleString('tr-TR')} TL</span>
                  </div>
                  {item.reason_tr && <p className="text-[11px] text-slate-500 italic">{item.reason_tr}</p>}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Document Changes */}
        <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-3">
          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-800">
            Belge Paketi Değişiklikleri
          </h3>
          <div className="space-y-3 text-xs">
            {diff.documents_added.length > 0 && (
              <div className="space-y-1">
                <p className="text-[11px] font-semibold text-emerald-700 flex items-center gap-1">
                  <FilePlus className="w-3.5 h-3.5" />
                  <span>Yeni Eklenen Belgeler:</span>
                </p>
                <ul className="list-disc list-inside text-slate-700 font-mono text-[11px] space-y-0.5">
                  {diff.documents_added.map((d, i) => (
                    <li key={i}>{d}</li>
                  ))}
                </ul>
              </div>
            )}

            {diff.documents_removed.length > 0 && (
              <div className="space-y-1">
                <p className="text-[11px] font-semibold text-rose-600 flex items-center gap-1">
                  <FileMinus className="w-3.5 h-3.5" />
                  <span>Kaldırılan / Yenilenen Eski Belgeler:</span>
                </p>
                <ul className="list-disc list-inside text-slate-500 font-mono text-[11px] space-y-0.5">
                  {diff.documents_removed.map((d, i) => (
                    <li key={i}>{d}</li>
                  ))}
                </ul>
              </div>
            )}

            {diff.documents_added.length === 0 && diff.documents_removed.length === 0 && (
              <p className="text-xs text-slate-400 italic py-4">Belge listesinde ekleme veya çıkarma yok.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
