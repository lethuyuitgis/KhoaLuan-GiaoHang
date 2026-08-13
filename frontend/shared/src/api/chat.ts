import type { AxiosInstance } from 'axios';
import type { OrderChatMessage } from '../types';

/** Customer's anonymous chat with the shipper for one order (oldest→newest). */
export async function getOrderChat(client: AxiosInstance, orderId: string): Promise<OrderChatMessage[]> {
  const { data } = await client.get<OrderChatMessage[]>(`/api/orders/${orderId}/chat`);
  return data;
}

export async function sendOrderChat(
  client: AxiosInstance,
  orderId: string,
  body: string,
): Promise<OrderChatMessage> {
  const { data } = await client.post<OrderChatMessage>(`/api/orders/${orderId}/chat`, { body });
  return data;
}
