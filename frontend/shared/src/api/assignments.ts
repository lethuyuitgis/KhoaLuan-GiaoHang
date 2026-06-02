import type { AxiosInstance } from 'axios';
import type { AssignmentResponse } from '../types';

export async function listMyAssignments(client: AxiosInstance): Promise<AssignmentResponse[]> {
  const { data } = await client.get<AssignmentResponse[]>('/api/shipper/assignments');
  return data;
}

export async function acceptAssignment(client: AxiosInstance, id: string): Promise<AssignmentResponse> {
  const { data } = await client.post<AssignmentResponse>(`/api/shipper/assignments/${id}/accept`);
  return data;
}

export async function rejectAssignment(client: AxiosInstance, id: string): Promise<void> {
  await client.post(`/api/shipper/assignments/${id}/reject`);
}

export async function startAssignment(client: AxiosInstance, id: string): Promise<AssignmentResponse> {
  const { data } = await client.post<AssignmentResponse>(`/api/shipper/assignments/${id}/start`);
  return data;
}

export async function completeAssignment(client: AxiosInstance, id: string): Promise<AssignmentResponse> {
  const { data } = await client.post<AssignmentResponse>(`/api/shipper/assignments/${id}/complete`);
  return data;
}

export interface RateCustomerRequest {
  stars: number;
  comment?: string | null;
}

export interface RateCustomerResponse {
  ratingId: number;
  orderId: string;
  stars: number;
  comment: string | null;
  createdAt: string;
}

/**
 * Shipper rates the customer (Should #6, counterpart to customer→shipper rating).
 * Endpoint: POST /api/shipper/orders/{orderId}/rate-customer
 * Result is NOT shown publicly to the customer.
 */
export async function rateCustomer(
  client: AxiosInstance,
  orderId: string,
  body: RateCustomerRequest,
): Promise<RateCustomerResponse> {
  const { data } = await client.post<RateCustomerResponse>(
    `/api/shipper/orders/${orderId}/rate-customer`,
    body,
  );
  return data;
}
