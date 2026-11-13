import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ShieldCheck, Lock, Mail, ArrowRight, UserCheck, Info } from 'lucide-react';

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const { login, quickLogin } = useAuth();
  const [email, setEmail] = useState('memur@mersin.edu.tr');
  const [password, setPassword] = useState('Memur123!');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(email, password);
      navigate('/');
    } catch (err: any) {
      setError(err?.message || 'Giriş başarısız. Lütfen bilgilerinizi kontrol ediniz.');
    } finally {
      setLoading(false);
    }
  };

  const handleQuickLogin = async (targetEmail: string) => {
    setError(null);
    setLoading(true);
    try {
      await quickLogin(targetEmail);
      navigate('/');
    } catch (err: any) {
      setError(err?.message || 'Hızlı giriş yapılamadı.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-100 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="flex justify-center">
          <div className="w-14 h-14 rounded-2xl bg-meu-900 shadow-lg flex items-center justify-center text-white font-black text-2xl border-2 border-meu-700">
            BAP
          </div>
        </div>
        <h2 className="mt-4 text-center text-xl font-bold tracking-tight text-slate-900">
          Mersin Üniversitesi BAP Koordinasyon Birimi
        </h2>
        <p className="mt-1 text-center text-xs text-slate-600">
          Mevzuat Uygunluk ve Ön İnceleme Karar Destek Sistemi
        </p>
      </div>

      <div className="mt-6 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-white py-8 px-4 shadow-xl rounded-2xl sm:px-10 border border-slate-200">
          {/* Institutional Scope Notice */}
          <div className="mb-6 rounded-lg bg-amber-50/80 p-3.5 border border-amber-200 text-xs text-amber-900 flex gap-2.5 items-start">
            <Info className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
            <p className="leading-relaxed">
              <strong>Yasal Sınır Notu:</strong> Bu sistem yalnızca idari, şekli ve mali BAP mevzuat uygunluğunu denetler. Bilimsel değerlendirme ve kabul BAP Komisyonu yetkisindedir.
            </p>
          </div>

          {error && (
            <div className="mb-4 rounded-lg bg-rose-50 p-3 text-xs text-rose-700 border border-rose-200">
              {error}
            </div>
          )}

          <form className="space-y-4" onSubmit={handleSubmit}>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Kurumsal E-Posta
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                  <Mail className="h-4 w-4" />
                </div>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="block w-full pl-9 pr-3 py-2 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 text-slate-900 bg-white"
                  placeholder="ad.soyad@mersin.edu.tr"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Sistem Şifresi
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                  <Lock className="h-4 w-4" />
                </div>
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="block w-full pl-9 pr-3 py-2 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-meu-500 focus:border-meu-500 text-slate-900 bg-white"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full flex justify-center items-center gap-2 py-2.5 px-4 border border-transparent rounded-lg shadow-sm text-xs font-semibold text-white bg-meu-900 hover:bg-meu-800 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-meu-500 transition-colors cursor-pointer"
            >
              <span>{loading ? 'Doğrulanıyor...' : 'Güvenli Giriş Yap'}</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </form>

          {/* Quick Role Selector */}
          <div className="mt-8 pt-6 border-t border-slate-200">
            <p className="text-[11px] font-bold uppercase tracking-wider text-slate-400 text-center mb-3">
              Birim Yetki Profilleri
            </p>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => handleQuickLogin('memur@mersin.edu.tr')}
                className="p-2 text-left rounded-lg border border-slate-200 hover:border-meu-500 hover:bg-meu-50/50 transition-colors"
              >
                <div className="flex items-center gap-1.5 font-semibold text-xs text-slate-800">
                  <UserCheck className="w-3.5 h-3.5 text-blue-600" />
                  <span>Memur</span>
                </div>
                <div className="text-[10px] text-slate-500 truncate">memur@mersin.edu.tr</div>
              </button>

              <button
                type="button"
                onClick={() => handleQuickLogin('koordinator@mersin.edu.tr')}
                className="p-2 text-left rounded-lg border border-slate-200 hover:border-meu-500 hover:bg-meu-50/50 transition-colors"
              >
                <div className="flex items-center gap-1.5 font-semibold text-xs text-slate-800">
                  <UserCheck className="w-3.5 h-3.5 text-emerald-600" />
                  <span>Koordinatör</span>
                </div>
                <div className="text-[10px] text-slate-500 truncate">koordinator@mersin.edu.tr</div>
              </button>

              <button
                type="button"
                onClick={() => handleQuickLogin('admin@mersin.edu.tr')}
                className="p-2 text-left rounded-lg border border-slate-200 hover:border-meu-500 hover:bg-meu-50/50 transition-colors"
              >
                <div className="flex items-center gap-1.5 font-semibold text-xs text-slate-800">
                  <UserCheck className="w-3.5 h-3.5 text-purple-600" />
                  <span>Sistem Yöneticisi</span>
                </div>
                <div className="text-[10px] text-slate-500 truncate">admin@mersin.edu.tr</div>
              </button>

              <button
                type="button"
                onClick={() => handleQuickLogin('kurul@mersin.edu.tr')}
                className="p-2 text-left rounded-lg border border-slate-200 hover:border-meu-500 hover:bg-meu-50/50 transition-colors"
              >
                <div className="flex items-center gap-1.5 font-semibold text-xs text-slate-800">
                  <UserCheck className="w-3.5 h-3.5 text-amber-600" />
                  <span>Kurul Üyesi</span>
                </div>
                <div className="text-[10px] text-slate-500 truncate">kurul@mersin.edu.tr</div>
              </button>
            </div>
          </div>
        </div>

        <div className="mt-4 text-center text-[11px] text-slate-500 flex items-center justify-center gap-1.5">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
          <span>Mersin Üniversitesi BAP Koordinasyon Birimi</span>
        </div>
      </div>
    </div>
  );
};
