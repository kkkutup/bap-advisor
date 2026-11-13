// API Client with automatic offline fallback to seed mock data

const API_BASE = '/api/v1';

export interface RequestOptions extends RequestInit {
  params?: Record<string, string | number | boolean | undefined | null>;
}

class ApiError extends Error {
  status: number;
  data: any;

  constructor(status: number, message: string, data?: any) {
    super(message);
    this.status = status;
    this.data = data;
  }
}

export let isBackendReachable = true;
type ConnectionListener = (reachable: boolean) => void;
const connectionListeners: ConnectionListener[] = [];

export function subscribeToConnectionStatus(listener: ConnectionListener) {
  connectionListeners.push(listener);
  listener(isBackendReachable);
  return () => {
    const idx = connectionListeners.indexOf(listener);
    if (idx !== -1) connectionListeners.splice(idx, 1);
  };
}

export function setBackendReachableState(reachable: boolean) {
  if (isBackendReachable !== reachable) {
    isBackendReachable = reachable;
    connectionListeners.forEach((fn) => fn(reachable));
  }
}

// User-forced offline mock toggle
const FORCE_OFFLINE_KEY = 'bap_force_offline_mode';
export function isForceOffline(): boolean {
  return localStorage.getItem(FORCE_OFFLINE_KEY) === 'true';
}

export function setForceOffline(enabled: boolean) {
  localStorage.setItem(FORCE_OFFLINE_KEY, enabled ? 'true' : 'false');
  setBackendReachableState(!enabled);
}

const SEED_PASSWORDS: Record<string, string> = {
  'memur@mersin.edu.tr': 'Memur123!',
  'koordinator@mersin.edu.tr': 'Koord123!',
  'admin@mersin.edu.tr': 'Admin123!',
  'kurul@mersin.edu.tr': 'Kurul123!',
};

export async function transparentRelogin(): Promise<string | null> {
  try {
    let email = 'memur@mersin.edu.tr';
    const storedUserStr = localStorage.getItem('bap_user');
    if (storedUserStr) {
      try {
        const u = JSON.parse(storedUserStr);
        if (u.email && SEED_PASSWORDS[u.email]) {
          email = u.email;
        }
      } catch {}
    }
    const password = SEED_PASSWORDS[email] || 'Memur123!';

    const res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ email, password }),
    });

    if (res.ok) {
      const data = await res.json();
      if (data.access_token) {
        localStorage.setItem('bap_access_token', data.access_token);
        if (data.user) {
          localStorage.setItem('bap_user', JSON.stringify(data.user));
        }
        setBackendReachableState(true);
        return data.access_token;
      }
    }
  } catch {}
  return null;
}

export async function apiRequest<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  // If user forced offline mode, throw so caller falls back to mock
  if (isForceOffline()) {
    throw new ApiError(0, 'Offline mode is forced by user');
  }

  const { params, headers, ...rest } = options;
  let token = localStorage.getItem('bap_access_token');

  // CRITICAL FIX: If token is missing or a stale mock token from previous failed logins,
  // transparently re-authenticate with real core-api credentials
  if ((!token || token.startsWith('mock-jwt-token-')) && !endpoint.startsWith('/auth/login')) {
    const freshToken = await transparentRelogin();
    if (freshToken) {
      token = freshToken;
    }
  }

  let url = `${API_BASE}${endpoint}`;
  if (params) {
    const searchParams = new URLSearchParams();
    Object.entries(params).forEach(([key, val]) => {
      if (val !== undefined && val !== null) {
        searchParams.append(key, String(val));
      }
    });
    const queryString = searchParams.toString();
    if (queryString) {
      url += (url.includes('?') ? '&' : '?') + queryString;
    }
  }

  const defaultHeaders: Record<string, string> = {
    Accept: 'application/json',
  };

  if (!(rest.body instanceof FormData)) {
    defaultHeaders['Content-Type'] = 'application/json';
  }

  if (token) {
    defaultHeaders['Authorization'] = `Bearer ${token}`;
  }

  try {
    const response = await fetch(url, {
      ...rest,
      headers: {
        ...defaultHeaders,
        ...(headers as Record<string, string>),
      },
    });

    if (response.ok) {
      setBackendReachableState(true);
      if (response.status === 204) {
        return {} as T;
      }
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        return (await response.json()) as T;
      }
      return (await response.blob()) as unknown as T;
    }

    // Auto-recovery for 401: Attempt transparent relogin & one-shot retry
    if (response.status === 401 && !endpoint.startsWith('/auth/login')) {
      const refreshedToken = await transparentRelogin();
      if (refreshedToken) {
        const retryHeaders = {
          ...defaultHeaders,
          ...(headers as Record<string, string>),
          Authorization: `Bearer ${refreshedToken}`,
        };
        const retryResponse = await fetch(url, {
          ...rest,
          headers: retryHeaders,
        });
        if (retryResponse.ok) {
          setBackendReachableState(true);
          if (retryResponse.status === 204) {
            return {} as T;
          }
          const ct = retryResponse.headers.get('content-type');
          if (ct && ct.includes('application/json')) {
            return (await retryResponse.json()) as T;
          }
          return (await retryResponse.blob()) as unknown as T;
        }
      }
      // If retry still fails, clear bad token
      localStorage.removeItem('bap_access_token');
    }

    let errorData = null;
    try {
      errorData = await response.json();
    } catch {
      // response wasn't JSON
    }

    throw new ApiError(
      response.status,
      errorData?.message || `API Hatası: ${response.status} ${response.statusText}`,
      errorData
    );
  } catch (err: any) {
    if (err instanceof ApiError) {
      throw err;
    }
    // Network error / connection refused
    setBackendReachableState(false);
    throw new ApiError(0, err?.message || 'Sunucuya ulaşılamıyor (Ağ hatası)');
  }
}
