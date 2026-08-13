import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchShipperLedger, formatVnd } from '@shop/shared';
import { api } from '@/lib/api';

const CARD = 'bg-white rounded-2xl shadow-[0_2px_10px_rgba(0,0,0,0.04)] border border-black/[0.03]';

export function EarningsDetailPage() {
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const { data } = useQuery({
    queryKey: ['shipper', 'ledger', 'all'],
    queryFn: () => fetchShipperLedger(api, 0, 200),
  });
  const dayEntries = (data?.content ?? []).filter(
    e => e.entryType === 'COMMISSION' && e.createdAt.startsWith(date),
  );
  const total = dayEntries.reduce((s, e) => s + Number(e.amount), 0);

  return (
    <div className="p-4 space-y-4">
      <header>
        <h1 className="text-2xl font-bold text-gray-900">Chi tiết thu nhập</h1>
        <p className="text-xs text-gray-500 mt-0.5">Hoa hồng theo từng ngày</p>
      </header>

      <label className={`${CARD} flex items-center justify-between gap-3 px-4 py-3`}>
        <span className="text-[11px] font-semibold uppercase tracking-[0.08em] text-gray-400">Chọn ngày</span>
        <input
          type="date"
          value={date}
          onChange={e => setDate(e.target.value)}
          className="bg-transparent text-sm font-medium text-gray-900 focus:outline-none"
        />
      </label>

      {/* Tổng ngày — hero nhỏ theo brand cam */}
      <section className="rounded-2xl p-5 text-center text-white bg-gradient-to-br from-[var(--brand-primary)] to-[var(--brand-primary-dark,#9c3f0a)] shadow-lg shadow-orange-500/20">
        <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-amber-50/85">Tổng hoa hồng ngày này</p>
        <p className="text-3xl font-extrabold mt-1 tabular-nums">{formatVnd(total)}</p>
        <p className="text-xs text-amber-50/80 mt-1">{dayEntries.length} đơn hoàn tất</p>
      </section>

      <ul className="space-y-2">
        {dayEntries.map(e => (
          <li key={e.id} className={`${CARD} px-4 py-3 flex items-center justify-between gap-3`}>
            <div className="min-w-0">
              <p className="text-sm text-gray-900 truncate">{e.note ?? 'Hoa hồng đơn'}</p>
              <p className="text-[11px] text-gray-400 mt-0.5">
                {new Date(e.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
              </p>
            </div>
            <p className="shrink-0 font-bold text-emerald-600 tabular-nums">+{formatVnd(e.amount)}</p>
          </li>
        ))}
        {dayEntries.length === 0 && (
          <li className="text-center text-sm text-gray-500 py-10">
            Không có đơn nào hoàn tất trong ngày này
          </li>
        )}
      </ul>
    </div>
  );
}
