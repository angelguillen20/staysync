import axios from 'axios';

const KEYS = { access: 'ss_access_token', refresh: 'ss_refresh_token' };
const BFF_URL = import.meta.env.VITE_BFF_URL ?? '/bff';

const apiClient = axios.create({
  baseURL: BFF_URL,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
});

/* ── Request interceptor: attach JWT ─────────────────────────── */
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(KEYS.access);
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

/* ── Refresh mutex: one promise shared by all concurrent retries ── */
let refreshPromise = null;

function clearSession() {
  Object.values(KEYS).forEach((k) => localStorage.removeItem(k));
  localStorage.removeItem('ss_user');
}

/* ── Response interceptor: handle 401 ───────────────────────── */
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;

    // Only retry once, and never on the refresh endpoint itself
    if (
      error.response?.status !== 401 ||
      original._retry ||
      original.url?.includes('/auth/refresh')
    ) {
      return Promise.reject(error);
    }

    original._retry = true;

    const refreshToken = localStorage.getItem(KEYS.refresh);
    if (!refreshToken) {
      clearSession();
      window.location.href = '/login';
      return Promise.reject(error);
    }

    // If a refresh is already in flight, piggyback on it instead of firing another
    if (!refreshPromise) {
      refreshPromise = axios
        .post(`${BFF_URL}/auth/refresh`, { refreshToken })
        .then(({ data }) => {
          localStorage.setItem(KEYS.access, data.accessToken);
          localStorage.setItem(KEYS.refresh, data.refreshToken);
          return data.accessToken;
        })
        .finally(() => {
          // Always release the lock so future 401s can refresh again
          refreshPromise = null;
        });
    }

    return refreshPromise
      .then((newToken) => {
        original.headers.Authorization = `Bearer ${newToken}`;
        return apiClient(original);
      })
      .catch(() => {
        // Refresh failed (token not found, expired, etc.) → force logout once
        clearSession();
        window.location.href = '/login';
        return Promise.reject(error);
      });
  },
);

export default apiClient;
