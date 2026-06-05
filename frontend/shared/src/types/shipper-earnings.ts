export type LedgerEntryType = 'COMMISSION' | 'COD_OWED' | 'SETTLEMENT_PAYOUT' | 'SETTLEMENT_DEPOSIT';

export interface EarningsSummary {
  today: number;
  week: number;
  month: number;
  balance: number;
  todayOrders: number;
  weekOrders: number;
}

export interface DailyEarning {
  date: string;
  ordersCount: number;
  commission: number;
}

export interface LedgerRow {
  id: number;
  entryType: LedgerEntryType;
  amount: number;
  orderId: string | null;
  note: string | null;
  createdAt: string;
}

export interface ShipperSelfProfile {
  name: string;
  phone: string;
  ratingAvg: number;
  totalOrders: number;
  joinedAt: string;
}

export interface BalanceResponse {
  balance: number;
  lastSettledAt: string | null;
}

export interface SettleRequest {
  type: 'PAYOUT' | 'DEPOSIT';
  amount: number;
  note?: string;
}

export interface EarningsBucket {
  groupKey: string;
  ordersCount: number;
  commission: number;
}
