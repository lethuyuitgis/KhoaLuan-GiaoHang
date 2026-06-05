import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { BarChart, Bar, XAxis, YAxis, ResponsiveContainer, Tooltip } from 'recharts';
import { fetchEarningsSummary, fetchDailyEarnings, formatVnd } from '@shop/shared';
import { api } from '@/lib/api';

export function EarningsPage() {
  const { data: summary, isLoading } = useQuery({
    queryKey: ['shipper', 'earnings', 'summary'],
    queryFn: () => fetchEarningsSummary(api),
  });

  const to = new Date();
  const from = new Date(to.getTime() - 6 * 86400_000);
  const { data: daily = [] } = useQuery({
    queryKey: ['shipper', 'earnings', 'daily', from.toISOString().slice(0, 10)],
    queryFn: () => fetchDailyEarnings(api, from.toISOString(), to.toISOString()),
  });

  if (isLoading || !summary) return <p className="p-4">Đang tải…</p>;

  return (
    <div className="p-4 space-y-4">
      <header>
        <p className="text-sm text-gray-500">Chào shipper 👋</p>
      </header>

      <section className="bg-gradient-to-br from-[var(--brand-primary)] to-[var(--brand-primary-dark,#ea580c)] text-white rounded-2xl p-5 shadow-lg">
        <p className="text-xs uppercase tracking-wide opacity-90">Hôm nay bạn kiếm được</p>
        <p className="text-4xl font-extrabold mt-1">{formatVnd(summary.today)}</p>
        <p className="text-sm opacity-90 mt-1">{summary.todayOrders} đơn</p>
      </section>

      <section className="grid grid-cols-2 gap-3">
        <Kpi label="Tuần này" value={formatVnd(summary.week)} sub={`${summary.weekOrders} đơn`} />
        <Kpi label="Tháng này" value={formatVnd(summary.month)} />
        <Kpi
          label="Số dư ví"
          value={formatVnd(summary.balance)}
          sub={summary.balance >= 0 ? 'Shop nợ' : 'Bạn nợ'}
        />
      </section>

      <section className="bg-white rounded-2xl p-4">
        <p className="font-semibold mb-2 text-sm">Thu nhập 7 ngày qua</p>
        {daily.length === 0 ? (
          <p className="text-xs text-gray-500 py-6 text-center">Chưa có thu nhập trong 7 ngày qua</p>
        ) : (
          <ResponsiveContainer width="100%" height={140}>
            <BarChart data={daily}>
              <XAxis dataKey="date" fontSize={10} tickFormatter={d => d.slice(5)} />
              <YAxis fontSize={10} tickFormatter={n => `${Math.round(n / 1000)}k`} />
              <Tooltip formatter={(v) => formatVnd(Number(v))} />
              <Bar dataKey="commission" fill="var(--brand-primary, #f97316)" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </section>

      <Link
        to="/shipper/earnings/history"
        className="block text-center text-sm text-[var(--brand-primary)] py-2"
      >
        Xem chi tiết theo ngày →
      </Link>
    </div>
  );
}

function Kpi({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="bg-white rounded-xl p-3 shadow-sm">
      <p className="text-xs text-gray-500">{label}</p>
      <p className="text-lg font-bold mt-1">{value}</p>
      {sub && <p className="text-xs text-gray-400 mt-0.5">{sub}</p>}
    </div>
  );
}
