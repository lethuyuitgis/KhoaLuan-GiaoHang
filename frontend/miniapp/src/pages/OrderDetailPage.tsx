import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getOrder, cancelOrder, formatVnd, formatDateTime } from '@shop/shared';
import { api } from '@/lib/api';
import { OrderStatusBadge } from '@/components/OrderStatusBadge';
import { tg } from '@/lib/telegram';

const CANCELLABLE = new Set(['PENDING', 'CONFIRMED']);

export function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();

  const { data: order, isLoading, error } = useQuery({
    queryKey: ['order', id],
    queryFn: () => getOrder(api, id!),
    enabled: !!id,
  });

  const cancelMut = useMutation({
    mutationFn: (reason: string) => cancelOrder(api, id!, reason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['order', id] });
      qc.invalidateQueries({ queryKey: ['orders', 'mine'] });
    },
    onError: async (err: any) => {
      const msg = err.response?.data?.message ?? 'Không hủy được đơn';
      if (tg.isInTelegram()) await tg.showAlert(msg);
      else alert(msg);
    },
  });

  const handleCancel = async () => {
    const ok = tg.isInTelegram()
      ? await tg.showConfirm('Bạn có chắc muốn hủy đơn này?')
      : confirm('Bạn có chắc muốn hủy đơn này?');
    if (!ok) return;
    cancelMut.mutate('Khách hủy');
  };

  if (isLoading) return <p className="text-tg-hint">Đang tải...</p>;
  if (error || !order) {
    return (
      <div>
        <p className="text-red-500 mb-4">Không tải được đơn.</p>
        <Link to="/customer/orders" className="text-tg-link">Về danh sách đơn</Link>
      </div>
    );
  }

  return (
    <div>
      <button
        onClick={() => navigate(-1)}
        className="mb-4 text-tg-link"
      >
        ← Quay lại
      </button>

      <div className="flex justify-between items-start mb-4">
        <div>
          <h1 className="text-2xl font-bold">{order.code}</h1>
          <p className="text-xs text-tg-hint mt-1">{formatDateTime(order.createdAt)}</p>
        </div>
        <OrderStatusBadge status={order.status} />
      </div>

      <div className="bg-tg-secondaryBg rounded-lg p-4 mb-4">
        <h2 className="font-semibold mb-2">Sản phẩm</h2>
        {order.items.map(i => (
          <div key={i.id} className="flex justify-between py-1 text-sm">
            <span>SP #{i.productId} × {i.quantity}</span>
            <span>{formatVnd(i.subtotal)}</span>
          </div>
        ))}
        <div className="border-t border-tg-hint/20 mt-2 pt-2 space-y-1 text-sm">
          <div className="flex justify-between">
            <span className="text-tg-hint">Tạm tính</span>
            <span>{formatVnd(order.subtotal)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-tg-hint">Phí ship ({order.distanceKm}km)</span>
            <span>{formatVnd(order.deliveryFee)}</span>
          </div>
          <div className="flex justify-between font-bold pt-1 border-t border-tg-hint/20">
            <span>Tổng</span>
            <span>{formatVnd(order.total)}</span>
          </div>
        </div>
      </div>

      <div className="bg-tg-secondaryBg rounded-lg p-4 mb-4">
        <h2 className="font-semibold mb-2">Giao đến</h2>
        <p className="text-sm">{order.deliveryAddress}</p>
        {order.customerPhone && (
          <p className="text-sm text-tg-hint mt-1">SĐT: {order.customerPhone}</p>
        )}
        {order.note && (
          <p className="text-sm text-tg-hint mt-2 italic">Ghi chú: {order.note}</p>
        )}
      </div>

      <div className="bg-tg-secondaryBg rounded-lg p-4 mb-4 text-sm">
        <div className="flex justify-between">
          <span className="text-tg-hint">Thanh toán</span>
          <span>{order.paymentMethod}</span>
        </div>
        <div className="flex justify-between mt-1">
          <span className="text-tg-hint">Trạng thái thanh toán</span>
          <span>{order.paymentStatus}</span>
        </div>
      </div>

      {CANCELLABLE.has(order.status) && (
        <button
          onClick={handleCancel}
          disabled={cancelMut.isPending}
          className="w-full py-3 border border-red-500 text-red-500 rounded-lg font-medium disabled:opacity-50"
        >
          {cancelMut.isPending ? 'Đang hủy...' : 'Hủy đơn'}
        </button>
      )}
    </div>
  );
}
