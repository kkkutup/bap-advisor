import React, { createContext, useContext, useState, useEffect } from 'react';
import { UserProfileResponse, UserRole } from '../types/api';
import { authApi } from '../api/authApi';
import { transparentRelogin } from '../api/client';

export const ROLE_DEFAULT_PASSWORDS: Record<string, string> = {
  'memur@mersin.edu.tr': 'Memur123!',
  'koordinator@mersin.edu.tr': 'Koord123!',
  'admin@mersin.edu.tr': 'Admin123!',
  'kurul@mersin.edu.tr': 'Kurul123!',
};

interface AuthContextType {
  user: UserProfileResponse | null;
  role: UserRole | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  quickLogin: (email: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserProfileResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('bap_access_token');

    // CRITICAL: Auto-heal current open sessions if holding a stale mock token
    if (token && token.startsWith('mock-jwt-token-')) {
      localStorage.removeItem('bap_access_token');
      transparentRelogin()
        .then(() => authApi.getCurrentUser())
        .then((u) => setUser(u))
        .catch(() => setUser(null))
        .finally(() => setIsLoading(false));
      return;
    }

    if (token) {
      authApi
        .getCurrentUser()
        .then((u) => setUser(u))
        .catch(async () => {
          // If token was rejected (401), attempt transparent relogin
          const freshToken = await transparentRelogin();
          if (freshToken) {
            try {
              const u = await authApi.getCurrentUser();
              setUser(u);
              return;
            } catch {}
          }
          authApi.logout();
          setUser(null);
        })
        .finally(() => setIsLoading(false));
    } else {
      setIsLoading(false);
    }
  }, []);

  const login = async (email: string, password: string) => {
    const res = await authApi.login(email, password);
    setUser(res.user);
  };

  const quickLogin = async (email: string) => {
    const password = ROLE_DEFAULT_PASSWORDS[email] || 'Memur123!';
    await login(email, password);
  };

  const logout = () => {
    authApi.logout();
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        role: user?.role || null,
        isAuthenticated: !!user,
        isLoading,
        login,
        quickLogin,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
