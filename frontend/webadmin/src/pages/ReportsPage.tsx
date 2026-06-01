import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { format, subDays } from 'date-fns';
import { api } from '@/lib/api';
import { reportsApi } from '@shop/shared';
import type {
  CancellationReport,
  GroupBy,
  RevenuePoint,
  TopShipperRow,
} from '@shop/shared';
import { RevenueChart } from '@/components/charts/RevenueChart';
import { TopShippersChart } from '@/components/charts/TopShippersChart';
import { CancellationChart } from '@/components/charts/CancellationChart';

const reports = reportsApi(api);
const MAX_DAYS = 90;

export function ReportsPage() {
  const today = new Date();
  const [from, setFrom] = useState(format(subDays(today, 30), 'yyyy-MM-dd'));
  const [to, setTo]     = useState(format(today, 'yyyy-MM-dd'));
  const [groupBy, setGroupBy] = useState<GroupBy>('day');

  const maxDate = format(today, 'yyyy-MM-dd');
  const minDate = format(subDays(today, MAX_DAYS), 'yyyy-MM-dd');

  const range = { from, to };
  const rangeValid = from <= to && daysBetween(from, to) <= MAX_DAYS;

  const revenue = useQuery<RevenuePoint[]>({
    queryKey: ['admin', 'reports', 'revenue', from, to, groupBy],
    queryFn: () => reports.revenue(range, groupBy),
    enabled: rangeValid,
  });

  const top = useQuery<TopShipperRow[]>({
    queryKey: ['admin', 'reports', 'top-shippers', from, to],
    queryFn: () => reports.topShippers(range, 10),
    enabled: rangeValid,
  });

  const cancellation = useQuery<CancellationReport>({
    queryKey: ['admin', 'reports', 'cancellation', from, to],
    queryFn: () => reports.cancellation(range),
    enabled: rangeValid,
  });

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Báo cáo</h1>

      {/* Date-range picker */}
      <div className="bg-white rounded-lg shadow p-4 mb-6 flex flex-wrap gap-4 items-end">
        <div>
          <label htmlFor="from" className="block text-sm text-gray-600 mb-1">Từ ngày</label>
          <input
            id="from"
            type="date"
            value={from}
            min={minDate}
            max={to}
            onChange={e => setFrom(e.target.value)}
            className="border border-gray-300 rounded px-2 py-1"
          />
        </div>
        <div>
          <label htmlFor="to" className="block text-sm text-gray-600 mb-1">Đến ngày</label>
          <input
            id="to"
            type="date"
            value={to}
            min={from}
            max={maxDate}
            onChange={e => setTo(e.target.value)}
            className="border border-gray-300 rounded px-2 py-1"
          />
        </div>
        <div>
          <label htmlFor="groupBy" className="block text-sm text-gray-600 mb-1">Nhóm theo</label>
          <select
            id="groupBy"
            value={groupBy}
            onChange={e => setGroupBy(e.target.value as GroupBy)}
            className="border border-gray-300 rounded px-2 py-1"
          >
            <option value="day">Ngày</option>
            <option value="week">Tuần</option>
          </select>
        </div>
        {!rangeValid && (
          <p className="text-red-600 text-sm self-center">
            Khoảng thời gian không hợp lệ (tối đa {MAX_DAYS} ngày).
          </p>
        )}
      </div>

      {/* Revenue chart */}
      <section className="bg-white rounded-lg shadow p-4 mb-6">
        <h2 className="font-semibold mb-3">Doanh thu theo {groupBy === 'day' ? 'ngày' : 'tuần'}</h2>
        {revenue.isLoading ? <p>Đang tải...</p>
          : revenue.isError ? <p className="text-red-600">Lỗi tải doanh thu.</p>
          : <RevenueChart data={revenue.data ?? []} />}
      </section>

      {/* Top shippers */}
      <section className="bg-white rounded-lg shadow p-4 mb-6">
        <h2 className="font-semibold mb-3">Top shipper</h2>
        {top.isLoading ? <p>Đang tải...</p>
          : top.isError ? <p className="text-red-600">Lỗi tải dữ liệu shipper.</p>
          : <TopShippersChart data={top.data ?? []} />}
      </section>

      {/* Cancellation */}
      <section className="bg-white rounded-lg shadow p-4 mb-6">
        <h2 className="font-semibold mb-3">Tỷ lệ huỷ + lý do</h2>
        {cancellation.isLoading ? <p>Đang tải...</p>
          : cancellation.isError ? <p className="text-red-600">Lỗi tải dữ liệu huỷ.</p>
          : cancellation.data && (
              <>
                <p className="mb-3 text-sm text-gray-700">
                  Tổng đơn: <b>{cancellation.data.totalOrders}</b> · Đã huỷ: <b>{cancellation.data.cancelledCount}</b> · Tỷ lệ huỷ: <b>{(cancellation.data.cancelRate * 100).toFixed(1)}%</b>
                </p>
                <CancellationChart data={cancellation.data.byReason} />
              </>
            )}
      </section>
    </div>
  );
}

function daysBetween(from: string, to: string): number {
  const f = new Date(from);
  const t = new Date(to);
  return Math.floor((t.getTime() - f.getTime()) / 86_400_000);
}
