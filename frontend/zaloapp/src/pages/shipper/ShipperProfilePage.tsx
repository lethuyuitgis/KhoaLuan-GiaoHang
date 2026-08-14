import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { fetchShipperSelfProfile, fetchEarningsSummary, formatVnd } from '@shop/shared';
import { api } from '@/lib/api';

export function ShipperProfilePage() {
  const { i18n } = useTranslation();
  const { data: p, isLoading } = useQuery({
    queryKey: ['shipper', 'profile'],
    queryFn: () => fetchShipperSelfProfile(api),
  });
  const { data: summary } = useQuery({
    queryKey: ['shipper', 'earnings', 'summary'],
    queryFn: () => fetchEarningsSummary(api),
  });

  if (isLoading || !p) {
    return (
      <div className="px-4 py-6 space-y-3 animate-pulse">
        <div className="h-48 rounded-3xl bg-amber-200/40" />
        <div className="grid grid-cols-2 gap-3">
          <div className="h-20 rounded-xl bg-white/80" />
          <div className="h-20 rounded-xl bg-white/80" />
          <div className="h-20 rounded-xl bg-white/80" />
          <div className="h-20 rounded-xl bg-white/80" />
        </div>
      </div>
    );
  }

  const rating = Number(p.ratingAvg);
  const tier =
    rating >= 4.5
      ? { label: 'Top shipper', color: 'bg-yellow-100 text-yellow-700' }
      : rating >= 4.0
      ? { label: 'Shipper tốt', color: 'bg-emerald-100 text-emerald-700' }
      : rating >= 3.5
      ? { label: 'Shipper mới', color: 'bg-sky-100 text-sky-700' }
      : { label: 'Cần cải thiện', color: 'bg-gray-100 text-gray-700' };
  const initials = p.name
    .split(' ')
    .slice(-2)
    .map(s => s[0])
    .join('')
    .toUpperCase();
  const memberDays = Math.max(
    1,
    Math.floor((Date.now() - new Date(p.joinedAt).getTime()) / 86400_000),
  );

  return (
    <div className="pb-6">
      {/* ─────────── Profile hero with avatar ring ─────────── */}
      <header className="relative overflow-hidden">
        <div className="bg-gradient-to-br from-[var(--brand-primary)] to-[var(--brand-primary-dark,#9c3f0a)] px-5 pt-7 pb-16 text-white">
          <svg
            className="absolute -top-8 -left-8 w-44 h-44 opacity-15"
            viewBox="0 0 100 100"
            fill="none"
            aria-hidden
          >
            <circle cx="50" cy="50" r="38" stroke="white" strokeWidth="1" />
            <circle cx="50" cy="50" r="26" stroke="white" strokeWidth="1" />
          </svg>

          <div className="relative flex flex-col items-center">
            {/* Avatar with ring */}
            <div className="relative">
              <div className="absolute -inset-1.5 rounded-full bg-gradient-to-tr from-yellow-300 via-amber-200 to-orange-300 blur-[2px]" />
              <div className="relative w-24 h-24 rounded-full bg-white/95 flex items-center justify-center shadow-lg">
                <span className="text-3xl font-extrabold text-[var(--brand-primary-dark,#9c3f0a)]">
                  {initials || '🛵'}
                </span>
              </div>
            </div>

            <h2 className="text-2xl font-bold mt-3">{p.name}</h2>
            <p className="text-sm opacity-90">{p.phone}</p>

            <div className="flex items-center gap-2 mt-3">
              <span className="inline-flex items-center gap-1 text-yellow-200 font-semibold">
                <Star /> {rating.toFixed(2)}
              </span>
              <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${tier.color}`}>
                {tier.label}
              </span>
            </div>
          </div>
        </div>
      </header>

      {/* ─────────── Stats grid (4) ─────────── */}
      <section className="px-4 -mt-10 relative z-10 grid grid-cols-2 gap-3">
        <Stat
          icon="📦"
          label="Tổng đơn"
          value={p.totalOrders.toString()}
          tone="bg-emerald-50"
        />
        <Stat
          icon="⭐"
          label="Đánh giá"
          value={`${rating.toFixed(1)}/5`}
          tone="bg-yellow-50"
        />
        <Stat
          icon="💰"
          label="Thu nhập tháng"
          value={summary ? formatVnd(summary.month, i18n.language) : '—'}
          tone="bg-orange-50"
        />
        <Stat
          icon="🗓"
          label="Đã tham gia"
          value={`${memberDays} ngày`}
          tone="bg-sky-50"
        />
      </section>

      {/* ─────────── Rating bar visualization ─────────── */}
      <section className="mx-4 mt-5 bg-white rounded-2xl p-4 shadow-[0_2px_12px_rgba(0,0,0,0.04)]">
        <p className="font-semibold text-gray-900 text-sm mb-3">Mức đánh giá</p>
        <div className="flex items-center gap-2">
          {[1, 2, 3, 4, 5].map(i => (
            <div
              key={i}
              className={`flex-1 h-2 rounded-full ${
                i <= Math.round(rating) ? 'bg-yellow-400' : 'bg-gray-200'
              }`}
            />
          ))}
        </div>
        <p className="text-[11px] text-gray-500 mt-2">
          Dựa trên đánh giá của {p.totalOrders} đơn đã giao
        </p>
      </section>

      {/* ─────────── Info card ─────────── */}
      <section className="mx-4 mt-3 bg-white rounded-2xl divide-y divide-gray-100 shadow-[0_2px_12px_rgba(0,0,0,0.04)]">
        <InfoRow icon="📞" label="Số điện thoại" value={p.phone} />
        <InfoRow
          icon="📅"
          label="Ngày tham gia"
          value={new Date(p.joinedAt).toLocaleDateString('vi-VN')}
        />
      </section>
    </div>
  );
}

function Stat({
  icon,
  label,
  value,
  tone,
}: {
  icon: string;
  label: string;
  value: string;
  tone: string;
}) {
  return (
    <div className="bg-white rounded-2xl p-3.5 shadow-[0_2px_12px_rgba(0,0,0,0.04)]">
      <div className={`w-9 h-9 rounded-xl ${tone} flex items-center justify-center text-lg mb-2`}>
        {icon}
      </div>
      <p className="text-[11px] text-gray-500 font-medium">{label}</p>
      <p className="text-base font-bold mt-0.5 tabular-nums text-gray-900">{value}</p>
    </div>
  );
}

function InfoRow({
  icon,
  label,
  value,
}: {
  icon: string;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center gap-3 px-4 py-3">
      <span className="text-base">{icon}</span>
      <div className="flex-1 min-w-0">
        <p className="text-[11px] text-gray-500">{label}</p>
        <p className="text-sm text-gray-900 truncate">{value}</p>
      </div>
    </div>
  );
}

function Star() {
  return (
    <svg viewBox="0 0 24 24" className="w-4 h-4" fill="currentColor" aria-hidden>
      <path d="M12 2l2.9 6.1 6.6.8-4.9 4.6 1.3 6.7L12 17l-5.9 3.2 1.3-6.7L2.5 8.9l6.6-.8L12 2z" />
    </svg>
  );
}
