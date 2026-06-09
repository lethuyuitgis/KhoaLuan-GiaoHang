import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { BarChart, Bar, XAxis, YAxis, ResponsiveContainer, Tooltip, Cell } from 'recharts';
import { useTranslation } from 'react-i18next';
import {
  fetchEarningsSummary,
  fetchDailyEarnings,
  fetchShipperSelfProfile,
  formatVnd,
} from '@shop/shared';
import { api } from '@/lib/api';

const DAY_VI = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];

export function EarningsPage() {
  const { i18n } = useTranslation();
  const { data: summary, isLoading } = useQuery({
    queryKey: ['shipper', 'earnings', 'summary'],
    queryFn: () => fetchEarningsSummary(api),
  });
  const { data: profile } = useQuery({
    queryKey: ['shipper', 'profile'],
    queryFn: () => fetchShipperSelfProfile(api),
  });

  const to = new Date();
  const from = new Date(to.getTime() - 6 * 86400_000);
  const { data: daily = [] } = useQuery({
    queryKey: ['shipper', 'earnings', 'daily', from.toISOString().slice(0, 10)],
    queryFn: () => fetchDailyEarnings(api, from.toISOString(), to.toISOString()),
  });

  if (isLoading || !summary) {
    return <Skeleton />;
  }

  // Build a 7-day window with zero-fill so the chart has consistent x-axis.
  const dailyMap = new Map(daily.map(d => [d.date, d.commission]));
  const chart: { day: string; value: number; isToday: boolean }[] = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(to.getTime() - i * 86400_000);
    const key = d.toISOString().slice(0, 10);
    chart.push({
      day: DAY_VI[d.getDay()],
      value: Number(dailyMap.get(key) ?? 0),
      isToday: i === 0,
    });
  }

  const peak = chart.reduce((m, c) => (c.value > m.value ? c : m), chart[0]);
  const firstName = profile?.name?.split(' ')[0] ?? 'shipper';

  return (
    <div className="pb-6">
      {/* ─────────────── Hero with decorative pattern ─────────────── */}
      <header className="relative overflow-hidden">
        <div
          className="bg-gradient-to-br from-[var(--brand-primary)] via-[var(--brand-primary)] to-[var(--brand-primary-dark,#9c3f0a)] text-white px-5 pt-6 pb-12"
        >
          {/* Decorative shapes */}
          <svg
            className="absolute -top-8 -right-12 w-48 h-48 opacity-20"
            viewBox="0 0 100 100"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            aria-hidden
          >
            <circle cx="50" cy="50" r="40" stroke="white" strokeWidth="1" />
            <circle cx="50" cy="50" r="30" stroke="white" strokeWidth="1" />
            <circle cx="50" cy="50" r="20" stroke="white" strokeWidth="1" />
          </svg>
          <svg
            className="absolute -bottom-6 -left-6 w-32 h-32 opacity-15"
            viewBox="0 0 100 100"
            fill="white"
            aria-hidden
          >
            <path d="M0,100 Q50,50 100,100 Z" />
          </svg>

          <div className="relative">
            <p className="text-xs opacity-80">Chào buổi sáng,</p>
            <h1 className="text-2xl font-bold">{firstName} 👋</h1>
          </div>

          <div className="relative mt-5">
            <p className="text-[11px] uppercase tracking-wider opacity-80">
              Thu nhập hôm nay
            </p>
            <p className="text-4xl font-extrabold leading-tight mt-1 tabular-nums">
              {formatVnd(summary.today, i18n.language)}
            </p>
            <div className="flex items-center gap-1.5 mt-1.5 text-sm opacity-90">
              <Badge>{summary.todayOrders} đơn</Badge>
              {summary.balance !== 0 && (
                <Badge variant={summary.balance > 0 ? 'positive' : 'negative'}>
                  Ví: {formatVnd(summary.balance, i18n.language)}
                </Badge>
              )}
            </div>
          </div>
        </div>
      </header>

      {/* ─────────────── KPI tiles (4) — overlap hero ─────────────── */}
      <section className="px-4 -mt-6 relative z-10 grid grid-cols-2 gap-3">
        <Kpi
          label="Tuần này"
          value={formatVnd(summary.week, i18n.language)}
          sub={`${summary.weekOrders} đơn`}
          accent="bg-amber-50 text-amber-700"
        />
        <Kpi
          label="Tháng này"
          value={formatVnd(summary.month, i18n.language)}
          accent="bg-emerald-50 text-emerald-700"
        />
        <Kpi
          label="Rating"
          value={`★ ${Number(profile?.ratingAvg ?? 0).toFixed(2)}`}
          sub={`${profile?.totalOrders ?? 0} đơn đã giao`}
          accent="bg-yellow-50 text-yellow-700"
        />
        <Kpi
          label="Số dư ví"
          value={formatVnd(summary.balance, i18n.language)}
          sub={summary.balance >= 0 ? 'Shop nợ bạn' : 'Bạn nợ shop'}
          accent={summary.balance >= 0 ? 'bg-sky-50 text-sky-700' : 'bg-red-50 text-red-700'}
        />
      </section>

      {/* ─────────────── 7-day chart ─────────────── */}
      <section className="mx-4 mt-5 bg-white rounded-2xl p-4 shadow-[0_2px_12px_rgba(0,0,0,0.04)]">
        <div className="flex items-baseline justify-between mb-1">
          <p className="font-semibold text-gray-900 text-sm">Thu nhập 7 ngày qua</p>
          {peak.value > 0 && (
            <p className="text-xs text-gray-500">
              Cao nhất: <span className="font-semibold text-[var(--brand-primary)]">{peak.day}</span>
            </p>
          )}
        </div>
        {chart.every(c => c.value === 0) ? (
          <p className="text-xs text-gray-500 py-8 text-center">
            Chưa có thu nhập trong 7 ngày qua
          </p>
        ) : (
          <ResponsiveContainer width="100%" height={160}>
            <BarChart data={chart} margin={{ top: 18, right: 4, left: -16, bottom: 0 }}>
              <XAxis
                dataKey="day"
                axisLine={false}
                tickLine={false}
                tick={{ fontSize: 11, fill: '#6b7280' }}
              />
              <YAxis hide />
              <Tooltip
                cursor={{ fill: 'rgba(0,0,0,0.04)' }}
                contentStyle={{
                  borderRadius: 10,
                  border: 'none',
                  boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
                  fontSize: 12,
                }}
                formatter={(v) => [formatVnd(Number(v ?? 0), i18n.language), 'Hoa hồng']}
              />
              <Bar dataKey="value" radius={[10, 10, 4, 4]} maxBarSize={28}>
                {chart.map((c, i) => (
                  <Cell
                    key={i}
                    fill={c.isToday ? 'var(--brand-primary)' : 'var(--brand-primary-light,#fdba74)'}
                  />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        )}
      </section>

      {/* ─────────────── Quick actions ─────────────── */}
      <section className="px-4 mt-5 grid grid-cols-2 gap-3">
        <QuickAction
          to="/shipper/assignments"
          icon="📋"
          title="Xem đơn"
          subtitle="Đang chờ giao"
          tone="amber"
        />
        <QuickAction
          to="/shipper/earnings/history"
          icon="📊"
          title="Lịch sử"
          subtitle="Theo từng ngày"
          tone="emerald"
        />
      </section>
    </div>
  );
}

function Skeleton() {
  return (
    <div className="px-4 py-6 space-y-3 animate-pulse">
      <div className="h-44 rounded-3xl bg-amber-200/40" />
      <div className="grid grid-cols-2 gap-3">
        <div className="h-20 rounded-xl bg-white/80" />
        <div className="h-20 rounded-xl bg-white/80" />
        <div className="h-20 rounded-xl bg-white/80" />
        <div className="h-20 rounded-xl bg-white/80" />
      </div>
      <div className="h-44 rounded-2xl bg-white/80" />
    </div>
  );
}

function Badge({
  children,
  variant = 'neutral',
}: {
  children: React.ReactNode;
  variant?: 'neutral' | 'positive' | 'negative';
}) {
  const tone =
    variant === 'positive'
      ? 'bg-emerald-400/30 text-white'
      : variant === 'negative'
      ? 'bg-red-400/30 text-white'
      : 'bg-white/20 text-white';
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${tone}`}>
      {children}
    </span>
  );
}

function Kpi({
  label,
  value,
  sub,
  accent,
}: {
  label: string;
  value: string;
  sub?: string;
  accent: string;
}) {
  return (
    <div className="bg-white rounded-2xl p-3.5 shadow-[0_2px_12px_rgba(0,0,0,0.04)]">
      <span className={`inline-block text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 rounded-full ${accent}`}>
        {label}
      </span>
      <p className="text-lg font-bold mt-2 tabular-nums text-gray-900">{value}</p>
      {sub && <p className="text-[11px] text-gray-500 mt-0.5">{sub}</p>}
    </div>
  );
}

function QuickAction({
  to,
  icon,
  title,
  subtitle,
  tone,
}: {
  to: string;
  icon: string;
  title: string;
  subtitle: string;
  tone: 'amber' | 'emerald';
}) {
  const bg = tone === 'amber' ? 'bg-amber-100' : 'bg-emerald-100';
  return (
    <Link
      to={to}
      className="bg-white rounded-2xl p-3.5 shadow-[0_2px_12px_rgba(0,0,0,0.04)] flex items-center gap-3 active:scale-[0.98] transition"
    >
      <div className={`w-10 h-10 rounded-xl ${bg} flex items-center justify-center text-xl`}>
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-gray-900">{title}</p>
        <p className="text-[11px] text-gray-500 truncate">{subtitle}</p>
      </div>
      <span className="text-gray-400">›</span>
    </Link>
  );
}
