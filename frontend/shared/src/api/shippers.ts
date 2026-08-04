import type { AxiosInstance } from 'axios';
import type { ShipperResponse, CreateShipperRequest, ShipperCandidateResponse } from '../types';

export async function listShippers(client: AxiosInstance): Promise<ShipperResponse[]> {
  const { data } = await client.get<ShipperResponse[]>('/api/admin/shippers');
  return data;
}

export async function createShipper(client: AxiosInstance, req: CreateShipperRequest): Promise<ShipperResponse> {
  const { data } = await client.post<ShipperResponse>('/api/admin/shippers', req);
  return data;
}

export async function approveShipper(client: AxiosInstance, shipperId: number): Promise<ShipperResponse> {
  const { data } = await client.post<ShipperResponse>(`/api/admin/shippers/${shipperId}/approve`);
  return data;
}

export async function rejectShipper(client: AxiosInstance, shipperId: number): Promise<void> {
  await client.post(`/api/admin/shippers/${shipperId}/reject`);
}

export async function assignShipper(client: AxiosInstance, orderId: string, shipperId: number): Promise<unknown> {
  const { data } = await client.post(`/api/admin/orders/${orderId}/assign`, { shipperId });
  return data;
}

/** AVAILABLE shippers ranked for an order (nearest to pickup first). */
export async function getCandidateShippers(
  client: AxiosInstance,
  orderId: string
): Promise<ShipperCandidateResponse[]> {
  const { data } = await client.get<ShipperCandidateResponse[]>(`/api/admin/orders/${orderId}/candidate-shippers`);
  return data;
}
