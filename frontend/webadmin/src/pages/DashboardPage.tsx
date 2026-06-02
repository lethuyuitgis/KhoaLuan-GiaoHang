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
    return (
      <div>
        <PageHeader title="Tổng quan" subtitle="Tóm tắt hoạt động shop trong hôm nay" />
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="h-24 bg-white rounded-xl border border-gray-100 animate-pulse" />
          ))}
        </div>
      </div>
    );
  }
  if (isError || !data) {
    return (
      <div className="max-w-md">
        <PageHeader title="Tổng quan" subtitle="Tóm tắt hoạt động shop trong hôm nay" />
        <div className="bg-red-50 border border-red-200 rounded-xl p-4">
          <p className="text-red-700 font-medium">Không tải được dữ liệu Dashboard.</p>
          <button onClick={() => refetch()}
            className="mt-3 px-4 py-1.5 bg-white border border-red-200 text-red-700 text-sm font-medium rounded-lg hover:bg-red-100 transition">
            Thử lại
          </button>
        </div>
      </div>
    );
  }

  return (
    <div>
      <PageHeader title="Tổng quan" subtitle="Tóm tắt hoạt động shop trong hôm nay" />

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard icon={IconOrders}    color="blue"   label="Đơn hôm nay"             value={data.ordersToday.total} />
        <StatCard icon={IconClock}     color="amber"  label="Chờ xác nhận"            value={data.ordersToday.byStatus.PENDING ?? 0} />
        <StatCard icon={IconTruck}     color="purple" label="Đang giao"               value={data.ordersToday.byStatus.DELIVERING ?? 0} />
        <StatCard icon={IconMoney}     color="green"  label="Doanh thu hôm nay"       value={formatVnd(data.revenueToday)} />
        <StatCard icon={IconBike}      color="sky"    label="Shipper đang hoạt động"  value={data.activeShippers} />
        <StatCard icon={IconUserPlus}  color="indigo" label="Khách mới hôm nay"       value={data.newCustomersToday} />
        <StatCard icon={IconCheck}     color="emerald" label="Đã giao hôm nay"        value={data.ordersToday.byStatus.DELIVERED ?? 0} />
        <StatCard icon={IconX}         color="rose"   label="Đã huỷ hôm nay"          value={data.ordersToday.byStatus.CANCELLED ?? 0} />
      </div>

      <div className="mt-6 grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="lg:col-span-2 bg-white rounded-xl border border-gray-100 shadow-sm p-5">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="font-semibold text-gray-900">Doanh thu 7 ngày qua</h2>
              <p className="text-xs text-gray-500 mt-0.5">Số đơn + tổng doanh thu theo ngày</p>
            </div>
          </div>
          <RevenueMiniChart data={data.revenueLast7Days} />
        </div>

        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
          <h2 className="font-semibold text-gray-900 mb-1">Top 3 shipper</h2>
          <p className="text-xs text-gray-500 mb-4">7 ngày gần nhất</p>
          {top && top.length > 0 ? (
            <ol className="space-y-3">
              {top.map((s, i) => {
                const medal = ['from-amber-400 to-yellow-500', 'from-gray-300 to-gray-400', 'from-amber-600 to-orange-700'][i] ?? 'from-slate-300 to-slate-400';
                return (
                  <li key={s.shipperId} className="flex items-center gap-3">
                    <div className={`w-9 h-9 rounded-full bg-gradient-to-br ${medal} flex items-center justify-center text-white text-sm font-bold flex-shrink-0`}>
                      {i + 1}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="font-medium text-gray-900 truncate">{s.name}</p>
                      <p className="text-xs text-gray-500">
                        {s.deliveredCount} đơn · ⭐ {Number(s.ratingAvg).toFixed(2)}
                      </p>
                    </div>
                    <p className="text-sm font-semibold text-green-700 whitespace-nowrap">
                      {formatVnd(s.revenueGenerated)}
                    </p>
                  </li>
                );
              })}
            </ol>
          ) : (
            <p className="text-sm text-gray-400 italic">Chưa có dữ liệu.</p>
          )}
        </div>
      </div>
    </div>
  );
}

function PageHeader({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div className="mb-6">
      <h1 className="text-2xl font-bold text-gray-900">{title}</h1>
      {subtitle && <p className="text-sm text-gray-500 mt-1">{subtitle}</p>}
    </div>
  );
}

const COLOR_MAP: Record<string, { bg: string; fg: string }> = {
  blue:    { bg: 'bg-blue-50',    fg: 'text-blue-600' },
  amber:   { bg: 'bg-amber-50',   fg: 'text-amber-600' },
  purple:  { bg: 'bg-purple-50',  fg: 'text-purple-600' },
  green:   { bg: 'bg-green-50',   fg: 'text-green-600' },
  sky:     { bg: 'bg-sky-50',     fg: 'text-sky-600' },
  indigo:  { bg: 'bg-indigo-50',  fg: 'text-indigo-600' },
  emerald: { bg: 'bg-emerald-50', fg: 'text-emerald-600' },
  rose:    { bg: 'bg-rose-50',    fg: 'text-rose-600' },
};

function StatCard({
  label, value, icon: IconCmp, color,
}: {
  label: string;
  value: number | string;
  icon: (props: { className?: string }) => JSX.Element;
  color: keyof typeof COLOR_MAP;
}) {
  const c = COLOR_MAP[color];
  return (
    <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-4 hover:shadow-md transition-shadow">
      <div className="flex items-start justify-between">
        <div className="min-w-0">
          <p className="text-xs text-gray-500 font-medium">{label}</p>
          <p className="text-2xl font-bold text-gray-900 mt-1.5 truncate">{value}</p>
        </div>
        <div className={`${c.bg} ${c.fg} w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ml-2`}>
          <IconCmp className="w-5 h-5" />
        </div>
      </div>
    </div>
  );
}

// --- Inline SVG icons (no external dep) ---

function Svg({ className, d }: { className?: string; d: string }) {
  return (
    <svg className={className} xmlns="http://www.w3.org/2000/svg" fill="none"
         viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.8"
         strokeLinecap="round" strokeLinejoin="round">
      <path d={d} />
    </svg>
  );
}
const IconOrders   = (p: { className?: string }) => <Svg {...p} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l-1.5 11h-11L5 9z" />;
const IconClock    = (p: { className?: string }) => <Svg {...p} d="M12 8v4l3 2M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />;
const IconTruck    = (p: { className?: string }) => <Svg {...p} d="M3 7h11v9H3V7zm11 4h4l3 3v2h-7v-5zM7 19a2 2 0 100-4 2 2 0 000 4zm10 0a2 2 0 100-4 2 2 0 000 4z" />;
const IconMoney    = (p: { className?: string }) => <Svg {...p} d="M12 4v16M8 8a3 3 0 116 0 3 3 0 01-3 3 3 3 0 00-3 3 3 3 0 106 0" />;
const IconBike     = (p: { className?: string }) => <Svg {...p} d="M5 18a3 3 0 100-6 3 3 0 000 6zm14 0a3 3 0 100-6 3 3 0 000 6zM5 15l4-8h6l4 8M9 7h6" />;
const IconUserPlus = (p: { className?: string }) => <Svg {...p} d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zm10-3v6m-3-3h6" />;
const IconCheck    = (p: { className?: string }) => <Svg {...p} d="M5 13l4 4L19 7" />;
const IconX        = (p: { className?: string }) => <Svg {...p} d="M6 6l12 12M6 18L18 6" />;

function toIso(d: Date): string {
  // "yyyy-MM-dd" in local time (matches backend's LocalDate semantics)
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
