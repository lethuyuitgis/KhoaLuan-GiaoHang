import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { formatVnd, formatDateTime, type OrderStatus, type OrderSummary, type Page } from '@shop/shared';
import { api } from '@/lib/api';
import { OrderStatusBadge } from '@/components/OrderStatusBadge';
import { PaymentStatusBadge } from '@/components/PaymentStatusBadge';
import { useAdminOrdersSocket } from '@/hooks/useAdminOrdersSocket';

const STATUSES: (OrderStatus | 'ALL')[] = ['ALL', 'PENDING', 'CONFIRMED', 'ASSIGNED', 'DELIVERING', 'DELIVERED', 'CANCELLED'];
const STATUS_VN: Record<OrderStatus | 'ALL', string> = {
  ALL: 'Tất cả',
  PENDING: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  ASSIGNED: 'Đã gán',
  DELIVERING: 'Đang giao',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã huỷ',
  RETURNED: 'Hoàn về',
};

const PAGE_SIZE = 15;

export function OrdersPage() {
  useAdminOrdersSocket();

  const [filter, setFilter] = useState<OrderStatus | 'ALL'>('ALL');
  const [page, setPage] = useState(0);

  // Phân trang server-side: đổi tab lọc là truy vấn lại từ trang 0.
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'orders', 'list', filter, page],
    queryFn: async () => {
      const statusParam = filter === 'ALL' ? '' : `&status=${filter}`;
      const { data } = await api.get<Page<OrderSummary>>(
        `/api/admin/orders?size=${PAGE_SIZE}&page=${page}${statusParam}`);
      return data;
    },
  });

  // Số đơn từng trạng thái cho tab — đếm trên toàn bảng, không phụ thuộc trang.
  const { data: counts } = useQuery({
    queryKey: ['admin', 'orders', 'status-counts'],
    queryFn: async () => (await api.get<Record<string, number>>('/api/admin/orders/status-counts')).data,
  });

  const totalAll = Object.values(counts ?? {}).reduce((a, b) => a + b, 0);
  const filtered = data?.content ?? [];
  const totalPages = data?.totalPages ?? 1;

  function selectFilter(s: OrderStatus | 'ALL') {
    setFilter(s);
    setPage(0);
  }

  return (
    <div>
      <div className="mb-6 flex items-end justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Đơn hàng</h1>
          <p className="text-sm text-gray-500 mt-1">
            {data ? `${data.totalElements} đơn` : 'Đang tải…'}
            {filter !== 'ALL' && ` · lọc theo "${STATUS_VN[filter]}"`}
          </p>
        </div>
      </div>

      {/* Filter pills */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm mb-4 p-2 flex gap-1.5 overflow-x-auto">
        {STATUSES.map(s => {
          const count = s === 'ALL' ? totalAll : (counts?.[s] ?? 0);
          const active = filter === s;
          return (
            <button
              key={s}
              onClick={() => selectFilter(s)}
              className={`px-3.5 py-1.5 text-sm rounded-lg whitespace-nowrap font-medium transition-colors flex items-center gap-1.5 ${
                active
                  ? 'bg-orange-500 text-white shadow-sm'
                  : 'text-gray-600 hover:bg-gray-100'
              }`}
            >
              <span>{STATUS_VN[s]}</span>
              <span className={`text-xs px-1.5 py-0.5 rounded ${active ? 'bg-white/20' : 'bg-gray-100 text-gray-500'}`}>
                {count}
              </span>
            </button>
          );
        })}
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wider">
              <th className="px-4 py-3 text-left font-semibold">Mã đơn</th>
              <th className="px-4 py-3 text-left font-semibold">Trạng thái</th>
              <th className="px-4 py-3 text-left font-semibold">Phương thức</th>
              <th className="px-4 py-3 text-left font-semibold">Thanh toán</th>
              <th className="px-4 py-3 text-right font-semibold">Tổng</th>
              <th className="px-4 py-3 text-left font-semibold">Tạo lúc</th>
              <th className="px-4 py-3 text-right font-semibold">&nbsp;</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && Array.from({ length: 6 }).map((_, i) => (
              <tr key={i}>
                <td colSpan={7} className="px-4 py-4">
                  <div className="h-4 bg-gray-100 rounded animate-pulse" />
                </td>
              </tr>
            ))}
            {!isLoading && filtered.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-gray-400">
                  <p className="text-3xl mb-1">📭</p>
                  <p className="text-sm">Không có đơn nào ở trạng thái này</p>
                </td>
              </tr>
            )}
            {filtered.map(o => (
              <tr key={o.id} className="hover:bg-orange-50/40 transition-colors">
                <td className="px-4 py-3 font-mono font-semibold text-gray-900">{o.code}</td>
                <td className="px-4 py-3"><OrderStatusBadge status={o.status} /></td>
                <td className="px-4 py-3">
                  <span className={`text-xs font-medium px-2 py-0.5 rounded ${
                    o.paymentMethod === 'VNPAY' ? 'bg-blue-50 text-blue-700' : 'bg-gray-100 text-gray-700'
                  }`}>{o.paymentMethod}</span>
                </td>
                <td className="px-4 py-3"><PaymentStatusBadge status={o.paymentStatus} /></td>
                <td className="px-4 py-3 text-right font-semibold text-gray-900 whitespace-nowrap">{formatVnd(o.total)}</td>
                <td className="px-4 py-3 text-gray-500 text-xs whitespace-nowrap">{formatDateTime(o.createdAt)}</td>
                <td className="px-4 py-3 text-right">
                  <Link
                    to={`/orders/${o.id}`}
                    className="inline-flex items-center gap-1 text-orange-600 hover:text-orange-700 text-sm font-medium"
                  >
                    Chi tiết
                    <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                    </svg>
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {/* Thanh phân trang */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100 bg-gray-50/60">
            <p className="text-xs text-gray-500">
              Trang {page + 1}/{totalPages} · {data?.totalElements ?? 0} đơn
            </p>
            <div className="flex items-center gap-1">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-3 py-1.5 text-sm rounded-lg border border-gray-200 bg-white font-medium text-gray-700 hover:bg-gray-100 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                ← Trước
              </button>
              {Array.from({ length: totalPages }, (_, i) => i)
                .filter(i => i === 0 || i === totalPages - 1 || Math.abs(i - page) <= 1)
                .map((i, idx, arr) => (
                  <span key={i} className="flex items-center">
                    {idx > 0 && arr[idx - 1] !== i - 1 && <span className="px-1 text-gray-400">…</span>}
                    <button
                      onClick={() => setPage(i)}
                      className={`min-w-[2rem] px-2 py-1.5 text-sm rounded-lg font-medium ${
                        i === page
                          ? 'bg-orange-500 text-white'
                          : 'text-gray-600 hover:bg-gray-100'
                      }`}
                    >
                      {i + 1}
                    </button>
                  </span>
                ))}
              <button
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="px-3 py-1.5 text-sm rounded-lg border border-gray-200 bg-white font-medium text-gray-700 hover:bg-gray-100 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Sau →
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
