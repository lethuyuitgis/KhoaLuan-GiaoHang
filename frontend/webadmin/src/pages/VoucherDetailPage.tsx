import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchAdminVoucherDetail, deleteVoucher, formatVnd } from '@shop/shared';
import { api } from '@/lib/api';

export function VoucherDetailPage() {
  const { id } = useParams();
  const nav = useNavigate();
  const qc = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'voucher', id],
    queryFn: () => fetchAdminVoucherDetail(api, Number(id)),
  });

  const del = useMutation({
    mutationFn: () => deleteVoucher(api, Number(id)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'vouchers'] });
      nav('/vouchers');
    },
  });

  if (isLoading || !data) return <p className="text-gray-500">Đang tải…</p>;

  const s = data.summary;

  return (
    <div>
      <header className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold font-mono text-gray-900">{s.code}</h1>
          <p className="text-sm text-gray-500 mt-1">{s.name}</p>
        </div>
        <div className="flex gap-2">
          <Link
            to={`/vouchers/${id}/edit`}
            className="px-4 py-2 bg-orange-500 hover:bg-orange-600 text-white rounded-lg font-semibold transition-colors"
          >
            Sửa
          </Link>
          <button
            onClick={() => {
              if (window.confirm('Xoá voucher này?')) del.mutate();
            }}
            disabled={del.isPending}
            className="px-4 py-2 bg-red-100 hover:bg-red-200 text-red-700 rounded-lg font-semibold transition-colors disabled:opacity-50"
          >
            Xoá
          </button>
        </div>
      </header>

      <div className="grid grid-cols-2 gap-4 mb-6">
        <Card
          label="Đối tượng"
          value={s.target === 'SHIPPING' ? '🚚 Phí ship' : '🛍️ Tiền hàng'}
        />
        <Card
          label="Mức giảm"
          value={
            s.discountType === 'FIXED'
              ? formatVnd(s.discountValue)
              : `${s.discountValue}%${s.maxDiscount ? ` (cap ${formatVnd(s.maxDiscount)})` : ''}`
          }
        />
        <Card label="Đã dùng" value={`${s.usedCount} / ${s.maxUsesTotal ?? '∞'}`} />
        <Card label="Hết hạn" value={new Date(s.validUntil).toLocaleDateString('vi-VN')} />
      </div>

      <h2 className="font-semibold text-gray-900 mb-3">
        Lịch sử áp ({data.recentRedemptions.length})
      </h2>
      <table className="w-full bg-white rounded-xl border border-gray-200">
        <thead className="bg-gray-50 text-left text-xs uppercase">
          <tr>
            <th className="px-4 py-3">Thời gian</th>
            <th className="px-4 py-3">Đơn</th>
            <th className="px-4 py-3">Giảm</th>
          </tr>
        </thead>
        <tbody>
          {data.recentRedemptions.map(r => (
            <tr key={r.orderId} className="border-t border-gray-100">
              <td className="px-4 py-3 text-sm">
                {new Date(r.createdAt).toLocaleString('vi-VN')}
              </td>
              <td className="px-4 py-3 font-mono text-xs">{r.orderId.slice(0, 8)}…</td>
              <td className="px-4 py-3">{formatVnd(r.discountApplied)}</td>
            </tr>
          ))}
          {data.recentRedemptions.length === 0 && (
            <tr>
              <td colSpan={3} className="text-center py-6 text-gray-500">
                Chưa có ai áp voucher này
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function Card({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-white border border-gray-200 rounded-xl p-4">
      <p className="text-xs text-gray-500">{label}</p>
      <p className="text-lg font-semibold mt-1 text-gray-900">{value}</p>
    </div>
  );
}
