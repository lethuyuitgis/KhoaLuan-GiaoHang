import type { AxiosInstance } from 'axios';
import type { SavedAddress } from '../types';

/** A customer's saved delivery addresses, most-recently-used first. */
export async function listSavedAddresses(client: AxiosInstance): Promise<SavedAddress[]> {
  const { data } = await client.get<SavedAddress[]>('/api/addresses');
  return data;
}

export async function deleteSavedAddress(client: AxiosInstance, id: number): Promise<void> {
  await client.delete(`/api/addresses/${id}`);
}
