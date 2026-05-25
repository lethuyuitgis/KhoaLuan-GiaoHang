import type { AxiosInstance } from 'axios';
import type { CreatePaymentRequest, CreatePaymentResponse } from '../types/payment';

export async function createVnpayPayment(
  api: AxiosInstance,
  body: CreatePaymentRequest,
): Promise<CreatePaymentResponse> {
  const { data } = await api.post<CreatePaymentResponse>(
    '/api/payment/vnpay/create',
    body,
  );
  return data;
}
