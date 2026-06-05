import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchAdminShipperBalance, fetchAdminShipperLedger, settleShipper, formatVnd,
} from '@shop/shared';
import { api } from '@/lib/api';

export function ShipperDetailPage() {
  const { id } = useParams();
  const sid = Number(id);
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const [modal, setModal] = useState<null | { type: 'PAYOUT' | 'DEPOSIT' }>(null);

  const { data: bal } = useQuery({
    queryKey: ['admin', 'shipper', 'balance', sid],
    queryFn: () => fetchAdminShipperBalance(api, sid),
  });
  const { data: ledger } = useQuery({
    queryKey: ['admin', 'shipper', 'ledger', sid, page],
    queryFn: () => fetchAdminShipperLedger(api, sid, page, 20),
  });
  const settle = useMutation({
    mutationFn: ({ type, amount, note }: { type: 'PAYOUT' | 'DEPOSIT'; amount: number; note: string }) =>
      settleShipper(api, sid, { type, amount, note }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'shipper', 'balance', sid] });
      qc.invalidateQueries({ queryKey: ['admin', 'shipper', 'ledger', sid] });
      qc.invalidateQueries({ queryKey: ['admin', 'shipper-balances'] });
      setModal(null);
    },
  });

  const entries = ledger?.content ?? [];
  const totalPages = ledger ? Math.ceil(((ledger as any).totalElements ?? entries.length) / 20) : 1;

  return (
    <div>
      <header className="flex items-center justify-between mb-6">
        <div>
          <Link to="/shippers" className="text-sm text-gray-500">← Danh sách shipper</Link>
          <h1 className="text-2xl font-bold mt-1">Shipper #{sid}</h1>
        </div>
      </header>

      <div className="grid grid-cols-3 gap-4 mb-6">
        <div className="bg-white rounded-xl p-5 col-span-2">
          <p className="text-xs text-gray-500">Số dư hiện tại</p>
          <p className={`text-3xl font-bold mt-1 ${(bal?.balance ?? 0) >= 0 ? 'text-green-700' : 'text-red-600'}`}>
            {bal && formatVnd(bal.balance)}
          </p>
          <p className="text-xs text-gray-400 mt-1">
            {bal?.lastSettledAt
              ? `Đối soát lần cuối: ${new Date(bal.lastSettledAt).toLocaleDateString('vi-VN')}`
              : 'Chưa từng đối soát'}
          </p>
          <div className="flex gap-2 mt-4">
            <button onClick={() => setModal({ type: 'PAYOUT' })}
              className="px-3 py-1.5 rounded bg-orange-500 hover:bg-orange-600 text-white text-sm">
              Đã trả lương
            </button>
            <button onClick={() => setModal({ type: 'DEPOSIT' })}
              className="px-3 py-1.5 rounded bg-blue-500 hover:bg-blue-600 text-white text-sm">
              Đã nhận tiền nộp
            </button>
          </div>
        </div>
      </div>

      <h2 className="font-semibold mb-3">Lịch sử ledger</h2>
      <table className="w-full bg-white rounded-xl border border-gray-200">
        <thead className="bg-gray-50 text-left text-xs uppercase">
          <tr>
            <th className="px-4 py-3">Thời gian</th>
            <th className="px-4 py-3">Loại</th>
            <th className="px-4 py-3">Đơn</th>
            <th className="px-4 py-3">Ghi chú</th>
            <th className="px-4 py-3 text-right">Số tiền</th>
          </tr>
        </thead>
        <tbody>
          {entries.map(e => (
            <tr key={e.id} className="border-t border-gray-100">
              <td className="px-4 py-2 text-sm">{new Date(e.createdAt).toLocaleString('vi-VN')}</td>
              <td className="px-4 py-2 text-xs">{e.entryType}</td>
              <td className="px-4 py-2 font-mono text-xs">{e.orderId?.slice(0, 8) ?? '—'}</td>
              <td className="px-4 py-2 text-sm">{e.note}</td>
              <td className={`px-4 py-2 text-right font-bold ${Number(e.amount) >= 0 ? 'text-green-700' : 'text-red-600'}`}>
                {Number(e.amount) >= 0 ? '+' : ''}{formatVnd(e.amount)}
              </td>
            </tr>
          ))}
          {entries.length === 0 && (
            <tr><td colSpan={5} className="text-center py-6 text-gray-500">Chưa có giao dịch nào</td></tr>
          )}
        </tbody>
      </table>

      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-4 text-sm">
          <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
            className="px-3 py-1 rounded border disabled:opacity-50">‹</button>
          <span>Trang {page + 1} / {totalPages}</span>
          <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
            className="px-3 py-1 rounded border disabled:opacity-50">›</button>
        </div>
      )}

      {modal && (
        <SettleModal type={modal.type} onClose={() => setModal(null)}
          onSubmit={(amount, note) => settle.mutate({ type: modal.type, amount, note })}
          loading={settle.isPending} />
      )}
    </div>
  );
}

function SettleModal({ type, onClose, onSubmit, loading }: {
  type: 'PAYOUT' | 'DEPOSIT';
  onClose: () => void;
  onSubmit: (amount: number, note: string) => void;
  loading: boolean;
}) {
  const [amount, setAmount] = useState(0);
  const [note, setNote] = useState('');
  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={onClose}>
      <div className="bg-white rounded-2xl p-6 w-96 space-y-3" onClick={e => e.stopPropagation()}>
        <h3 className="font-bold text-lg">
          {type === 'PAYOUT' ? 'Đã trả lương shipper' : 'Đã nhận tiền nộp từ shipper'}
        </h3>
        <input type="number" min={0} value={amount || ''} onChange={e => setAmount(Number(e.target.value))}
          placeholder="Số tiền (đ)" className="w-full px-3 py-2 rounded border" />
        <input type="text" value={note} onChange={e => setNote(e.target.value)}
          placeholder="Ghi chú (tuỳ chọn, vd: chuyển khoản 2026-06-05)" className="w-full px-3 py-2 rounded border" />
        <div className="flex gap-2 justify-end pt-2">
          <button onClick={onClose} type="button" className="px-4 py-2 rounded bg-gray-200">Huỷ</button>
          <button onClick={() => onSubmit(amount, note)} disabled={amount <= 0 || loading} type="button"
            className="px-4 py-2 rounded bg-orange-500 text-white disabled:opacity-50">
            {loading ? '...' : 'Lưu'}
          </button>
        </div>
      </div>
    </div>
  );
}
