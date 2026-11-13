import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useApiStatus } from '../../context/ApiStatusContext';
import { providerApi } from '../../api/providerApi';
import { ProviderStatusResponse } from '../../types/api';
import {
  ShieldAlert,
  Server,
  Cpu,
  LogOut,
  ChevronDown,
  UserCheck,
  RefreshCw,
  Sliders,
} from 'lucide-react';
import { Link } from 'react-router-dom';

export const Header: React.FC = () => {
  const { user, role, logout, quickLogin } = useAuth();
  const { isOfflineMode, toggleOfflineMode, isBackendReachable } = useApiStatus();
  const [providerStatus, setProviderStatus] = useState<ProviderStatusResponse | null>(null);
  const [userDropdownOpen, setUserDropdownOpen] = useState(false);

  useEffect(() => {
    providerApi
      .getProviderStatus()
      .then((s) => setProviderStatus(s))
      .catch(() => null);
  }, []);

  const roleNameMap: Record<string, string> = {
    ADMIN: 'Sistem Yöneticisi',
    COORDINATOR: 'BAP Koordinatörü',
    OFFICER: 'Ön İnceleme Memuru',
    COMMISSION_VIEWER: 'Komisyon Üyesi (Kurul)',
  };

  return (
    <header className="sticky top-0 z-40 bg-white border-b border-slate-200 shadow-xs">
      {/* Institutional Legal & Scope Guardrail Banner */}
      <div className="bg-meu-900 text-white px-4 py-1.5 text-[12px] font-medium flex items-center justify-between border-b border-meu-950">
        <div className="flex items-center gap-2 max-w-5xl">
          <ShieldAlert className="w-4 h-4 text-amber-400 shrink-0" />
          <span className="leading-tight">
            <strong>Mersin Üniversitesi BAP Koordinasyon Birimi:</strong> Bu sistem yalnızca BAP mevzuatı kapsamında idari, şekli ve mali uygunluk ön incelemesi yapar; bilimsel değerlendirme BAP Komisyonu (Profesör Kurulu) yetkisindedir.
          </span>
        </div>
        <div className="hidden lg:flex items-center gap-3 shrink-0 text-[11px] text-slate-300">
          <span>2026 Mali Yılı Limitleri Aktif</span>
          <span className="w-1 h-1 rounded-full bg-emerald-400"></span>
          <span>H-Cetveli Uyumlu</span>
        </div>
      </div>

      {/* Main Bar */}
      <div className="px-4 sm:px-6 h-14 flex items-center justify-between gap-4">
        {/* Left: Institution Branding */}
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-meu-900 flex items-center justify-center text-white font-bold text-xs shadow-xs">
            BAP
          </div>
          <div>
            <h1 className="text-sm font-bold text-slate-900 tracking-tight leading-none">
              MEÜ BAP Mevzuat Uygunluk ve Ön İnceleme Sistemi
            </h1>
            <p className="text-[11px] text-slate-500 font-medium mt-0.5">
              Mersin Üniversitesi Bilimsel Araştırma Projeleri Koordinasyon Birimi
            </p>
          </div>
        </div>

        {/* Right Actions: System Badges & User Menu */}
        <div className="flex items-center gap-2.5">
          {/* Active Engine Badge */}
          <Link
            to="/admin/settings"
            title="Sistem ve Model Yapılandırması"
            className="hidden sm:inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium bg-slate-100 hover:bg-slate-200/80 text-slate-700 border border-slate-200 transition-colors"
          >
            <Cpu className="w-3.5 h-3.5 text-meu-600" />
            <span>
              {providerStatus?.active_provider === 'ollama' || providerStatus?.active_provider === 'vllm'
                ? 'Kurum İçi Model'
                : 'Harici API Modu'}
            </span>
            {providerStatus?.airgapped_mode && (
              <span className="px-1 py-0.2 bg-emerald-100 text-emerald-800 text-[10px] font-bold rounded">
                KAPALI AĞ
              </span>
            )}
          </Link>

          {/* Service Status Indicator */}
          <div
            className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold border ${
              isBackendReachable
                ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                : 'bg-amber-50 text-amber-800 border-amber-200'
            }`}
          >
            <Server className={`w-3.5 h-3.5 ${isBackendReachable ? 'text-emerald-600' : 'text-amber-600'}`} />
            <span>{isBackendReachable ? 'Servis Bağlı' : 'Bağlantı Kontrolü'}</span>
          </div>

          {/* User Profile & Role Switcher */}
          <div className="relative">
            <button
              onClick={() => setUserDropdownOpen(!userDropdownOpen)}
              className="flex items-center gap-2 p-1.5 rounded-lg hover:bg-slate-100 border border-transparent hover:border-slate-200 transition-colors text-left"
            >
              <div className="w-7 h-7 rounded-full bg-meu-100 text-meu-700 font-bold flex items-center justify-center text-xs border border-meu-200">
                {user?.full_name ? user.full_name[0] : 'U'}
              </div>
              <div className="hidden md:block">
                <div className="text-xs font-semibold text-slate-800 leading-tight">
                  {user?.full_name?.split(' ')[0]} {user?.full_name?.split(' ')[1]}
                </div>
                <div className="text-[10px] text-slate-500 font-medium">
                  {role ? roleNameMap[role] : 'Kullanıcı'}
                </div>
              </div>
              <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
            </button>

            {userDropdownOpen && (
              <div
                className="absolute right-0 mt-2 w-64 bg-white rounded-xl shadow-xl border border-slate-200 py-2 z-50 text-xs"
                onClick={() => setUserDropdownOpen(false)}
              >
                <div className="px-4 py-2 border-b border-slate-100 bg-slate-50/50">
                  <p className="font-semibold text-slate-900">{user?.full_name}</p>
                  <p className="text-[11px] text-slate-500 truncate">{user?.email}</p>
                  <div className="mt-1">
                    <span className="px-1.5 py-0.5 bg-meu-100 text-meu-800 rounded font-bold text-[10px]">
                      {role ? roleNameMap[role] : role}
                    </span>
                  </div>
                </div>

                <div className="py-1">
                  <p className="px-4 py-1 text-[10px] font-bold uppercase text-slate-400 tracking-wider">
                    Birim Rolü Değiştir
                  </p>
                  <button
                    onClick={() => quickLogin('memur@mersin.edu.tr')}
                    className="w-full text-left px-4 py-1.5 hover:bg-slate-50 flex items-center justify-between text-slate-700"
                  >
                    <span>Memur (Ön İnceleme)</span>
                    <UserCheck className="w-3.5 h-3.5 text-blue-500" />
                  </button>
                  <button
                    onClick={() => quickLogin('koordinator@mersin.edu.tr')}
                    className="w-full text-left px-4 py-1.5 hover:bg-slate-50 flex items-center justify-between text-slate-700"
                  >
                    <span>Koordinatör (Doç. Dr.)</span>
                    <UserCheck className="w-3.5 h-3.5 text-emerald-500" />
                  </button>
                  <button
                    onClick={() => quickLogin('admin@mersin.edu.tr')}
                    className="w-full text-left px-4 py-1.5 hover:bg-slate-50 flex items-center justify-between text-slate-700"
                  >
                    <span>Sistem Yöneticisi</span>
                    <UserCheck className="w-3.5 h-3.5 text-purple-500" />
                  </button>
                  <button
                    onClick={() => quickLogin('kurul@mersin.edu.tr')}
                    className="w-full text-left px-4 py-1.5 hover:bg-slate-50 flex items-center justify-between text-slate-700"
                  >
                    <span>Komisyon Üyesi (Kurul)</span>
                    <UserCheck className="w-3.5 h-3.5 text-amber-500" />
                  </button>
                </div>

                <div className="border-t border-slate-100 pt-1">
                  <Link
                    to="/admin/settings"
                    className="w-full text-left px-4 py-1.5 hover:bg-slate-50 flex items-center gap-2 text-slate-700"
                  >
                    <Sliders className="w-3.5 h-3.5 text-slate-500" />
                    <span>Sistem ve Airgap Ayarları</span>
                  </Link>
                  <button
                    onClick={logout}
                    className="w-full text-left px-4 py-1.5 hover:bg-rose-50 text-rose-600 flex items-center gap-2"
                  >
                    <LogOut className="w-3.5 h-3.5" />
                    <span>Güvenli Çıkış</span>
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  );
};
