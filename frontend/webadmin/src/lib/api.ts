import axios, { type AxiosInstance, AxiosError } from 'axios';
import { useAuthStore } from '@/stores/auth-store';
import { authStorage } from './auth-storage';

const baseURL = (import.meta.env.VITE_API_BASE_URL as string) ?? '';

export const api: AxiosInstance = axios.create({ baseURL, timeout: 15000 });

api.interceptors.request.use(config => {
  const auth = useAuthStore.getState().auth;
  if (auth?.accessToken) {
    config.headers.set('Authorization', `Bearer ${auth.accessToken}`);
  }
  return config;
});

let refreshInFlight: Promise<string> | null = null;

api.interceptors.response.use(
  resp => resp,
  async (error: AxiosError) => {
    const original = error.config as any;
    // Backend trả 403 (không phải 401) cho JWT hết hạn/thiếu ở admin chain →
    // phải refresh cả khi 403, nếu không access-token hết hạn (15') là kẹt 403.
    const status = error.response?.status;
    const isAuthExpired = status === 401 || status === 403;
    // Đừng thử refresh cho chính endpoint refresh (tránh lặp).
    const isRefreshCall = typeof original?.url === 'string' && original.url.includes('/auth/refresh');
    if (isAuthExpired && original && !original._retry && !isRefreshCall) {
      const auth = useAuthStore.getState().auth;
      if (!auth?.refreshToken) {
        useAuthStore.getState().logout();
        return Promise.reject(error);
      }
      original._retry = true;
      try {
        if (!refreshInFlight) {
          refreshInFlight = axios
            .post<{ accessToken: string; refreshToken: string }>(
              `${baseURL}/api/admin/auth/refresh`,
              { refreshToken: auth.refreshToken }
            )
            .then(r => {
              const newAt = r.data.accessToken;
              // Cập nhật CẢ refreshToken mới (backend có xoay token) — nếu chỉ giữ
              // accessToken thì lần refresh sau dùng refreshToken cũ đã bị vô hiệu → 403.
              const cur = useAuthStore.getState().auth;
              if (cur) useAuthStore.getState().setAuth({ ...cur, accessToken: newAt, refreshToken: r.data.refreshToken });
              else useAuthStore.getState().updateAccessToken(newAt);
              return newAt;
            })
            .finally(() => { refreshInFlight = null; });
        }
        const newToken = await refreshInFlight;
        original.headers.Authorization = `Bearer ${newToken}`;
        return api(original);
      } catch {
        useAuthStore.getState().logout();
        authStorage.clear();
        return Promise.reject(error);
      }
    }
    return Promise.reject(error);
  }
);
