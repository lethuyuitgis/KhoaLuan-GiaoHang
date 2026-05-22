import type { AxiosInstance } from 'axios';
import type { LocationPing } from '../types';

export async function getLatestLocation(client: AxiosInstance, orderId: string): Promise<LocationPing> {
  const { data } = await client.get<LocationPing>(`/api/orders/${orderId}/location`);
  return data;
}
