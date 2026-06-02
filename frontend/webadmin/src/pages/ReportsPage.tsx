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

  const inputCls = "block w-full px-3 py-2 text-sm border border-gray-300 rounded-lg bg-white text-gray-900 " +
    "focus:ring-2 focus:ring-orange-500 focus:border-orange-500 outline-none transition-colors";
  const labelCls = "block text-xs font-semibold text-gray-600 mb-1.5 uppercase tracking-wide";

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Báo cáo</h1>
        <p className="text-sm text-gray-500 mt-1">Doanh thu, top shipper, tỉ lệ huỷ theo khoảng thời gian tuỳ chọn</p>
      </div>

      {/* Date-range picker */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 mb-6">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 items-end">
          <div>
            <label htmlFor="from" className={labelCls}>Từ ngày</label>
            <input
              id="from"
              type="date"
              value={from}
              min={minDate}
              max={to}
              onChange={e => setFrom(e.target.value)}
              className={inputCls}
            />
          </div>
          <div>
            <label htmlFor="to" className={labelCls}>Đến ngày</label>
            <input
              id="to"
              type="date"
              value={to}
              min={from}
              max={maxDate}
              onChange={e => setTo(e.target.value)}
              className={inputCls}
            />
          </div>
          <div>
            <label htmlFor="groupBy" className={labelCls}>Nhóm theo</label>
            <div className="inline-flex rounded-lg border border-gray-200 bg-gray-50 p-0.5 w-full">
              {(['day', 'week'] as const).map(g => (
                <button
                  key={g}
                  type="button"
                  onClick={() => setGroupBy(g)}
                  className={`flex-1 text-sm py-1.5 rounded-md font-medium transition-colors ${
                    groupBy === g
                      ? 'bg-white text-orange-600 shadow-sm'
                      : 'text-gray-500 hover:text-gray-700'
                  }`}
                >
                  {g === 'day' ? 'Theo ngày' : 'Theo tuần'}
                </button>
              ))}
            </div>
          </div>
          <div className="text-xs text-gray-500">
            Tối đa {MAX_DAYS} ngày một lần.
          </div>
        </div>
        {!rangeValid && (
          <p className="text-red-600 text-sm mt-3">
            ⚠️ Khoảng thời gian không hợp lệ (tối đa {MAX_DAYS} ngày).
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
