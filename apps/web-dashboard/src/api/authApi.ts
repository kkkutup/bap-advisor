import { apiRequest, isForceOffline } from './client';
import { AuthTokenResponse, UserProfileResponse } from '../types/api';
import { MOCK_USERS } from './mockData';

export const authApi = {
  async login(email: string, password: string): Promise<AuthTokenResponse> {
    try {
      const res = await apiRequest<AuthTokenResponse>('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      });
      localStorage.setItem('bap_access_token', res.access_token);
      localStorage.setItem('bap_user', JSON.stringify(res.user));
      return res;
    } catch (err) {
      // ONLY fall back to mock data if offline mode is explicitly forced by user
      if (!isForceOffline()) {
        throw err;
      }

      const mockUser = MOCK_USERS[email] || {
        id: '99999999-9999-9999-9999-999999999999',
        email,
        full_name: email.split('@')[0].toUpperCase(),
        role: 'OFFICER',
        is_active: true,
      };

      const mockResponse: AuthTokenResponse = {
        access_token: 'mock-jwt-token-' + btoa(email),
        refresh_token: 'mock-refresh-token',
        token_type: 'Bearer',
        expires_in_seconds: 86400,
        user: mockUser,
      };

      localStorage.setItem('bap_access_token', mockResponse.access_token);
      localStorage.setItem('bap_user', JSON.stringify(mockUser));
      return mockResponse;
    }
  },

  async getCurrentUser(): Promise<UserProfileResponse> {
    try {
      return await apiRequest<UserProfileResponse>('/auth/me');
    } catch (err) {
      if (!isForceOffline()) {
        throw err;
      }
      const stored = localStorage.getItem('bap_user');
      if (stored) {
        return JSON.parse(stored);
      }
      return MOCK_USERS['memur@mersin.edu.tr'];
    }
  },

  logout() {
    localStorage.removeItem('bap_access_token');
    localStorage.removeItem('bap_user');
  },
};
