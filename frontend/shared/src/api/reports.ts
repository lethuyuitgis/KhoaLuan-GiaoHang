import type { AxiosInstance } from 'axios';
import type {
  CancellationReport,
  DashboardSummary,
  GroupBy,
  RevenuePoint,
  TopShipperRow,
} from '../types/reports';

export interface ReportsRange {
  from: string;  // 'yyyy-MM-dd'
  to: string;    // 'yyyy-MM-dd'
}

export function reportsApi(client: AxiosInstance) {
  return {
    dashboardSummary: () =>
      client.get<DashboardSummary>('/api/admin/dashboard/summary').then(r => r.data),

    revenue: (range: ReportsRange, groupBy: GroupBy = 'day') =>
      client
        .get<RevenuePoint[]>('/api/admin/reports/revenue', {
          params: { from: range.from, to: range.to, groupBy },
        })
        .then(r => r.data),

    topShippers: (range: ReportsRange, limit = 10) =>
      client
        .get<TopShipperRow[]>('/api/admin/reports/top-shippers', {
          params: { from: range.from, to: range.to, limit },
        })
        .then(r => r.data),

    cancellation: (range: ReportsRange) =>
      client
        .get<CancellationReport>('/api/admin/reports/cancellation', {
          params: { from: range.from, to: range.to },
        })
        .then(r => r.data),
  };
}
