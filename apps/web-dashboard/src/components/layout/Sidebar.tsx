import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  LayoutDashboard,
  FileSpreadsheet,
  FilePlus2,
  BookOpenCheck,
  Settings,
  Scale,
  ExternalLink,
} from 'lucide-react';

export const Sidebar: React.FC = () => {
  const { role } = useAuth();

  const isCoordinatorOrAdmin = role === 'COORDINATOR' || role === 'ADMIN';

  const navItems = [
    {
      to: '/',
      label: 'Özet Gösterge Paneli',
      icon: LayoutDashboard,
      exact: true,
    },
    {
      to: '/submissions',
      label: 'Başvuru Kuyruğu ve Arşiv',
      icon: FileSpreadsheet,
    },
    {
      to: '/submissions/new',
      label: 'Yeni Başvuru Girişi',
      icon: FilePlus2,
    },
  ];

  const adminItems = [
    {
      to: '/admin/regulations',
      label: 'Mevzuat ve 2026 Limitleri',
      icon: Scale,
    },
    {
      to: '/admin/settings',
      label: 'Sistem ve Entegrasyon',
      icon: Settings,
    },
  ];

  return (
    <aside className="w-64 bg-slate-900 text-slate-300 flex flex-col shrink-0 min-h-[calc(100vh-5.5rem)] border-r border-slate-800">
      <div className="p-4 space-y-6 flex-1">
        <div>
          <p className="px-3 text-[10px] font-bold uppercase tracking-wider text-slate-400 mb-2">
            İnceleme ve Süreç
          </p>
          <nav className="space-y-1">
            {navItems.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.exact}
                  className={({ isActive }) =>
                    `flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-medium transition-colors ${
                      isActive
                        ? 'bg-meu-700 text-white shadow-xs font-semibold'
                        : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                    }`
                  }
                >
                  <Icon className="w-4 h-4 shrink-0" />
                  <span>{item.label}</span>
                </NavLink>
              );
            })}
          </nav>
        </div>

        {isCoordinatorOrAdmin && (
          <div>
            <p className="px-3 text-[10px] font-bold uppercase tracking-wider text-slate-400 mb-2">
              Koordinatör & Yönetim
            </p>
            <nav className="space-y-1">
              {adminItems.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    className={({ isActive }) =>
                      `flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-medium transition-colors ${
                        isActive
                          ? 'bg-meu-700 text-white shadow-xs font-semibold'
                          : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                      }`
                    }
                  >
                    <Icon className="w-4 h-4 shrink-0" />
                    <span>{item.label}</span>
                  </NavLink>
                );
              })}
            </nav>
          </div>
        )}

        <div className="pt-2">
          <p className="px-3 text-[10px] font-bold uppercase tracking-wider text-slate-400 mb-2">
            Mevzuat ve Dış Bağlantılar
          </p>
          <div className="space-y-1">
            <a
              href="https://bap.mersin.edu.tr"
              target="_blank"
              rel="noreferrer"
              className="flex items-center justify-between px-3 py-2 rounded-lg text-xs text-slate-300 hover:bg-slate-800 hover:text-white transition-colors"
            >
              <div className="flex items-center gap-3">
                <BookOpenCheck className="w-4 h-4 text-emerald-400 shrink-0" />
                <span>bap.mersin.edu.tr</span>
              </div>
              <ExternalLink className="w-3 h-3 text-slate-400" />
            </a>
            <a
              href="https://bap2.mersin.edu.tr"
              target="_blank"
              rel="noreferrer"
              className="flex items-center justify-between px-3 py-2 rounded-lg text-xs text-slate-300 hover:bg-slate-800 hover:text-white transition-colors"
            >
              <div className="flex items-center gap-3">
                <ExternalLink className="w-4 h-4 text-cyan-400 shrink-0" />
                <span>bap2 Otomasyonu</span>
              </div>
              <ExternalLink className="w-3 h-3 text-slate-400" />
            </a>
          </div>
        </div>
      </div>

      {/* Footer Info in Sidebar */}
      <div className="p-4 border-t border-slate-800 text-[11px] text-slate-400 space-y-1 bg-slate-950/40">
        <p className="font-semibold text-slate-300">MEÜ BAP Koordinasyon Birimi</p>
        <p>Mevzuat Ön İnceleme Sistemi</p>
        <p className="text-[10px] text-slate-400">2026 Mali Yılı Uygulama Esasları</p>
      </div>
    </aside>
  );
};
