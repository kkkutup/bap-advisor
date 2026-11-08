import React from 'react';

interface StatCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  icon: React.ComponentType<{ className?: string }>;
  variant?: 'blue' | 'emerald' | 'amber' | 'rose' | 'slate';
  badge?: string;
}

export const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  subtitle,
  icon: Icon,
  variant = 'blue',
  badge,
}) => {
  const variantMap = {
    blue: {
      bg: 'bg-white',
      border: 'border-slate-200/80',
      iconBg: 'bg-meu-50 text-meu-600 border-meu-100',
    },
    emerald: {
      bg: 'bg-white',
      border: 'border-slate-200/80',
      iconBg: 'bg-emerald-50 text-emerald-600 border-emerald-100',
    },
    amber: {
      bg: 'bg-white',
      border: 'border-slate-200/80',
      iconBg: 'bg-amber-50 text-amber-600 border-amber-100',
    },
    rose: {
      bg: 'bg-white',
      border: 'border-slate-200/80',
      iconBg: 'bg-rose-50 text-rose-600 border-rose-100',
    },
    slate: {
      bg: 'bg-white',
      border: 'border-slate-200/80',
      iconBg: 'bg-slate-100 text-slate-700 border-slate-200',
    },
  };

  const style = variantMap[variant];

  return (
    <div
      className={`rounded-xl border p-5 ${style.bg} ${style.border} shadow-sm hover:shadow transition-shadow relative overflow-hidden`}
    >
      <div className="flex items-start justify-between">
        <div className="space-y-1">
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">{title}</p>
          <div className="flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight text-slate-900">{value}</span>
            {badge && (
              <span className="text-[11px] font-medium px-1.5 py-0.5 rounded bg-slate-100 text-slate-700">
                {badge}
              </span>
            )}
          </div>
          {subtitle && <p className="text-xs text-slate-500 pt-0.5">{subtitle}</p>}
        </div>
        <div className={`p-3 rounded-xl border ${style.iconBg}`}>
          <Icon className="w-5 h-5" />
        </div>
      </div>
    </div>
  );
};
