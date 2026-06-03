import type { AxiosInstance } from 'axios';
import type {
  ValidateVoucherRequest, ValidateVoucherResponse,
  VoucherSummary, VoucherDetail,
  CreateVoucherRequest, UpdateVoucherRequest,
} from '../types';

/** Customer endpoint — validate a voucher without redeeming it. */
export async function validateVoucher(
  client: AxiosInstance, req: ValidateVoucherRequest
): Promise<ValidateVoucherResponse> {
  const { data } = await client.post<ValidateVoucherResponse>(
    '/api/customer/vouchers/validate', req);
  return data;
}

/** Admin — paginated list. */
export async function fetchAdminVouchers(
  client: AxiosInstance, page = 0, size = 20
): Promise<{ content: VoucherSummary[]; totalElements: number }> {
  const { data } = await client.get('/api/admin/vouchers', { params: { page, size } });
  return data;
}

export async function fetchAdminVoucherDetail(
  client: AxiosInstance, id: number
): Promise<VoucherDetail> {
  const { data } = await client.get<VoucherDetail>(`/api/admin/vouchers/${id}`);
  return data;
}

export async function createVoucher(
  client: AxiosInstance, req: CreateVoucherRequest
): Promise<VoucherSummary> {
  const { data } = await client.post<VoucherSummary>('/api/admin/vouchers', req);
  return data;
}

export async function updateVoucher(
  client: AxiosInstance, id: number, req: UpdateVoucherRequest
): Promise<VoucherSummary> {
  const { data } = await client.put<VoucherSummary>(`/api/admin/vouchers/${id}`, req);
  return data;
}

export async function deleteVoucher(
  client: AxiosInstance, id: number
): Promise<void> {
  await client.delete(`/api/admin/vouchers/${id}`);
}
