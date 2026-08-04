import type { AxiosInstance } from 'axios';
import type { CreateOrderRequest, OrderResponse, OrderSummary, Page, StatusHistoryResponse } from '../types';

export async function createOrder(
  client: AxiosInstance,
  req: CreateOrderRequest
): Promise<OrderResponse> {
  const { data } = await client.post<OrderResponse>('/api/orders', req);
  return data;
}

export async function listMyOrders(
  client: AxiosInstance,
  page = 0,
  size = 20
): Promise<Page<OrderSummary>> {
  const { data } = await client.get<Page<OrderSummary>>('/api/orders/mine', {
    params: { page, size },
  });
  return data;
}

export async function getOrder(client: AxiosInstance, id: string): Promise<OrderResponse> {
  const { data } = await client.get<OrderResponse>(`/api/orders/${id}`);
  return data;
}

export async function cancelOrder(
  client: AxiosInstance,
  id: string,
  reason?: string
): Promise<OrderResponse> {
  const { data } = await client.post<OrderResponse>(`/api/orders/${id}/cancel`, { reason });
  return data;
}

/** Web Admin: status timeline of an order, oldest→newest (as stored). */
export async function getAdminOrderHistory(
  client: AxiosInstance,
  id: string
): Promise<StatusHistoryResponse[]> {
  const { data } = await client.get<StatusHistoryResponse[]>(`/api/admin/orders/${id}/history`);
  return data;
}
