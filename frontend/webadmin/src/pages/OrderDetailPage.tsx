import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { formatVnd, formatDateTime, type OrderResponse } from '@shop/shared';
import { api } from '@/lib/api';
import { OrderStatusBadge } from '@/components/OrderStatusBadge';
import { AssignShipperModal } from '@/components/AssignShipperModal';

export function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [showAssign, setShowAssign] = useState(false);

  const { data: order, isLoading, error } = useQuery({
    queryKey: ['admin', 'order', id],
    queryFn: async () => {
      const { data } = await api.get<OrderResponse>(`/api/admin/orders/${id}`);
      return data;
    },
    enabled: !!id,
  });

  const confirmMut = useMutation({
    mutationFn: async () => {
      const { data } = await api.post<OrderResponse>(`/api/admin/orders/${id}/confirm`, { note: 'Admin xác nhận' });
      return data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'order', id] });
      qc.invalidateQueries({ queryKey: ['admin', 'orders', 'list'] });
    },
  });

  const cancelMut = useMutation({
    mutationFn: async () => {
      const { data } = await api.post<OrderResponse>(`/api/admin/orders/${id}/cancel`, { reason: 'Admin hủy' });
      return data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'order', id] });
      qc.invalidateQueries({ queryKey: ['admin', 'orders', 'list'] });
    },
  });

  if (isLoading) return <p className="text-gray-600">Đang tải...</p>;
  if (error || !order) {
    return (
      <div>
        <Link to="/orders" className="text-brand-600">← Về danh sách</Link>
        <p className="text-red-500 mt-4">Không tải được đơn.</p>
      </div>
    );
  }

  const canConfirm = order.status === 'PENDING';
  const canCancel = order.status === 'PENDING' || order.status === 'CONFIRMED';

  return (
    <div>
      <button onClick={() => navigate('/orders')} className="text-brand-600 mb-4">← Về danh sách</button>

      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold">{order.code}</h1>
          <p className="text-sm text-gray-500 mt-1">{formatDateTime(order.createdAt)}</p>
        </div>
        <OrderStatusBadge status={order.status} />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="bg-white rounded-lg shadow p-4">
          <h2 className="font-semibold mb-2">Sản phẩm</h2>
          <table className="w-full text-sm">
            <tbody>
              {order.items.map(i => (
                <tr key={i.id} className="border-b border-gray-100">
                  <td className="py-2">SP #{i.productId} × {i.quantity}</td>
                  <td className="py-2 text-right">{formatVnd(i.subtotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="mt-3 pt-3 border-t border-gray-200 space-y-1 text-sm">
            <div className="flex justify-between"><span className="text-gray-600">Tạm tính</span><span>{formatVnd(order.subtotal)}</span></div>
            <div className="flex justify-between"><span className="text-gray-600">Phí ship ({order.distanceKm}km)</span><span>{formatVnd(order.deliveryFee)}</span></div>
            <div className="flex justify-between font-bold pt-1 border-t border-gray-100"><span>Tổng</span><span>{formatVnd(order.total)}</span></div>
          </div>
        </div>

        <div className="space-y-4">
          <div className="bg-white rounded-lg shadow p-4">
            <h2 className="font-semibold mb-2">Khách hàng</h2>
            <p className="text-sm">{order.customerName ?? '(chưa cung cấp tên)'}</p>
            <p className="text-sm text-gray-600">{order.customerPhone ?? '(chưa có SĐT)'}</p>
            <p className="text-sm mt-2">{order.deliveryAddress}</p>
            {order.note && <p className="text-sm text-gray-600 mt-2 italic">Ghi chú: {order.note}</p>}
          </div>

          <div className="bg-white rounded-lg shadow p-4 text-sm">
            <div className="flex justify-between"><span className="text-gray-600">Thanh toán</span><span>{order.paymentMethod}</span></div>
            <div className="flex justify-between mt-1"><span className="text-gray-600">Trạng thái thanh toán</span><span>{order.paymentStatus}</span></div>
          </div>

          {(canConfirm || canCancel || order.status === 'CONFIRMED') && (
            <div className="bg-white rounded-lg shadow p-4 space-y-2">
              {canConfirm && (
                <button
                  onClick={() => confirmMut.mutate()}
                  disabled={confirmMut.isPending}
                  className="w-full py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700 disabled:opacity-50"
                >
                  {confirmMut.isPending ? 'Đang xác nhận...' : 'Xác nhận đơn'}
                </button>
              )}
              {order.status === 'CONFIRMED' && (
                <button
                  onClick={() => setShowAssign(true)}
                  className="w-full py-2 bg-purple-600 text-white rounded-md hover:bg-purple-700"
                >
                  Gán shipper
                </button>
              )}
              {canCancel && (
                <button
                  onClick={() => cancelMut.mutate()}
                  disabled={cancelMut.isPending}
                  className="w-full py-2 border border-red-500 text-red-500 rounded-md hover:bg-red-50 disabled:opacity-50"
                >
                  {cancelMut.isPending ? 'Đang hủy...' : 'Hủy đơn'}
                </button>
              )}
            </div>
          )}
        </div>
      </div>
      {showAssign && (
        <AssignShipperModal orderId={order.id} orderCode={order.code} onClose={() => setShowAssign(false)} />
      )}
    </div>
  );
}
