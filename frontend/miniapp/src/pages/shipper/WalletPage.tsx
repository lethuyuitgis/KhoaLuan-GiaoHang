import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  fetchShipperLedger,
  fetchEarningsSummary,
  formatVnd,
  type LedgerEntryType,
  type LedgerRow,
} from '@shop/shared';
import { api } from '@/lib/api';

type Filter = 'all' | LedgerEntryType;

const META: Record<LedgerEntryType, { label: string; icon: string; tone: string }> = {
  COMMISSION:         { label: 'Hoa hồng',       icon: '💰', tone: 'bg-emerald-100 text-emerald-700' },
  COD_OWED:           { label: 'COD đã thu',     icon: '🚚', tone: 'bg-red-100 text-red-700' },
  SETTLEMENT_PAYOUT:  { label: 'Shop trả lương', icon: '✅', tone: 'bg-red-100 text-red-700' },
  SETTLEMENT_DEPOSIT: { label: 'Đã nộp tiền',    icon: '📥', tone: 'bg-emerald-100 text-emerald-700' },
};

const FILTER_ORDER: Filter[] = ['all', 'COMMISSION', 'COD_OWED', 'SETTLEMENT_PAYOUT', 'SETTLEMENT_DEPOSIT'];

export function WalletPage() {
  const { i18n } = useTranslation();
  const [filter, setFilter] = useState<Filter>('all');

  const { data: summary } = useQuery({
    queryKey: ['shipper', 'earnings', 'summary'],
    queryFn: () => fetchEarningsSummary(api),
  });
  const { data } = useQuery({
    queryKey: ['shipper', 'ledger', 0],
    queryFn: () => fetchShipperLedger(api, 0, 50),
  });

  const filtered = useMemo(
    () => (data?.content ?? []).filter(e => filter === 'all' || e.entryType === filter),
    [data, filter],
  );

  // Group by day for sticky day headers
  const grouped = useMemo(() => groupByDay(filtered), [filtered]);

  const balance = summary?.balance ?? 0;
  const positive = balance >= 0;

  return (
    <div className="pb-6">
      {/* ─────────── Balance hero ─────────── */}
      <header className="relative overflow-hidden">
        <div
          className={`px-5 pt-6 pb-12 text-white ${
            positive
              ? 'bg-gradient-to-br from-[var(--brand-primary)] to-[var(--brand-primary-dark,#9c3f0a)]'
              : 'bg-gradient-to-br from-rose-500 to-red-700'
          }`}
        >
          <svg
            className="absolute -top-6 -right-6 w-40 h-40 opacity-20"
            viewBox="0 0 100 100"
            fill="none"
            aria-hidden
          >
            <circle cx="50" cy="50" r="36" stroke="white" strokeWidth="1" />
            <circle cx="50" cy="50" r="24" stroke="white" strokeWidth="1" />
          </svg>

          <div className="relative">
            <div className="flex items-center gap-2 mb-1">
              <span className="text-2xl">👛</span>
              <h1 className="text-lg font-semibold">Ví của bạn</h1>
            </div>
            <p className="text-[11px] uppercase tracking-wider opacity-80 mt-3">
              Số dư hiện tại
            </p>
            <p className="text-4xl font-extrabold leading-tight mt-1 tabular-nums">
              {summary ? formatVnd(balance, i18n.language) : '…'}
            </p>
            <p className="text-xs opacity-90 mt-1.5">
              {positive
                ? '✓ Shop sẽ chuyển khoản cho bạn trong kỳ tới'
                : 'ⓘ Bạn cần nộp số tiền này cho shop'}
            </p>
          </div>
        </div>
      </header>

      {/* ─────────── Filter chips ─────────── */}
      <div className="px-4 -mt-6 relative z-10">
        <div className="bg-white rounded-2xl p-2 shadow-[0_2px_12px_rgba(0,0,0,0.06)] flex gap-1.5 overflow-x-auto">
          {FILTER_ORDER.map(f => {
            const active = filter === f;
            const meta = f !== 'all' ? META[f] : null;
            return (
              <button
                key={f}
                onClick={() => setFilter(f)}
                type="button"
                className={`text-xs px-3 py-1.5 rounded-full whitespace-nowrap font-medium transition-all flex items-center gap-1.5 ${
                  active
                    ? 'bg-[var(--brand-primary)] text-white shadow-sm'
                    : 'text-gray-600 hover:bg-gray-50'
                }`}
              >
                {meta && <span>{meta.icon}</span>}
                <span>{f === 'all' ? 'Tất cả' : meta!.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* ─────────── Ledger entries grouped by day ─────────── */}
      <div className="px-4 mt-4 space-y-5">
        {grouped.length === 0 && (
          <div className="bg-white rounded-2xl p-8 text-center">
            <p className="text-4xl mb-2">📭</p>
            <p className="text-sm text-gray-500">Chưa có giao dịch nào</p>
          </div>
        )}
        {grouped.map(g => {
          const dayTotal = g.entries.reduce((s, e) => s + Number(e.amount), 0);
          return (
            <section key={g.day}>
              <header className="flex items-baseline justify-between mb-2 px-1">
                <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
                  {g.label}
                </p>
                <p className={`text-xs font-bold tabular-nums ${dayTotal >= 0 ? 'text-emerald-700' : 'text-red-600'}`}>
                  {dayTotal >= 0 ? '+' : ''}{formatVnd(dayTotal, i18n.language)}
                </p>
              </header>
              <ul className="space-y-1.5">
                {g.entries.map(e => {
                  const meta = META[e.entryType];
                  const amount = Number(e.amount);
                  return (
                    <li
                      key={e.id}
                      className="bg-white rounded-xl px-3 py-2.5 flex items-center gap-3 shadow-[0_1px_3px_rgba(0,0,0,0.04)]"
                    >
                      <div className={`w-10 h-10 rounded-xl ${meta.tone} flex items-center justify-center text-lg shrink-0`}>
                        {meta.icon}
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-gray-900 truncate">
                          {e.note ?? meta.label}
                        </p>
                        <p className="text-[11px] text-gray-400 mt-0.5">
                          {meta.label} · {formatTime(e.createdAt)}
                        </p>
                      </div>
                      <p className={`font-bold tabular-nums whitespace-nowrap ${amount >= 0 ? 'text-emerald-700' : 'text-red-600'}`}>
                        {amount >= 0 ? '+' : ''}{formatVnd(amount, i18n.language)}
                      </p>
                    </li>
                  );
                })}
              </ul>
            </section>
          );
        })}
      </div>
    </div>
  );
}

// ─────────────── Helpers ───────────────

function groupByDay(entries: LedgerRow[]): { day: string; label: string; entries: LedgerRow[] }[] {
  const map = new Map<string, LedgerRow[]>();
  for (const e of entries) {
    const day = e.createdAt.slice(0, 10);
    if (!map.has(day)) map.set(day, []);
    map.get(day)!.push(e);
  }
  const today = new Date().toISOString().slice(0, 10);
  const yesterday = new Date(Date.now() - 86400_000).toISOString().slice(0, 10);
  return Array.from(map.entries()).map(([day, rows]) => ({
    day,
    label:
      day === today
        ? 'Hôm nay'
        : day === yesterday
        ? 'Hôm qua'
        : new Date(day).toLocaleDateString('vi-VN', {
            weekday: 'long',
            day: '2-digit',
            month: '2-digit',
          }),
    entries: rows,
  }));
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}
