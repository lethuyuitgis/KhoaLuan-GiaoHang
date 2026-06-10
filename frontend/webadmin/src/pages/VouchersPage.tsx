import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchAdminVouchers, formatVnd, type VoucherTarget } from '@shop/shared';
import { api } from '@/lib/api';

type StatusFilter = 'all' | 'active' | 'expired';
type TargetFilter = 'all' | VoucherTarget;

const STATUS_LABEL: Record<StatusFilter, string> = {
  all: 'Tất cả',
  active: 'Đang chạy',
  expired: 'Đã hết',
};
const TARGET_LABEL: Record<TargetFilter, string> = {
  all: 'Tất cả',
  SHIPPING: '🚚 Phí ship',
  PRODUCTS: '🛍️ Tiền hàng',
};

export function VouchersPage() {
  const nav = useNavigate();
  const [status, setStatus] = useState<StatusFilter>('all');
  const [target, setTarget] = useState<TargetFilter>('all');
  const [page] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'vouchers', page],
    queryFn: () => fetchAdminVouchers(api, page, 20),
  });

  const allVouchers = data?.content ?? [];
  const filtered = allVouchers.filter(v => {
    const now = new Date();
    const expired = new Date(v.validUntil) < now || !v.active;
    if (status === 'active' && expired) return false;
    if (status === 'expired' && !expired) return false;
    if (target !== 'all' && v.target !== target) return false;
    return true;
  });

  const activeCount = allVouchers.filter(
    v => v.active && new Date(v.validUntil) >= new Date(),
  ).length;
  const totalRedeemed = allVouchers.reduce((s, v) => s + v.usedCount, 0);

  return (
    <div className="space-y-6">
      {/* ─────────── Header ─────────── */}
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 tracking-tight">Khuyến mãi</h1>
          <p className="text-sm text-gray-500 mt-1">
            Tạo và quản lý voucher cho khách hàng
          </p>
        </div>
        <Link
          to="/vouchers/new"
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-orange-500 hover:bg-orange-600 text-white font-semibold text-sm transition-colors shadow-sm"
        >
          <PlusIcon /> Tạo voucher
        </Link>
      </header>

      {/* ─────────── Summary stats ─────────── */}
      <div className="grid grid-cols-3 gap-4">
        <Stat
          icon="🎟️"
          label="Tổng voucher"
          value={allVouchers.length.toString()}
          accent="bg-blue-50 text-blue-700"
        />
        <Stat
          icon="✅"
          label="Đang chạy"
          value={activeCount.toString()}
          accent="bg-emerald-50 text-emerald-700"
        />
        <Stat
          icon="📊"
          label="Tổng lượt áp"
          value={totalRedeemed.toString()}
          accent="bg-amber-50 text-amber-700"
        />
      </div>

      {/* ─────────── Filters ─────────── */}
      <div className="bg-white rounded-xl border border-gray-200 p-3 flex flex-wrap gap-x-6 gap-y-2 items-center">
        <FilterGroup label="Trạng thái" value={status} options={['all', 'active', 'expired']} onChange={setStatus} labels={STATUS_LABEL} />
        <FilterGroup label="Đối tượng" value={target} options={['all', 'SHIPPING', 'PRODUCTS']} onChange={setTarget} labels={TARGET_LABEL} />
        <span className="text-xs text-gray-400 ml-auto">
          Hiển thị {filtered.length} / {allVouchers.length}
        </span>
      </div>

      {/* ─────────── Table ─────────── */}
      {isLoading ? (
        <SkeletonTable />
      ) : filtered.length === 0 ? (
        <EmptyState hasAny={allVouchers.length > 0} />
      ) : (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50/50 text-left text-[11px] uppercase tracking-wider text-gray-500 font-medium">
              <tr>
                <th className="px-5 py-3">Mã</th>
                <th className="px-5 py-3">Tên</th>
                <th className="px-5 py-3">Đối tượng</th>
                <th className="px-5 py-3">Mức giảm</th>
                <th className="px-5 py-3">Đã dùng</th>
                <th className="px-5 py-3">Hạn dùng</th>
                <th className="px-5 py-3">Trạng thái</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filtered.map(v => {
                const expired = new Date(v.validUntil) < new Date() || !v.active;
                const usagePct = v.maxUsesTotal
                  ? Math.min(100, (v.usedCount / v.maxUsesTotal) * 100)
                  : 0;
                return (
                  <tr
                    key={v.id}
                    className="hover:bg-orange-50/30 cursor-pointer transition-colors group"
                    onClick={() => nav(`/vouchers/${v.id}`)}
                  >
                    <td className="px-5 py-4">
                      <span className="font-mono text-sm font-semibold text-gray-900 bg-gray-100 group-hover:bg-orange-100 px-2 py-1 rounded-md transition-colors">
                        {v.code}
                      </span>
                    </td>
                    <td className="px-5 py-4 text-sm text-gray-900 max-w-xs truncate">
                      {v.name}
                    </td>
                    <td className="px-5 py-4 text-sm">
                      {v.target === 'SHIPPING' ? (
                        <span className="inline-flex items-center gap-1.5 text-blue-700">
                          🚚 Phí ship
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1.5 text-purple-700">
                          🛍️ Tiền hàng
                        </span>
                      )}
                    </td>
                    <td className="px-5 py-4 text-sm font-medium text-gray-900 tabular-nums">
                      {v.discountType === 'FIXED'
                        ? formatVnd(v.discountValue)
                        : (
                          <>
                            {v.discountValue}%
                            {v.maxDiscount && (
                              <span className="text-gray-400 text-xs ml-1">
                                ≤ {formatVnd(v.maxDiscount)}
                              </span>
                            )}
                          </>
                        )}
                    </td>
                    <td className="px-5 py-4">
                      <div className="flex flex-col gap-1 min-w-[100px]">
                        <span className="text-xs tabular-nums text-gray-700">
                          {v.usedCount} / {v.maxUsesTotal ?? '∞'}
                        </span>
                        {v.maxUsesTotal && (
                          <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden">
                            <div
                              className={`h-full transition-all ${
                                usagePct >= 90 ? 'bg-red-500' : usagePct >= 60 ? 'bg-amber-500' : 'bg-emerald-500'
                              }`}
                              style={{ width: `${usagePct}%` }}
                            />
                          </div>
                        )}
                      </div>
                    </td>
                    <td className="px-5 py-4 text-sm text-gray-600 tabular-nums">
                      {new Date(v.validUntil).toLocaleDateString('vi-VN', {
                        day: '2-digit',
                        month: '2-digit',
                        year: 'numeric',
                      })}
                    </td>
                    <td className="px-5 py-4">
                      <StatusPill expired={expired} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// ───────────── Components ─────────────

function Stat({
  icon,
  label,
  value,
  accent,
}: {
  icon: string;
  label: string;
  value: string;
  accent: string;
}) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-4 flex items-center gap-3">
      <div className={`w-10 h-10 rounded-xl ${accent} flex items-center justify-center text-xl`}>
        {icon}
      </div>
      <div>
        <p className="text-xs text-gray-500 font-medium">{label}</p>
        <p className="text-xl font-bold tabular-nums text-gray-900">{value}</p>
      </div>
    </div>
  );
}

function FilterGroup<T extends string>({
  label,
  value,
  options,
  onChange,
  labels,
}: {
  label: string;
  value: T;
  options: T[];
  onChange: (v: T) => void;
  labels: Record<T, string>;
}) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-xs text-gray-500 font-medium">{label}:</span>
      <div className="flex gap-1 bg-gray-50 p-1 rounded-lg">
        {options.map(o => (
          <button
            key={o}
            onClick={() => onChange(o)}
            type="button"
            className={`text-xs px-3 py-1 rounded-md font-medium transition-all ${
              value === o
                ? 'bg-white text-gray-900 shadow-sm'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {labels[o]}
          </button>
        ))}
      </div>
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

function PlusIcon() {
  return (
    <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

function SkeletonTable() {
  return (
    <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
      <div className="bg-gray-50/50 h-10" />
      {[1, 2, 3].map(i => (
        <div key={i} className="border-t border-gray-100 h-14 flex items-center px-5 gap-4 animate-pulse">
          <div className="h-6 w-20 bg-gray-100 rounded-md" />
          <div className="h-4 w-32 bg-gray-100 rounded" />
          <div className="h-4 w-20 bg-gray-100 rounded ml-auto" />
        </div>
      ))}
    </div>
  );
}

function EmptyState({ hasAny }: { hasAny: boolean }) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 border-dashed py-16 text-center">
      <div className="text-5xl mb-3">{hasAny ? '🔍' : '🎟️'}</div>
      <p className="text-sm font-medium text-gray-700 mb-1">
        {hasAny ? 'Không có voucher khớp bộ lọc' : 'Chưa có voucher nào'}
      </p>
      <p className="text-xs text-gray-500">
        {hasAny
          ? 'Thử bỏ bớt filter hoặc đổi điều kiện khác'
          : 'Tạo voucher đầu tiên để khách áp ở Checkout'}
      </p>
      {!hasAny && (
        <Link
          to="/vouchers/new"
          className="inline-flex mt-4 px-4 py-2 rounded-lg bg-orange-500 hover:bg-orange-600 text-white text-sm font-semibold"
        >
          + Tạo voucher đầu tiên
        </Link>
      )}
    </div>
  );
}
