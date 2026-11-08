import React, { createContext, useContext, useState, useEffect } from 'react';
import { UserProfileResponse, UserRole } from '../types/api';
import { authApi } from '../api/authApi';

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
    if (token) {
      authApi
        .getCurrentUser()
        .then((u) => setUser(u))
        .catch(() => {
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
    await login(email, 'BapAdvisor2026!');
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
