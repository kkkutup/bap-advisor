import React from 'react';
import {
  SubmissionStatus,
  FindingSeverity,
  SubmissionType,
  DocumentRole,
  FindingSource,
} from '../../types/api';

interface BadgeProps {
  children?: React.ReactNode;
  className?: string;
  variant?: 'slate' | 'blue' | 'emerald' | 'amber' | 'rose' | 'purple' | 'cyan';
}

export const Badge: React.FC<BadgeProps> = ({
  children,
  className = '',
  variant = 'slate',
}) => {
  const variantStyles = {
    slate: 'bg-slate-100 text-slate-700 border-slate-200',
    blue: 'bg-blue-50 text-blue-700 border-blue-200',
    emerald: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    amber: 'bg-amber-50 text-amber-800 border-amber-200',
    rose: 'bg-rose-50 text-rose-700 border-rose-200',
    purple: 'bg-purple-50 text-purple-700 border-purple-200',
    cyan: 'bg-cyan-50 text-cyan-850 border-cyan-200',
  };

  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border ${variantStyles[variant]} ${className}`}
    >
      {children}
    </span>
  );
};

export const StatusBadge: React.FC<{ status: SubmissionStatus }> = ({ status }) => {
  switch (status) {
    case 'DRAFT':
      return <Badge variant="slate">Taslak</Badge>;
    case 'OCR_PROCESSING':
      return <Badge variant="blue">OCR İşleniyor</Badge>;
    case 'OCR_FAILED':
      return <Badge variant="rose">OCR Başarısız</Badge>;
    case 'EXTRACTION_REVIEW':
      return <Badge variant="amber">OCR Veri Doğrulama</Badge>;
    case 'EVALUATING':
      return <Badge variant="purple">Denetim Çalışıyor</Badge>;
    case 'OFFICER_REVIEW':
      return <Badge variant="blue">Memur İncelemesinde</Badge>;
    case 'REVISION_REQUESTED':
      return <Badge variant="rose">Revizyon İstendi (İade)</Badge>;
    case 'COMMISSION_REFERRED':
      return <Badge variant="emerald">Komisyona Sevk Edildi</Badge>;
    case 'REJECTED_FORMAL':
      return <Badge variant="rose">Şekli Ret (Mevzuat Engeli)</Badge>;
    default:
      return <Badge variant="slate">{status}</Badge>;
  }
};

export const SeverityBadge: React.FC<{ severity: FindingSeverity }> = ({ severity }) => {
  switch (severity) {
    case 'COMPLIANT':
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800 border border-emerald-300">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-600"></span>
          Uygun
        </span>
      );
    case 'VIOLATION':
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-rose-100 text-rose-800 border border-rose-300">
          <span className="w-1.5 h-1.5 rounded-full bg-rose-600"></span>
          Kural İhlali
        </span>
      );
    case 'MISSING_DOCUMENT':
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-100 text-amber-800 border border-amber-300">
          <span className="w-1.5 h-1.5 rounded-full bg-amber-600"></span>
          Eksik Belge
        </span>
      );
    case 'AMBIGUOUS_COMMISSION':
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-purple-100 text-purple-850 border border-purple-300">
          <span className="w-1.5 h-1.5 rounded-full bg-purple-600"></span>
          Komisyon Takdiri
        </span>
      );
    default:
      return <Badge>{severity}</Badge>;
  }
};

export const SubmissionTypeBadge: React.FC<{ type: SubmissionType; projectCode?: string | null }> = ({
  type,
  projectCode,
}) => {
  const labelMap: Record<SubmissionType, string> = {
    NEW_PROJECT: 'Yeni Proje',
    OPEN_ACCESS: 'Açık Erişim',
    ADDITIONAL_BUDGET: 'Ek Bütçe',
    ADDITIONAL_TIME: 'Ek Süre',
    PROCUREMENT_ADVANCE: 'Satın Alma / Avans',
  };

  return (
    <div className="inline-flex items-center gap-1.5">
      <Badge variant="blue">{labelMap[type] || type}</Badge>
      {projectCode && (
        <span className="px-1.5 py-0.5 bg-slate-200 text-slate-800 text-xs font-mono font-bold rounded">
          {projectCode}
        </span>
      )}
    </div>
  );
};

export const DocumentRoleBadge: React.FC<{ role: DocumentRole }> = ({ role }) => {
  const map: Record<DocumentRole, { label: string; variant: BadgeProps['variant'] }> = {
    APPLICATION_FORM: { label: 'Başvuru Formu', variant: 'blue' },
    BUDGET_TABLE: { label: 'Bütçe Tablosu', variant: 'cyan' },
    PROFORMA_INVOICE: { label: 'Proforma Fatura', variant: 'purple' },
    TECHNICAL_SPEC: { label: 'Teknik Şartname', variant: 'slate' },
    ETHICS_APPROVAL: { label: 'Etik Kurul Onayı', variant: 'amber' },
    THESIS_PROPOSAL: { label: 'Tez Öneri Kararı', variant: 'emerald' },
    SIGNED_PETITION: { label: 'İmzalı Dilekçe', variant: 'blue' },
    PUBLICATION_PROOF: { label: 'Yayın & WoS Kanıtı', variant: 'emerald' },
    OTHER: { label: 'Diğer Ek Belge', variant: 'slate' },
  };

  const item = map[role] || { label: role, variant: 'slate' };
  return <Badge variant={item.variant}>{item.label}</Badge>;
};

export const SourceBadge: React.FC<{ source: FindingSource }> = ({ source }) => {
  if (source === 'DETERMINISTIC_RULE') {
    return (
      <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[11px] font-mono font-medium bg-slate-100 text-slate-750 border border-slate-300">
        <span className="text-blue-600 font-bold">#</span> Deterministik Kural
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[11px] font-mono font-medium bg-cyan-50 text-cyan-850 border border-cyan-300">
      <span className="text-cyan-600 font-bold">§</span> RAG Mevzuat
    </span>
  );
};
