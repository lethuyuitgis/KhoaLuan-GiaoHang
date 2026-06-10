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

  if (isLoading || !data) {
    return (
      <div className="space-y-4 animate-pulse">
        <div className="h-24 bg-white rounded-2xl" />
        <div className="grid grid-cols-4 gap-3">
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="h-24 bg-white rounded-xl" />
          ))}
        </div>
      </div>
    );
  }

  const s = data.summary;
  const expired = new Date(s.validUntil) < new Date() || !s.active;
  const usagePct = s.maxUsesTotal ? Math.min(100, (s.usedCount / s.maxUsesTotal) * 100) : 0;
  const daysLeft = Math.ceil((new Date(s.validUntil).getTime() - Date.now()) / 86400_000);
  const totalSaved = data.recentRedemptions.reduce((sum, r) => sum + Number(r.discountApplied), 0);

  return (
    <div className="space-y-6">
      {/* ─────────── Back link ─────────── */}
      <Link to="/vouchers" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
        ← Tất cả voucher
      </Link>

      {/* ─────────── Hero ─────────── */}
      <header className="bg-white rounded-2xl border border-gray-200 p-6 flex items-center justify-between gap-4 flex-wrap">
        <div className="flex items-center gap-4">
          <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-orange-500 to-amber-600 flex items-center justify-center text-white text-2xl shadow-md">
            🎟️
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-2xl font-bold font-mono text-gray-900 tracking-tight">{s.code}</h1>
              <StatusPill expired={expired} />
            </div>
            <p className="text-sm text-gray-500 mt-1">{s.name}</p>
          </div>
        </div>
        <div className="flex gap-2">
          <Link
            to={`/vouchers/${id}/edit`}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-orange-500 hover:bg-orange-600 text-white rounded-xl font-semibold text-sm transition-colors"
          >
            <EditIcon /> Sửa
          </Link>
          <button
            onClick={() => window.confirm('Xoá voucher này?') && del.mutate()}
            disabled={del.isPending}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-white border border-red-200 hover:bg-red-50 text-red-700 rounded-xl font-semibold text-sm transition-colors disabled:opacity-50"
          >
            <TrashIcon /> Xoá
          </button>
        </div>
      </header>

      {/* ─────────── 4 stat cards ─────────── */}
      <div className="grid grid-cols-4 gap-3">
        <Stat
          icon="🎯"
          label="Đối tượng"
          value={s.target === 'SHIPPING' ? '🚚 Phí ship' : '🛍️ Tiền hàng'}
          tone={s.target === 'SHIPPING' ? 'bg-blue-50' : 'bg-purple-50'}
        />
        <Stat
          icon="💸"
          label="Mức giảm"
          value={
            s.discountType === 'FIXED'
              ? formatVnd(s.discountValue)
              : `${s.discountValue}%`
          }
          sub={s.discountType === 'PERCENT' && s.maxDiscount ? `Tối đa ${formatVnd(s.maxDiscount)}` : undefined}
          tone="bg-emerald-50"
        />
        <Stat
          icon="📊"
          label="Đã dùng"
          value={`${s.usedCount} / ${s.maxUsesTotal ?? '∞'}`}
          tone="bg-amber-50"
        />
        <Stat
          icon={expired ? '⏰' : '⏳'}
          label={expired ? 'Đã hết hạn' : 'Còn lại'}
          value={
            expired
              ? new Date(s.validUntil).toLocaleDateString('vi-VN')
              : `${daysLeft} ngày`
          }
          sub={!expired ? `Hết: ${new Date(s.validUntil).toLocaleDateString('vi-VN')}` : undefined}
          tone={expired ? 'bg-gray-100' : 'bg-orange-50'}
        />
      </div>

      {/* ─────────── Usage progress ─────────── */}
      {s.maxUsesTotal && (
        <div className="bg-white rounded-2xl border border-gray-200 p-5">
          <div className="flex items-baseline justify-between mb-2">
            <h2 className="font-semibold text-gray-900 text-sm">Tỉ lệ sử dụng</h2>
            <span className="text-xs text-gray-500 tabular-nums">
              {usagePct.toFixed(0)}% — {s.usedCount} / {s.maxUsesTotal} lượt
            </span>
          </div>
          <div className="h-3 bg-gray-100 rounded-full overflow-hidden">
            <div
              className={`h-full rounded-full transition-all ${
                usagePct >= 90
                  ? 'bg-gradient-to-r from-red-500 to-red-600'
                  : usagePct >= 60
                  ? 'bg-gradient-to-r from-amber-500 to-orange-500'
                  : 'bg-gradient-to-r from-emerald-500 to-teal-500'
              }`}
              style={{ width: `${Math.max(usagePct, 2)}%` }}
            />
          </div>
          <p className="text-xs text-gray-500 mt-2">
            Còn lại <strong className="text-gray-900">{(s.maxUsesTotal - s.usedCount)}</strong> lượt
            có thể áp · Tổng tiền đã giảm: <strong className="text-gray-900 tabular-nums">{formatVnd(totalSaved)}</strong>
          </p>
        </div>
      )}

      {/* ─────────── Redemptions table ─────────── */}
      <section>
        <div className="flex items-baseline justify-between mb-3">
          <h2 className="font-semibold text-gray-900">Lịch sử áp dụng</h2>
          <span className="text-xs text-gray-500">{data.recentRedemptions.length} lượt gần nhất</span>
        </div>
        {data.recentRedemptions.length === 0 ? (
          <div className="bg-white rounded-xl border border-gray-200 border-dashed py-12 text-center">
            <div className="text-4xl mb-2">🪐</div>
            <p className="text-sm font-medium text-gray-700">Chưa có ai áp voucher này</p>
            <p className="text-xs text-gray-500 mt-1">Khi khách nhập mã ở Checkout, lịch sử sẽ hiện ra đây</p>
          </div>
        ) : (
          <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
            <table className="w-full">
              <thead className="bg-gray-50/50 text-left text-[11px] uppercase tracking-wider text-gray-500 font-medium">
                <tr>
                  <th className="px-5 py-3">Thời gian</th>
                  <th className="px-5 py-3">Đơn</th>
                  <th className="px-5 py-3">Khách</th>
                  <th className="px-5 py-3 text-right">Giảm</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {data.recentRedemptions.map(r => (
                  <tr key={r.orderId} className="hover:bg-gray-50/50 transition-colors">
                    <td className="px-5 py-3 text-sm text-gray-600 tabular-nums">
                      {new Date(r.createdAt).toLocaleString('vi-VN')}
                    </td>
                    <td className="px-5 py-3 font-mono text-xs text-gray-700">
                      {r.orderId.slice(0, 8)}…
                    </td>
                    <td className="px-5 py-3 text-sm text-gray-700 font-mono">
                      #{r.customerId}
                    </td>
                    <td className="px-5 py-3 text-right font-semibold text-emerald-700 tabular-nums">
                      −{formatVnd(r.discountApplied)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

// ───────────── Components ─────────────

function Stat({
  icon,
  label,
  value,
  sub,
  tone,
}: {
  icon: string;
  label: string;
  value: string;
  sub?: string;
  tone: string;
}) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-4">
      <div className={`w-9 h-9 rounded-lg ${tone} flex items-center justify-center text-lg mb-2`}>
        {icon}
      </div>
      <p className="text-[11px] text-gray-500 font-medium uppercase tracking-wide">{label}</p>
      <p className="text-base font-bold mt-0.5 text-gray-900">{value}</p>
      {sub && <p className="text-[11px] text-gray-400 mt-0.5">{sub}</p>}
    </div>
  );
}

function StatusPill({ expired }: { expired: boolean }) {
  if (expired) {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-gray-600 bg-gray-100 px-2.5 py-1 rounded-full">
        <span className="w-1.5 h-1.5 bg-gray-400 rounded-full" />
        Đã hết
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-700 bg-emerald-50 px-2.5 py-1 rounded-full">
      <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full animate-pulse" />
      Đang chạy
    </span>
  );
}

function EditIcon() {
  return (
    <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 20h9M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="3 6 5 6 21 6" />
      <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
    </svg>
  );
}
