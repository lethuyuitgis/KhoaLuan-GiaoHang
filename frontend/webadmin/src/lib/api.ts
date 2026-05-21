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
    if (error.response?.status === 401 && original && !original._retry) {
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
              useAuthStore.getState().updateAccessToken(newAt);
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
