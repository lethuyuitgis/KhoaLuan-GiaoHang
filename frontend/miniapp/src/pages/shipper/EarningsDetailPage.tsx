import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchShipperLedger, formatVnd } from '@shop/shared';
import { api } from '@/lib/api';

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
        <h1 className="text-xl font-bold">Chi tiết thu nhập</h1>
      </header>
      <input
        type="date"
        value={date}
        onChange={e => setDate(e.target.value)}
        className="w-full px-3 py-2 rounded border bg-white"
      />
      <section className="bg-white rounded-2xl p-4 text-center">
        <p className="text-xs text-gray-500">Tổng hoa hồng ngày này</p>
        <p className="text-2xl font-bold text-[var(--brand-primary)] mt-1">{formatVnd(total)}</p>
        <p className="text-xs text-gray-400 mt-1">{dayEntries.length} đơn</p>
      </section>
      <ul className="space-y-2">
        {dayEntries.map(e => (
          <li key={e.id} className="bg-white rounded-xl p-3 flex justify-between">
            <div>
              <p className="text-sm">{e.note}</p>
              <p className="text-xs text-gray-400">
                {new Date(e.createdAt).toLocaleTimeString('vi-VN')}
              </p>
            </div>
            <p className="font-bold text-green-700">+ {formatVnd(e.amount)}</p>
          </li>
        ))}
        {dayEntries.length === 0 && (
          <li className="text-center text-sm text-gray-500 py-6">
            Không có đơn nào hoàn tất hôm này
          </li>
        )}
      </ul>
    </div>
  );
}
