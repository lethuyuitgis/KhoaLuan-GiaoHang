import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchAdminVouchers, formatVnd, type VoucherTarget } from '@shop/shared';
import { api } from '@/lib/api';

type StatusFilter = 'all' | 'active' | 'expired';
type TargetFilter = 'all' | VoucherTarget;

export function VouchersPage() {
  const nav = useNavigate();
  const [status, setStatus] = useState<StatusFilter>('all');
  const [target, setTarget] = useState<TargetFilter>('all');
  const [page] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'vouchers', page],
    queryFn: () => fetchAdminVouchers(api, page, 20),
  });

  const filtered = (data?.content ?? []).filter(v => {
    const now = new Date();
    const expired = new Date(v.validUntil) < now || !v.active;
    if (status === 'active' && expired) return false;
    if (status === 'expired' && !expired) return false;
    if (target !== 'all' && v.target !== target) return false;
    return true;
  });

  return (
    <div>
      <header className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Khuyến mãi</h1>
          <p className="text-sm text-gray-500">Tạo và quản lý voucher cho khách</p>
        </div>
        <Link
          to="/vouchers/new"
          className="px-4 py-2 rounded-lg bg-orange-500 hover:bg-orange-600 text-white font-semibold"
        >
          + Tạo voucher
        </Link>
      </header>

      <div className="flex gap-3 mb-4">
        <FilterChips
          label="Trạng thái"
          value={status}
          options={['all', 'active', 'expired'] as StatusFilter[]}
          onChange={setStatus}
        />
        <FilterChips
          label="Đối tượng"
          value={target}
          options={['all', 'SHIPPING', 'PRODUCTS'] as TargetFilter[]}
          onChange={setTarget}
        />
      </div>

      {isLoading ? (
        <p className="text-gray-500">Đang tải…</p>
      ) : (
        <table className="w-full bg-white rounded-xl border border-gray-200">
          <thead className="bg-gray-50 text-left text-xs uppercase">
            <tr>
              <th className="px-4 py-3">Mã</th>
              <th className="px-4 py-3">Tên</th>
              <th className="px-4 py-3">Đối tượng</th>
              <th className="px-4 py-3">Giảm</th>
              <th className="px-4 py-3">Đã dùng</th>
              <th className="px-4 py-3">Hạn dùng</th>
              <th className="px-4 py-3">Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map(v => (
              <tr
                key={v.id}
                className="border-t border-gray-100 hover:bg-gray-50 cursor-pointer"
                onClick={() => nav(`/vouchers/${v.id}`)}
              >
                <td className="px-4 py-3 font-mono text-sm">{v.code}</td>
                <td className="px-4 py-3">{v.name}</td>
                <td className="px-4 py-3">
                  {v.target === 'SHIPPING' ? '🚚 Ship' : '🛍️ Hàng'}
                </td>
                <td className="px-4 py-3">
                  {v.discountType === 'FIXED'
                    ? formatVnd(v.discountValue)
                    : `${v.discountValue}%${v.maxDiscount ? ` (tối đa ${formatVnd(v.maxDiscount)})` : ''}`}
                </td>
                <td className="px-4 py-3">
                  {v.usedCount} / {v.maxUsesTotal ?? '∞'}
                </td>
                <td className="px-4 py-3 text-sm">
                  {new Date(v.validUntil).toLocaleDateString('vi-VN')}
                </td>
                <td className="px-4 py-3">
                  {v.active && new Date(v.validUntil) >= new Date() ? (
                    <span className="text-xs bg-green-100 text-green-700 px-2 py-1 rounded">
                      Active
                    </span>
                  ) : (
                    <span className="text-xs bg-gray-100 text-gray-600 px-2 py-1 rounded">
                      Inactive
                    </span>
                  )}
                </td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr>
                <td colSpan={7} className="text-center py-8 text-gray-500">
                  Chưa có voucher nào
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}

function FilterChips<T extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T;
  options: T[];
  onChange: (v: T) => void;
}) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-xs text-gray-500">{label}:</span>
      {options.map(o => (
        <button
          key={o}
          onClick={() => onChange(o)}
          type="button"
          className={`text-xs px-3 py-1 rounded-full ${
            value === o
              ? 'bg-orange-500 text-white'
              : 'bg-white border border-gray-300 text-gray-700'
          }`}
        >
          {o}
        </button>
      ))}
    </div>
  );
}
