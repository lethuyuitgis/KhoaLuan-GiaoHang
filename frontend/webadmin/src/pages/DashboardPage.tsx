import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { reportsApi, formatVnd } from '@shop/shared';
import type { DashboardSummary, TopShipperRow } from '@shop/shared';
import { RevenueMiniChart } from '@/components/charts/RevenueMiniChart';

const reports = reportsApi(api);

export function DashboardPage() {
  const { data, isLoading, isError, refetch } = useQuery<DashboardSummary>({
    queryKey: ['admin', 'dashboard', 'summary'],
    queryFn: reports.dashboardSummary,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
    staleTime: 10_000,
  });

  // Top shippers: last 7 days, top 3
  const today = new Date();
  const sevenDaysAgo = new Date(today.getTime() - 6 * 86_400_000);
  const range = {
    from: toIso(sevenDaysAgo),
    to: toIso(today),
  };
  const { data: top } = useQuery<TopShipperRow[]>({
    queryKey: ['admin', 'dashboard', 'top-shippers', range.from, range.to],
    queryFn: () => reports.topShippers(range, 3),
    refetchInterval: 30_000,
    staleTime: 10_000,
  });

  if (isLoading) {
    return <p className="text-gray-600">Đang tải...</p>;
  }
  if (isError || !data) {
    return (
      <div>
        <p className="text-red-600">Không tải được dữ liệu Dashboard.</p>
        <button onClick={() => refetch()} className="mt-2 px-3 py-1 bg-gray-100 rounded">Thử lại</button>
      </div>
    );
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Tổng quan</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Đơn hôm nay"      value={data.ordersToday.total} />
        <StatCard label="Chờ xác nhận"     value={data.ordersToday.byStatus.PENDING ?? 0}  className="text-yellow-600" />
        <StatCard label="Đang giao"        value={data.ordersToday.byStatus.DELIVERING ?? 0} className="text-purple-600" />
        <StatCard label="Doanh thu hôm nay" value={formatVnd(data.revenueToday)}           className="text-green-600" />
        <StatCard label="Shipper đang hoạt động" value={data.activeShippers} />
        <StatCard label="Khách mới hôm nay" value={data.newCustomersToday} />
        <StatCard label="Đã giao hôm nay"   value={data.ordersToday.byStatus.DELIVERED ?? 0} className="text-green-700" />
        <StatCard label="Đã huỷ hôm nay"    value={data.ordersToday.byStatus.CANCELLED ?? 0} className="text-red-600" />
      </div>

      <div className="mt-6 grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="lg:col-span-2 bg-white rounded-lg shadow p-4">
          <h2 className="font-semibold mb-3">Doanh thu 7 ngày qua</h2>
          <RevenueMiniChart data={data.revenueLast7Days} />
        </div>

        <div className="bg-white rounded-lg shadow p-4">
          <h2 className="font-semibold mb-3">Top 3 shipper (7 ngày)</h2>
          {top && top.length > 0 ? (
            <ol className="space-y-3">
              {top.map((s, i) => (
                <li key={s.shipperId} className="flex justify-between items-center">
                  <div>
                    <p className="font-medium">{i + 1}. {s.name}</p>
                    <p className="text-sm text-gray-500">
                      {s.deliveredCount} đơn · ⭐ {Number(s.ratingAvg).toFixed(2)}
                    </p>
                  </div>
                  <p className="text-sm font-semibold text-green-700">
                    {formatVnd(s.revenueGenerated)}
                  </p>
                </li>
              ))}
            </ol>
          ) : (
            <p className="text-sm text-gray-500">Chưa có dữ liệu.</p>
          )}
        </div>
      </div>
    </div>
  );
}

function StatCard({
  label,
  value,
  className = '',
}: {
  label: string;
  value: number | string;
  className?: string;
}) {
  return (
    <div className="bg-white rounded-lg shadow p-4">
      <p className="text-sm text-gray-600">{label}</p>
      <p className={`text-2xl font-bold mt-1 ${className}`}>{value}</p>
    </div>
  );
}

function toIso(d: Date): string {
  // "yyyy-MM-dd" in local time (matches backend's LocalDate semantics)
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
