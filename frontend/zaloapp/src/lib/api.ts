import { createApiClient } from '@shop/shared';
import { getAuthHeaders } from './auth';

const baseURL = (import.meta.env.VITE_API_BASE_URL as string) ?? '';

export const api = createApiClient(baseURL, getAuthHeaders);
