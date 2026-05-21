import type { AxiosInstance } from 'axios';
import type { MeResponse } from '../types';

export async function fetchMe(client: AxiosInstance): Promise<MeResponse> {
  const { data } = await client.get<MeResponse>('/api/me');
  return data;
}
