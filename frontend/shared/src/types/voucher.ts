export type VoucherTarget = 'SHIPPING' | 'PRODUCTS';
export type DiscountType  = 'FIXED'    | 'PERCENT';

export interface VoucherSummary {
  id: number;
  code: string;
  name: string;
  target: VoucherTarget;
  discountType: DiscountType;
  discountValue: number;
  maxDiscount: number | null;
  usedCount: number;
  maxUsesTotal: number | null;
  validFrom: string;        // ISO 8601
  validUntil: string;
  active: boolean;
}

export interface VoucherRedemptionRow {
  orderId: string;
  customerId: number;
  discountApplied: number;
  createdAt: string;
}

export interface VoucherDetail {
  summary: VoucherSummary;
  recentRedemptions: VoucherRedemptionRow[];
}

export interface ValidateVoucherRequest {
  code: string;
  target: VoucherTarget;
  subtotal: number;
  deliveryFee: number;
}

export interface ValidateVoucherResponse {
  code: string;
  name: string;
  discountAmount: number;
}

export interface CreateVoucherRequest {
  code: string;
  name: string;
  target: VoucherTarget;
  discountType: DiscountType;
  discountValue: number;
  maxDiscount?: number | null;
  minOrderAmount?: number;
  validFrom: string;
  validUntil: string;
  maxUsesTotal?: number | null;
  maxUsesPerCustomer: number;
}

export type UpdateVoucherRequest = Omit<CreateVoucherRequest, 'code' | 'target' | 'discountType'> & {
  active: boolean;
};
