import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { formatVnd, formatDateTime, type OrderStatus, type OrderSummary, type Page } from '@shop/shared';
import { api } from '@/lib/api';
import { OrderStatusBadge } from '@/components/OrderStatusBadge';
import { PaymentStatusBadge } from '@/components/PaymentStatusBadge';

const STATUSES: (OrderStatus | 'ALL')[] = ['ALL', 'PENDING', 'CONFIRMED', 'ASSIGNED', 'DELIVERING', 'DELIVERED', 'CANCELLED'];

export function OrdersPage() {
  const [filter, setFilter] = useState<OrderStatus | 'ALL'>('ALL');

  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'orders', 'list'],
    queryFn: async () => {
      const { data } = await api.get<Page<OrderSummary>>('/api/admin/orders?size=100');
      return data;
    },
  });

  const filtered = data?.content.filter(o => filter === 'ALL' || o.status === filter) ?? [];

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Đơn hàng</h1>

      <div className="bg-white rounded-lg shadow mb-4 p-3 flex gap-2 overflow-x-auto">
        {STATUSES.map(s => (
          <button
            key={s}
            onClick={() => setFilter(s)}
            className={`px-3 py-1 text-sm rounded-md whitespace-nowrap ${
              filter === s
                ? 'bg-brand-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            {s === 'ALL' ? 'Tất cả' : s}
          </button>
        ))}
      </div>

      {isLoading && <p className="text-gray-600">Đang tải...</p>}

      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600">
            <tr>
              <th className="px-4 py-2 text-left">Mã đơn</th>
              <th className="px-4 py-2 text-left">Trạng thái</th>
              <th className="px-4 py-2 text-left">Phương thức</th>
              <th className="px-4 py-2 text-left">Thanh toán</th>
              <th className="px-4 py-2 text-right">Tổng</th>
              <th className="px-4 py-2 text-left">Tạo lúc</th>
              <th className="px-4 py-2 text-right">Hành động</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {filtered.length === 0 && (
              <tr><td colSpan={7} className="px-4 py-8 text-center text-gray-500">Không có đơn</td></tr>
            )}
            {filtered.map(o => (
              <tr key={o.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">{o.code}</td>
                <td className="px-4 py-3"><OrderStatusBadge status={o.status} /></td>
                <td className="px-4 py-3 text-gray-700">{o.paymentMethod}</td>
                <td className="px-4 py-3"><PaymentStatusBadge status={o.paymentStatus} /></td>
                <td className="px-4 py-3 text-right font-medium">{formatVnd(o.total)}</td>
                <td className="px-4 py-3 text-gray-600">{formatDateTime(o.createdAt)}</td>
                <td className="px-4 py-3 text-right">
                  <Link to={`/orders/${o.id}`} className="text-brand-600 hover:underline">Xem</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
