export type OrderStatus =
  | 'PENDING' | 'CONFIRMED' | 'ASSIGNED'
  | 'DELIVERING' | 'DELIVERED' | 'CANCELLED' | 'RETURNED';

export type PaymentMethod = 'COD' | 'VNPAY';
export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED';

/** One entry of an order's status timeline (a row of status_history). */
export interface StatusHistoryResponse {
  id: number;
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  changedByUserId: number | null;
  changedAt: string;
  note: string | null;
}

export interface OrderItemResponse {
  id: number;
  productId: number;
  productName: string;
  productImageUrl: string | null;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface OrderResponse {
  id: string;
  code: string;
  customerId: number;
  customerName: string | null;
  customerPhone: string | null;
  pickupLat: string;
  pickupLng: string;
  deliveryAddress: string;
  deliveryLat: string;
  deliveryLng: string;
  distanceKm: string;
  subtotal: number;
  deliveryFee: number;
  deliveryFeeOriginal?: number | null;
  discountShipping?: number | null;
  total: number;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  status: OrderStatus;
  note: string | null;
  createdAt: string;
  items: OrderItemResponse[];
  shipperCommission?: number | null;
}

export interface OrderSummary {
  id: string;
  code: string;
  total: number;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  createdAt: string;
}

export interface CreateOrderRequest {
  customerName?: string;
  customerPhone?: string;
  deliveryAddress: string;
  deliveryLat: string;
  deliveryLng: string;
  items: { productId: number; quantity: number }[];
  paymentMethod: PaymentMethod;
  note?: string;
  voucherCodes?: {
    products?: string;
    shipping?: string;
  };
}
