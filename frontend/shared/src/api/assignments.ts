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
