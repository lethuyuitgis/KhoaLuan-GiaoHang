import type { AxiosInstance } from 'axios';
import type { OrderRatingResponse } from '../types';

/** Customer rates the shipper for a delivered order (stars 1..5 + optional comment). */
export async function rateOrder(
  client: AxiosInstance,
  orderId: string,
  stars: number,
  comment?: string,
): Promise<OrderRatingResponse> {
  const { data } = await client.post<OrderRatingResponse>(`/api/orders/${orderId}/rating`, {
    stars,
    comment: comment || undefined,
  });
  return data;
}
