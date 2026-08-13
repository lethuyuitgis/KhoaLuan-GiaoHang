export type ChatSenderRole = 'CUSTOMER' | 'SHIPPER';

/** One message in an order's anonymous customer↔shipper chat. */
export interface OrderChatMessage {
  id: number;
  senderRole: ChatSenderRole;
  body: string;
  createdAt: string;
}
