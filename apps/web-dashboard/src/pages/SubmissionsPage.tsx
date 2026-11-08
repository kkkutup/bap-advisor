import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { submissionsApi, SubmissionsFilterParams } from '../api/submissionsApi';
import {
  SubmissionDetailResponse,
  SubmissionStatus,
  SubmissionType,
} from '../types/api';
import { StatusBadge, SubmissionTypeBadge } from '../components/ui/Badge';
import {
  Search,
  Filter,
  FilePlus,
  FileSearch,
  FileText,
  GitCompare,
  RotateCcw,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';

export const SubmissionsPage: React.FC = () => {
  const [submissions, setSubmissions] = useState<SubmissionDetailResponse[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);

  // Filters
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<SubmissionStatus | ''>('');
  const [type, setType] = useState<SubmissionType | ''>('');
  const [projectCode, setProjectCode] = useState('');

  const fetchSubmissions = async () => {
    setLoading(true);
    try {
      const params: SubmissionsFilterParams = {
        page,
        size: 10,
        search: search.trim() || undefined,
        status: (status as SubmissionStatus) || undefined,
        submissionType: (type as SubmissionType) || undefined,
        projectCode: projectCode.trim() || undefined,
      };
      const res = await submissionsApi.listSubmissions(params);
      setSubmissions(res.content);
      setTotalElements(res.total_elements);
      setTotalPages(res.total_pages);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSubmissions();
  }, [page, status, type, projectCode]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    fetchSubmissions();
  };

  const handleResetFilters = () => {
    setSearch('');
    setStatus('');
    setType('');
    setProjectCode('');
    setPage(0);
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900 tracking-tight">
            Başvuru Arşivi ve İnceleme Kuyruğu
          </h2>
          <p className="text-xs text-slate-500 mt-1">
            Mevzuat filtreleme, OCR veri doğrulama ve ön inceleme raporlarına erişim
          </p>
        </div>
        <Link
          to="/submissions/new"
          className="inline-flex items-center gap-2 px-3.5 py-2 rounded-lg bg-meu-900 hover:bg-meu-800 text-white text-xs font-semibold shadow-xs transition-colors"
        >
          <FilePlus className="w-4 h-4" />
          <span>Yeni Başvuru Yükle</span>
        </Link>
      </div>

      {/* Filter Toolbar */}
      <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs space-y-3">
        <form onSubmit={handleSearchSubmit} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
          {/* Search Input */}
          <div className="lg:col-span-2 relative">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Referans No, Yürütücü Adı veya Proje Başlığı..."
              className="w-full pl-9 pr-3 py-2 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white"
            />
          </div>

          {/* Status Dropdown */}
          <div>
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as any);
                setPage(0);
              }}
              className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white text-slate-700"
            >
              <option value="">Tüm Durumlar</option>
              <option value="EXTRACTION_REVIEW">OCR Veri Doğrulama</option>
              <option value="OFFICER_REVIEW">Memur İncelemesinde</option>
              <option value="REVISION_REQUESTED">Revizyon İstendi (İade)</option>
              <option value="COMMISSION_REFERRED">Komisyona Sevk Edildi</option>
              <option value="REJECTED_FORMAL">Şekli Ret (Mevzuat Engeli)</option>
              <option value="OCR_PROCESSING">OCR İşleniyor</option>
              <option value="DRAFT">Taslak</option>
            </select>
          </div>

          {/* Submission Type Dropdown */}
          <div>
            <select
              value={type}
              onChange={(e) => {
                setType(e.target.value as any);
                setPage(0);
              }}
              className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white text-slate-700"
            >
              <option value="">Tüm Talep Türleri</option>
              <option value="NEW_PROJECT">Yeni Proje</option>
              <option value="OPEN_ACCESS">Açık Erişim (WoS)</option>
              <option value="ADDITIONAL_BUDGET">Ek Bütçe</option>
              <option value="ADDITIONAL_TIME">Ek Süre</option>
              <option value="PROCUREMENT_ADVANCE">Satın Alma / Avans</option>
            </select>
          </div>

          {/* Project Code Dropdown */}
          <div className="flex items-center gap-2">
            <select
              value={projectCode}
              onChange={(e) => {
                setProjectCode(e.target.value);
                setPage(0);
              }}
              className="w-full py-2 px-3 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 bg-white text-slate-700 font-mono"
            >
              <option value="">Tüm Kodlar</option>
              <option value="TP1">TP1 (Lisans - 30.000 TL)</option>
              <option value="TP2">TP2 (Yüksek Lisans - 125.000 TL)</option>
              <option value="TP3">TP3 (Doktora - 210.000 TL)</option>
              <option value="AP1">AP1 (Kariyer - 250.000 TL)</option>
              <option value="AP2">AP2 (Bireysel - 300.000 TL)</option>
              <option value="AP3">AP3 (Öncelikli - 500.000 TL)</option>
              <option value="AP4">AP4 (Kapsamlı - 800.000 TL)</option>
              <option value="AP7">AP7 (Hızlı Destek - 100.000 TL)</option>
              <option value="AP9">AP9 (Yarışma - 50.000 TL)</option>
              <option value="WOS_Q1_TOP10">WoS Q1 Top %10</option>
              <option value="WOS_Q1_OTHER">WoS Q1 Diğer</option>
              <option value="WOS_Q2">WoS Q2</option>
            </select>

            {(search || status || type || projectCode) && (
              <button
                type="button"
                onClick={handleResetFilters}
                title="Filtreleri Temizle"
                className="p-2 border border-slate-300 rounded-lg hover:bg-slate-100 text-slate-500 transition-colors shrink-0"
              >
                <RotateCcw className="w-4 h-4" />
              </button>
            )}
          </div>
        </form>
      </div>

      {/* Submissions Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-slate-200 text-slate-500 font-semibold text-[11px] bg-slate-50/70">
                <th className="py-3 px-4">REFERANS NO</th>
                <th className="py-3 px-4">TÜR VE KOD</th>
                <th className="py-3 px-4">PROJE BAŞLIĞI</th>
                <th className="py-3 px-4">YÜRÜTÜCÜ / BİRİM</th>
                <th className="py-3 px-4">DURUM</th>
                <th className="py-3 px-4">VERSİYON</th>
                <th className="py-3 px-4">TARİH</th>
                <th className="py-3 px-4 text-right">İŞLEMLER</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={8} className="py-12 text-center text-slate-400">
                    Başvurular listeleniyor...
                  </td>
                </tr>
              ) : submissions.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-12 text-center text-slate-400">
                    Kriterlere uygun başvuru bulunamadı.
                  </td>
                </tr>
              ) : (
                submissions.map((sub) => {
                  const currentV = sub.current_version_no;
                  return (
                    <tr key={sub.id} className="hover:bg-slate-50/80 transition-colors">
                      <td className="py-3.5 px-4 font-mono font-bold text-slate-900">
                        {sub.reference_no}
                        {sub.external_bap2_id && (
                          <span className="block text-[10px] text-slate-400 font-normal">
                            bap2: {sub.external_bap2_id}
                          </span>
                        )}
                      </td>
                      <td className="py-3.5 px-4">
                        <SubmissionTypeBadge type={sub.submission_type} projectCode={sub.project_code} />
                      </td>
                      <td className="py-3.5 px-4 max-w-xs">
                        <div className="font-semibold text-slate-800 line-clamp-1" title={sub.title}>
                          {sub.title}
                        </div>
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="font-medium text-slate-800">
                          {sub.applicant_title ? `${sub.applicant_title} ` : ''}
                          {sub.applicant_name}
                        </div>
                        <div className="text-[11px] text-slate-500 truncate">{sub.department}</div>
                      </td>
                      <td className="py-3.5 px-4">
                        <StatusBadge status={sub.status} />
                      </td>
                      <td className="py-3.5 px-4">
                        <span className="px-2 py-0.5 rounded bg-slate-100 text-slate-700 font-mono font-semibold text-[11px]">
                          v{sub.current_version_no}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-slate-500 text-[11px]">
                        {new Date(sub.updated_at || sub.created_at).toLocaleDateString('tr-TR')}
                      </td>
                      <td className="py-3.5 px-4 text-right">
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
                              <FileText className="w-3.5 h-3.5" />
                              <span>Rapor</span>
                            </Link>
                          )}

                          {currentV > 1 && (
                            <Link
                              to={`/submissions/${sub.id}/diff?fromVersion=1&toVersion=${currentV}`}
                              className="inline-flex items-center gap-1 px-2 py-1 rounded-md bg-slate-100 hover:bg-slate-200 text-slate-700 text-[11px] font-medium transition-colors"
                              title="v1 <-> v2 Karşılaştırma"
                            >
                              <GitCompare className="w-3 h-3 text-slate-500" />
                              <span>Diff</span>
                            </Link>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Bar */}
        <div className="flex items-center justify-between px-4 py-3 border-t border-slate-200 bg-slate-50/50 text-xs text-slate-600">
          <div>
            Toplam <strong>{totalElements}</strong> başvuru kaydı (Sayfa {page + 1} / {totalPages || 1})
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="p-1.5 rounded-lg border border-slate-300 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-100 text-slate-600"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="p-1.5 rounded-lg border border-slate-300 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-100 text-slate-600"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
