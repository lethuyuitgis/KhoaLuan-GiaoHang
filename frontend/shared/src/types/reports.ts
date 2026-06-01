// Reports + Dashboard DTOs — must match backend
// (com.shop.delivery.delivery.api.admin.dto.*)

export type OrderStatusKey =
  | 'PENDING'
  | 'CONFIRMED'
  | 'ASSIGNED'
  | 'DELIVERING'
  | 'DELIVERED'
  | 'RETURNED'
  | 'CANCELLED';

export interface OrdersTodaySummary {
  total: number;
  byStatus: Partial<Record<OrderStatusKey, number>>;
}

export interface DashboardSummary {
  ordersToday: OrdersTodaySummary;
  revenueToday: number;
  activeShippers: number;
  newCustomersToday: number;
  revenueLast7Days: RevenuePoint[];
}

export interface RevenuePoint {
  date: string;        // ISO date "yyyy-MM-dd"
  revenue: number;     // VND
  orderCount: number;
}

export interface TopShipperRow {
  shipperId: number;
  name: string;
  deliveredCount: number;
  revenueGenerated: number;
  ratingAvg: number;
}

export interface CancellationReport {
  totalOrders: number;
  cancelledCount: number;
  cancelRate: number;  // 0..1
  byReason: ReasonCount[];
}

export interface ReasonCount {
  reason: string;
  count: number;
}

export type GroupBy = 'day' | 'week';
