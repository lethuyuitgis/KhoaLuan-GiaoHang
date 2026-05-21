import axios, { type AxiosInstance } from 'axios';

export type GetHeaders = () => Record<string, string>;

export function createApiClient(baseURL: string, getHeaders: GetHeaders): AxiosInstance {
  const instance = axios.create({
    baseURL,
    timeout: 15000,
  });

  instance.interceptors.request.use(config => {
    const extra = getHeaders();
    for (const [k, v] of Object.entries(extra)) {
      config.headers.set(k, v);
    }
    return config;
  });

  return instance;
}
