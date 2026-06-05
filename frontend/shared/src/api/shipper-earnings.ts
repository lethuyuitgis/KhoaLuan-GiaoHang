import type { AxiosInstance } from 'axios';
import type {
  EarningsSummary, DailyEarning, LedgerRow, ShipperSelfProfile,
  BalanceResponse, SettleRequest, EarningsBucket,
} from '../types';

// Shipper self (Telegram-auth required)
export async function fetchEarningsSummary(client: AxiosInstance): Promise<EarningsSummary> {
  const { data } = await client.get<EarningsSummary>('/api/shipper/me/earnings/summary');
  return data;
}
export async function fetchDailyEarnings(client: AxiosInstance, from: string, to: string): Promise<DailyEarning[]> {
  const { data } = await client.get<DailyEarning[]>('/api/shipper/me/earnings/daily', { params: { from, to } });
  return data;
}
export async function fetchShipperLedger(
  client: AxiosInstance, page = 0, size = 20
): Promise<{ content: LedgerRow[]; totalElements: number }> {
  const { data } = await client.get('/api/shipper/me/ledger', { params: { page, size } });
  return data;
}
export async function fetchShipperSelfProfile(client: AxiosInstance): Promise<ShipperSelfProfile> {
  const { data } = await client.get<ShipperSelfProfile>('/api/shipper/me/profile');
  return data;
}

// Admin (JWT-auth)
export async function fetchAdminShipperBalance(client: AxiosInstance, id: number): Promise<BalanceResponse> {
  const { data } = await client.get<BalanceResponse>(`/api/admin/shippers/${id}/balance`);
  return data;
}
export async function fetchAdminShipperLedger(
  client: AxiosInstance, id: number, page = 0, size = 20
): Promise<{ content: LedgerRow[]; totalElements: number }> {
  const { data } = await client.get(`/api/admin/shippers/${id}/ledger`, { params: { page, size } });
  return data;
}
export async function settleShipper(client: AxiosInstance, id: number, req: SettleRequest): Promise<LedgerRow> {
  const { data } = await client.post<LedgerRow>(`/api/admin/shippers/${id}/settle`, req);
  return data;
}
export async function fetchAdminShipperEarnings(
  client: AxiosInstance, from: string, to: string, groupBy: 'shipper' | 'day' = 'shipper'
): Promise<EarningsBucket[]> {
  const { data } = await client.get<EarningsBucket[]>('/api/admin/reports/shipper-earnings',
    { params: { from, to, groupBy } });
  return data;
}
