import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchShipperLedger, fetchEarningsSummary, formatVnd, type LedgerEntryType } from '@shop/shared';
import { api } from '@/lib/api';

type Filter = 'all' | LedgerEntryType;
const LABEL: Record<LedgerEntryType, string> = {
  COMMISSION: 'Hoa hồng',
  COD_OWED: 'COD đã thu',
  SETTLEMENT_PAYOUT: 'Shop trả lương',
  SETTLEMENT_DEPOSIT: 'Đã nộp tiền',
};

export function WalletPage() {
  const [filter, setFilter] = useState<Filter>('all');
  const { data: summary } = useQuery({
    queryKey: ['shipper', 'earnings', 'summary'],
    queryFn: () => fetchEarningsSummary(api),
  });
  const { data } = useQuery({
    queryKey: ['shipper', 'ledger', 0],
    queryFn: () => fetchShipperLedger(api, 0, 50),
  });
  const entries = (data?.content ?? []).filter(
    e => filter === 'all' || e.entryType === filter,
  );

  return (
    <div className="p-4 space-y-4">
      <header><h1 className="text-xl font-bold">👛 Ví của bạn</h1></header>

      <section className="bg-white rounded-2xl p-5 text-center shadow-sm">
        <p className="text-xs text-gray-500">Số dư hiện tại</p>
        <p
          className={`text-3xl font-extrabold mt-1 ${
            (summary?.balance ?? 0) >= 0 ? 'text-[var(--brand-primary)]' : 'text-red-600'
          }`}
        >
          {summary ? formatVnd(summary.balance) : '...'}
        </p>
        <p className="text-xs text-gray-400 mt-2">
          {(summary?.balance ?? 0) >= 0
            ? 'Shop sẽ chuyển khoản cho bạn trong kỳ tới'
            : 'Bạn cần nộp số tiền này cho shop'}
        </p>
      </section>

      <div className="flex gap-2 overflow-x-auto pb-1 -mx-4 px-4">
        {(['all', 'COMMISSION', 'COD_OWED', 'SETTLEMENT_PAYOUT', 'SETTLEMENT_DEPOSIT'] as Filter[]).map(f => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            type="button"
            className={`text-xs px-3 py-1 rounded-full whitespace-nowrap ${
              filter === f
                ? 'bg-[var(--brand-primary)] text-white'
                : 'bg-white border border-gray-300 text-gray-700'
            }`}
          >
            {f === 'all' ? 'Tất cả' : LABEL[f as LedgerEntryType]}
          </button>
        ))}
      </div>

      <ul className="space-y-2">
        {entries.map(e => (
          <li key={e.id} className="bg-white rounded-xl p-3 flex justify-between">
            <div>
              <p className="text-sm">{e.note ?? LABEL[e.entryType]}</p>
              <p className="text-xs text-gray-400">
                {new Date(e.createdAt).toLocaleString('vi-VN')}
              </p>
            </div>
            <p className={`font-bold ${Number(e.amount) >= 0 ? 'text-green-700' : 'text-red-600'}`}>
              {Number(e.amount) >= 0 ? '+ ' : ''}{formatVnd(e.amount)}
            </p>
          </li>
        ))}
        {entries.length === 0 && (
          <li className="text-center text-sm text-gray-500 py-8">Chưa có giao dịch nào</li>
        )}
      </ul>
    </div>
  );
}
