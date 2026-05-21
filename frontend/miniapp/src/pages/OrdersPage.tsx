import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { listMyOrders, formatVnd, formatRelative } from '@shop/shared';
import { api } from '@/lib/api';
import { OrderStatusBadge } from '@/components/OrderStatusBadge';

export function OrdersPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['orders', 'mine'],
    queryFn: () => listMyOrders(api, 0, 50),
  });

  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Đơn hàng của tôi</h1>

      {isLoading && <p className="text-tg-hint">Đang tải...</p>}
      {error && <p className="text-red-500">Không tải được lịch sử đơn.</p>}

      {data && data.content.length === 0 && (
        <div className="text-center py-12">
          <p className="text-tg-hint mb-4">Chưa có đơn nào</p>
          <Link to="/customer/shop" className="text-tg-link">Bắt đầu mua hàng</Link>
        </div>
      )}

      <div className="space-y-3">
        {data?.content.map(o => (
          <Link
            key={o.id}
            to={`/customer/orders/${o.id}`}
            className="block bg-tg-secondaryBg rounded-lg p-3"
          >
            <div className="flex justify-between items-start">
              <div className="min-w-0">
                <p className="font-medium">{o.code}</p>
                <p className="text-xs text-tg-hint mt-0.5">{formatRelative(o.createdAt)}</p>
              </div>
              <OrderStatusBadge status={o.status} />
            </div>
            <div className="mt-2 flex justify-between items-end">
              <span className="text-sm text-tg-hint">{o.paymentMethod}</span>
              <span className="font-bold">{formatVnd(o.total)}</span>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
