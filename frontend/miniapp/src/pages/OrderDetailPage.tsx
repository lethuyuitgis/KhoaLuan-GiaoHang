import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getOrder, cancelOrder, formatVnd, formatDateTime, type PaymentStatus } from '@shop/shared';
import { api } from '@/lib/api';
import { OrderStatusBadge } from '@/components/OrderStatusBadge';
import { OrderTrackingMap } from '@/features/tracking/OrderTrackingMap';
import { tg } from '@/lib/telegram';
import { useToast } from '@/components/Toast';

const CANCELLABLE = new Set(['PENDING', 'CONFIRMED']);
const POLL_MS = 3000;
const POLL_TIMEOUT_MS = 60_000;

export function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const toast = useToast();
  const { t, i18n } = useTranslation();
  const [pollingExpired, setPollingExpired] = useState(false);

  const { data: order, isLoading, error } = useQuery({
    queryKey: ['order', id],
    queryFn: () => getOrder(api, id!),
    enabled: !!id,
    refetchInterval: query => {
      const o = query.state.data;
      if (!o) return false;
      if (pollingExpired) return false;
      const shouldPoll = o.paymentMethod === 'VNPAY' && o.paymentStatus === 'PENDING';
      return shouldPoll ? POLL_MS : false;
    },
  });

  useEffect(() => {
    if (order?.paymentMethod === 'VNPAY' && order?.paymentStatus === 'PENDING') {
      const t = setTimeout(() => setPollingExpired(true), POLL_TIMEOUT_MS);
      return () => clearTimeout(t);
    }
  }, [order?.paymentMethod, order?.paymentStatus]);

  const cancelMut = useMutation({
    mutationFn: (reason: string) => cancelOrder(api, id!, reason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['order', id] });
      qc.invalidateQueries({ queryKey: ['orders', 'mine'] });
      toast.info(t('orderDetail.cancelDone'));
    },
    onError: async (err: any) => {
      const msg = err.response?.data?.message ?? t('orderDetail.cancelError');
      if (tg.isInTelegram()) await tg.showAlert(msg);
      else toast.error(msg);
    },
  });

  const handleCancel = async () => {
    const ok = tg.isInTelegram()
      ? await tg.showConfirm(t('orderDetail.cancelConfirm'))
      : confirm(t('orderDetail.cancelConfirm'));
    if (!ok) return;
    cancelMut.mutate(t('orderDetail.cancelReason'));
  };

  if (isLoading) {
    return (
      <div className="space-y-3">
        <div className="h-10 bg-white rounded-xl animate-pulse border border-gray-100" />
        <div className="h-40 bg-white rounded-2xl animate-pulse border border-gray-100" />
        <div className="h-24 bg-white rounded-2xl animate-pulse border border-gray-100" />
        <div className="h-20 bg-white rounded-2xl animate-pulse border border-gray-100" />
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="text-center py-16">
        <p className="text-5xl mb-3">😕</p>
        <p className="font-medium text-gray-700 mb-1">{t('orderDetail.errorLoad')}</p>
        <p className="text-sm text-gray-500 mb-5">{t('orderDetail.errorLoadHint')}</p>
        <Link
          to="/customer/orders"
          className="inline-block px-5 py-2.5 rounded-2xl bg-orange-500 text-white font-semibold text-sm shadow-md shadow-orange-500/30 active:scale-[0.98] transition"
        >
          {t('orderDetail.backToList')}
        </Link>
      </div>
    );
  }

  return (
    <div className="pb-24">
      <button
        onClick={() => navigate(-1)}
        className="mb-3 text-sm text-gray-500 active:text-gray-700"
      >
        {t('orderDetail.back')}
      </button>

      <div className="flex justify-between items-start mb-4 gap-2">
        <div className="min-w-0">
          <h1 className="text-xl font-bold truncate">{order.code}</h1>
          <p className="text-xs text-gray-500 mt-1">{formatDateTime(order.createdAt, i18n.language)}</p>
        </div>
        <OrderStatusBadge status={order.status} />
      </div>

      {order.status === 'DELIVERING' && (
        <div className="mb-4">
          <OrderTrackingMap
            orderId={order.id}
            pickupLat={order.pickupLat}
            pickupLng={order.pickupLng}
            deliveryLat={order.deliveryLat}
            deliveryLng={order.deliveryLng}
          />
        </div>
      )}

      {/* Items + totals */}
      <section className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4 mb-3">
        <h2 className="text-sm font-semibold text-gray-500 mb-3">{t('orderDetail.items')}</h2>
        <div className="space-y-2">
          {order.items.map(i => (
            <div key={i.id} className="flex gap-3 items-center">
              {i.productImageUrl ? (
                <img
                  src={i.productImageUrl}
                  alt={i.productName}
                  className="w-12 h-12 rounded-xl object-cover bg-gray-100 flex-shrink-0"
                />
              ) : (
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-orange-400 to-red-500 flex-shrink-0 flex items-center justify-center text-white font-bold">
                  {i.productName.charAt(0).toUpperCase()}
                </div>
              )}
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium truncate">{i.productName}</p>
                <p className="text-xs text-gray-500">
                  {formatVnd(i.unitPrice, i18n.language)} × {i.quantity}
                </p>
              </div>
              <span className="text-sm font-semibold whitespace-nowrap">{formatVnd(i.subtotal, i18n.language)}</span>
            </div>
          ))}
        </div>
        <div className="border-t border-gray-100 mt-3 pt-3 space-y-1 text-sm">
          <div className="flex justify-between">
            <span className="text-gray-500">{t('orderDetail.subtotal')}</span>
            <span>{formatVnd(order.subtotal, i18n.language)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-500">{t('orderDetail.shippingFee', { km: order.distanceKm })}</span>
            <span>{formatVnd(order.deliveryFee, i18n.language)}</span>
          </div>
          <div className="flex justify-between font-bold pt-1.5 border-t border-gray-100 text-base">
            <span>{t('orderDetail.total')}</span>
            <span className="text-orange-600">{formatVnd(order.total, i18n.language)}</span>
          </div>
        </div>
      </section>

      {/* Delivery info */}
      <section className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4 mb-3">
        <h2 className="text-sm font-semibold text-gray-500 mb-2">{t('orderDetail.deliveryTo')}</h2>
        <p className="text-sm flex items-start gap-2">
          <span>📍</span>
          <span>{order.deliveryAddress}</span>
        </p>
        {order.customerPhone && (
          <p className="text-sm text-gray-500 mt-1.5">
            📞 {order.customerPhone}
          </p>
        )}
        {order.note && (
          <p className="text-sm text-gray-500 mt-2 italic bg-gray-50 rounded-xl px-3 py-2">
            💬 {order.note}
          </p>
        )}
      </section>

      {/* Payment status */}
      <section className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4 mb-3 text-sm">
        <div className="flex justify-between items-center">
          <span className="text-gray-500">{t('orderDetail.payment')}</span>
          <span className="font-medium">
            {order.paymentMethod === 'VNPAY' ? '💳 VNPay' : '💰 COD'}
          </span>
        </div>
        <div className="flex justify-between mt-2 items-center">
          <span className="text-gray-500">{t('orderDetail.paymentStatus')}</span>
          <PaymentStatusInline status={order.paymentStatus} />
        </div>
        {order.paymentMethod === 'VNPAY' && order.paymentStatus === 'PENDING' && !pollingExpired && (
          <div className="mt-3 rounded-xl bg-amber-50 border border-amber-200 px-3 py-2 text-xs text-amber-800 flex items-center gap-2">
            <span className="inline-block w-3 h-3 border-2 border-amber-300 border-t-amber-700 rounded-full animate-spin"></span>
            {t('orderDetail.vnpayPending')}
          </div>
        )}
        {order.paymentMethod === 'VNPAY' && order.paymentStatus === 'PENDING' && pollingExpired && (
          <p className="mt-3 rounded-xl bg-amber-50 border border-amber-200 px-3 py-2 text-xs text-amber-800">
            {t('orderDetail.vnpayTimeout')}
          </p>
        )}
      </section>

      {order.status === 'DELIVERED' && (
        <div className="rounded-2xl bg-gradient-to-br from-amber-50 to-orange-50 border border-amber-200 p-4 mb-3 text-sm text-amber-900">
          <p className="font-semibold">{t('orderDetail.delivered')}</p>
          <p className="text-xs mt-1">{t('orderDetail.deliveredHint')}</p>
        </div>
      )}

      {CANCELLABLE.has(order.status) && (
        <button
          onClick={handleCancel}
          disabled={cancelMut.isPending}
          className="w-full py-3 border border-red-300 text-red-600 rounded-2xl font-medium disabled:opacity-50 active:scale-[0.98] transition"
        >
          {cancelMut.isPending ? t('orderDetail.cancelling') : t('orderDetail.cancel')}
        </button>
      )}
    </div>
  );
}

function PaymentStatusInline({ status }: { status: PaymentStatus }) {
  const { t } = useTranslation();
  const styles: Record<PaymentStatus, string> = {
    PENDING:  'bg-gray-100 text-gray-700',
    SUCCESS:  'bg-emerald-100 text-emerald-700',
    FAILED:   'bg-red-100 text-red-700',
    REFUNDED: 'bg-amber-100 text-amber-700',
  };
  const cls = styles[status] ?? 'bg-gray-100 text-gray-700';
  return (
    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${cls}`}>
      {t(`payment.${status}`)}
    </span>
  );
}
