import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { analyticsApi } from '../api/analyticsApi';
import { submissionsApi } from '../api/submissionsApi';
import { AnalyticsOverviewResponse, SubmissionDetailResponse } from '../types/api';
import { StatCard } from '../components/ui/StatCard';
import { StatusBadge, SubmissionTypeBadge } from '../components/ui/Badge';
import {
  FileText,
  Clock,
  RotateCcw,
  Send,
  CheckCircle2,
  AlertTriangle,
  ArrowRight,
  TrendingUp,
  FileSearch,
  ExternalLink,
} from 'lucide-react';

export const DashboardPage: React.FC = () => {
  const [analytics, setAnalytics] = useState<AnalyticsOverviewResponse | null>(null);
  const [queue, setQueue] = useState<SubmissionDetailResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      analyticsApi.getAnalyticsOverview().catch(() => ({
        total_submissions_count: 0,
        status_breakdown: {},
        submission_type_breakdown: {},
        top_violated_articles: [],
        average_review_hours: 0,
      })),
      submissionsApi.listSubmissions({ size: 6 }).catch(() => ({
        content: [],
        total_elements: 0,
        total_pages: 0,
        page_number: 0,
        page_size: 6,
      })),
    ])
      .then(([a, subs]) => {
        setAnalytics(a);
        setQueue(subs.content || []);
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading || !analytics) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-4 border-meu-600 border-t-transparent rounded-full animate-spin"></div>
          <p className="text-xs text-slate-500 font-medium">Birim verileri ve istatistikler yükleniyor...</p>
        </div>
      </div>
    );
  }

  const pendingReviewCount =
    (analytics.status_breakdown.OFFICER_REVIEW || 0) +
    (analytics.status_breakdown.EXTRACTION_REVIEW || 0);
  const revisionCount = analytics.status_breakdown.REVISION_REQUESTED || 0;
  const referredCount = analytics.status_breakdown.COMMISSION_REFERRED || 0;
  const totalSubmissions = analytics.total_submissions_count || 1;
  const complianceRate = Math.round((referredCount / (referredCount + revisionCount || 1)) * 100);

  const typeLabels: Record<string, string> = {
    NEW_PROJECT: 'Yeni Proje Başvurusu',
    OPEN_ACCESS: 'Açık Erişim (WoS Q1/Q2)',
    ADDITIONAL_BUDGET: 'Ek Bütçe Talebi',
    ADDITIONAL_TIME: 'Ek Süre Talebi',
    PROCUREMENT_ADVANCE: 'Satın Alma / Avans',
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900 tracking-tight">
            Ön İnceleme ve Süreç Gösterge Paneli
          </h2>
          <p className="text-xs text-slate-500 mt-1">
            Mersin Üniversitesi 2026 BAP Mevzuatı ve Otomasyon Ön İnceleme Durumu
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Link
            to="/submissions/new"
            className="inline-flex items-center gap-2 px-3.5 py-2 rounded-lg bg-meu-900 hover:bg-meu-800 text-white text-xs font-semibold shadow-xs transition-colors"
          >
            <FileText className="w-4 h-4" />
            <span>Yeni Başvuru Başlat</span>
          </Link>
        </div>
      </div>

      {/* Top 5 KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
        <StatCard
          title="Toplam Başvuru"
          value={totalSubmissions}
          subtitle="2026 Dönemi Başvuruları"
          icon={FileText}
          variant="slate"
        />
        <StatCard
          title="Ön İnceleme Bekleyen"
          value={pendingReviewCount}
          subtitle="İnsan doğrulamalı işlem"
          icon={Clock}
          variant="amber"
          badge="İşlemde"
        />
        <StatCard
          title="Revizyon İstenen"
          value={revisionCount}
          subtitle="Eksik/ihlal ile iade edildi"
          icon={RotateCcw}
          variant="rose"
        />
        <StatCard
          title="Kurula Sevk Edilen"
          value={referredCount}
          subtitle="Şekli mevzuata uygun"
          icon={Send}
          variant="emerald"
        />
        <StatCard
          title="Mevzuat Uyum Oranı"
          value={`%${complianceRate}`}
          subtitle={`Ortalama: ${analytics.average_review_hours} saat / dosya`}
          icon={TrendingUp}
          variant="blue"
        />
      </div>

      {/* Two Column Section: Breakdowns & Top Violated Articles */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left: Request Type Distribution */}
        <div className="lg:col-span-1 bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-slate-100 pb-3">
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700">
              Talep Türüne Göre Dağılım
            </h3>
            <span className="text-xs text-slate-400 font-medium">Toplam {totalSubmissions}</span>
          </div>

          <div className="space-y-3">
            {Object.entries(analytics.submission_type_breakdown).map(([typeKey, count]) => {
              const pct = Math.round((count / totalSubmissions) * 100);
              return (
                <div key={typeKey} className="space-y-1">
                  <div className="flex justify-between text-xs">
                    <span className="font-medium text-slate-700">{typeLabels[typeKey] || typeKey}</span>
                    <span className="text-slate-500 font-semibold">
                      {count} <span className="text-[11px] font-normal text-slate-400">(%{pct})</span>
                    </span>
                  </div>
                  <div className="w-full bg-slate-100 rounded-full h-2 overflow-hidden">
                    <div
                      className="bg-meu-600 h-2 rounded-full transition-all"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>

          <div className="pt-2 border-t border-slate-100 text-[11px] text-slate-500 flex items-center justify-between">
            <span>En yüksek talep: Yeni Projeler (%52)</span>
            <Link to="/submissions" className="text-meu-600 hover:underline font-semibold">
              Tümünü Filtrele &rarr;
            </Link>
          </div>
        </div>

        {/* Middle/Right: Top Violated Articles & 2026 Limits Overview */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-slate-100 pb-3">
            <div className="flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-rose-600" />
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700">
                En Sık İhlal Edilen MEÜ Mevzuat Maddeleri
              </h3>
            </div>
            <Link to="/admin/regulations" className="text-xs text-meu-600 hover:underline font-medium">
              Mevzuat Bilgi Tabanını Aç &rarr;
            </Link>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-slate-100 text-slate-400 font-semibold text-[11px]">
                  <th className="pb-2">MEVZUAT MADDE VE DAYANAK</th>
                  <th className="pb-2">KAYNAK DOKÜMAN</th>
                  <th className="pb-2 text-right">İHLAL SAYISI</th>
                  <th className="pb-2 text-right">ÖN İNCELEME ETKİSİ</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {analytics.top_violated_articles.map((item, idx) => (
                  <tr key={idx} className="hover:bg-slate-50/70 transition-colors">
                    <td className="py-2.5 font-semibold text-slate-800">
                      <span className="font-mono text-meu-700 font-bold mr-1.5">{item.article_ref}</span>
                    </td>
                    <td className="py-2.5 text-slate-600">{item.doc_name}</td>
                    <td className="py-2.5 text-right font-bold text-rose-600">
                      {item.violation_count} adet
                    </td>
                    <td className="py-2.5 text-right">
                      <span className="px-2 py-0.5 rounded text-[11px] font-medium bg-rose-50 text-rose-700 border border-rose-200">
                        Doğrudan İade
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Actionable Officer Queue Table */}
      <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <div>
            <h3 className="text-sm font-bold text-slate-900">
              Görevli İş Listesi (Öncelikli Başvurular)
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              İnsan doğrulamalı OCR kontrolü ve mevzuat denetimi bekleyen son kayıtlar
            </p>
          </div>
          <Link
            to="/submissions"
            className="inline-flex items-center gap-1.5 text-xs text-meu-600 hover:text-meu-700 font-semibold"
          >
            <span>Tüm Kuyruğu Gör ({totalSubmissions})</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-slate-200/80 text-slate-500 font-semibold text-[11px] bg-slate-50/60">
                <th className="py-2.5 px-3">REFERANS NO</th>
                <th className="py-2.5 px-3">TÜR VE KOD</th>
                <th className="py-2.5 px-3">PROJE BAŞLIĞI VE YÜRÜTÜCÜ</th>
                <th className="py-2.5 px-3">DURUM</th>
                <th className="py-2.5 px-3">GÜNCELLEME</th>
                <th className="py-2.5 px-3 text-right">EYLEMLER</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {queue.map((sub) => {
                const currentV = sub.current_version_no;
                return (
                  <tr key={sub.id} className="hover:bg-slate-50 transition-colors">
                    <td className="py-3 px-3 font-mono font-bold text-slate-900">
                      {sub.reference_no}
                      {sub.external_bap2_id && (
                        <span className="block text-[10px] text-slate-400 font-normal">
                          {sub.external_bap2_id}
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-3">
                      <SubmissionTypeBadge type={sub.submission_type} projectCode={sub.project_code} />
                      <span className="block text-[10px] font-mono text-slate-500 mt-0.5">
                        Versiyon v{sub.current_version_no}
                      </span>
                    </td>
                    <td className="py-3 px-3 max-w-sm">
                      <div className="font-semibold text-slate-900 truncate" title={sub.title}>
                        {sub.title}
                      </div>
                      <div className="text-[11px] text-slate-500 truncate">
                        {sub.applicant_title ? `${sub.applicant_title} ` : ''}
                        {sub.applicant_name} • {sub.department}
                      </div>
                    </td>
                    <td className="py-3 px-3">
                      <StatusBadge status={sub.status} />
                    </td>
                    <td className="py-3 px-3 text-slate-500 text-[11px]">
                      {new Date(sub.updated_at || sub.created_at).toLocaleDateString('tr-TR', {
                        day: '2-digit',
                        month: '2-digit',
                        year: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </td>
                    <td className="py-3 px-3 text-right">
                      <div className="inline-flex items-center gap-1.5">
                        {sub.status === 'EXTRACTION_REVIEW' ? (
                          <Link
                            to={`/submissions/${sub.id}/versions/${currentV}/review`}
                            className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-amber-500 hover:bg-amber-600 text-white text-[11px] font-semibold transition-colors"
                          >
                            <FileSearch className="w-3.5 h-3.5" />
                            <span>OCR Doğrula</span>
                          </Link>
                        ) : (
                          <Link
                            to={`/submissions/${sub.id}/versions/${currentV}/report`}
                            className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-meu-800 hover:bg-meu-900 text-white text-[11px] font-semibold transition-colors"
                          >
                            <span>Raporu Aç</span>
                          </Link>
                        )}
                        {currentV > 1 && (
                          <Link
                            to={`/submissions/${sub.id}/diff?fromVersion=1&toVersion=${currentV}`}
                            className="px-2 py-1 rounded-md bg-slate-100 hover:bg-slate-200 text-slate-700 text-[11px] font-medium"
                            title="v1 <-> v2 Revizyon Farkı"
                          >
                            Diff
                          </Link>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
