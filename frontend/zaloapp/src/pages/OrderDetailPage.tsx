import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getOrder, cancelOrder, formatVnd, formatDateTime, type PaymentStatus } from '@shop/shared';
import { api } from '@/lib/api';
import { OrderStatusBadge } from '@/components/OrderStatusBadge';
import { useToast } from '@/components/Toast';

const CANCELLABLE = new Set(['PENDING', 'CONFIRMED']);
const POLL_MS = 3000;
const POLL_TIMEOUT_MS = 60_000;

const SECTION_TITLE_CLS = 'text-[11px] font-bold uppercase tracking-[0.18em] text-brand-500 mb-3';
const SECTION_CARD_CLS  = 'bg-white rounded-3xl shadow-warm p-5';

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
      const timer = setTimeout(() => setPollingExpired(true), POLL_TIMEOUT_MS);
      return () => clearTimeout(timer);
    }
  }, [order?.paymentMethod, order?.paymentStatus]);

  const cancelMut = useMutation({
    mutationFn: (reason: string) => cancelOrder(api, id!, reason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['order', id] });
      qc.invalidateQueries({ queryKey: ['orders', 'mine'] });
      toast.info(t('orderDetail.cancelDone'));
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? t('orderDetail.cancelError');
      toast.error(msg);
    },
  });

  const handleCancel = () => {
    const ok = confirm(t('orderDetail.cancelConfirm'));
    if (!ok) return;
    cancelMut.mutate(t('orderDetail.cancelReason'));
  };

  if (isLoading) {
    return (
      <div className="px-4 pt-4 space-y-3">
        <div className="h-10 bg-white rounded-2xl shadow-warm animate-pulse" />
        <div className="h-40 bg-white rounded-3xl shadow-warm animate-pulse" />
        <div className="h-24 bg-white rounded-3xl shadow-warm animate-pulse" />
        <div className="h-20 bg-white rounded-3xl shadow-warm animate-pulse" />
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="px-5 pt-6 text-center py-16">
        <div className="w-24 h-24 rounded-full bg-brand-100 mx-auto flex items-center justify-center mb-5">
          <span className="text-5xl" aria-hidden="true">😕</span>
        </div>
        <p className="font-bold text-lg text-brand-800 mb-1.5">{t('orderDetail.errorLoad')}</p>
        <p className="text-sm text-brand-500 mb-6">{t('orderDetail.errorLoadHint')}</p>
        <Link
          to="/customer/orders"
          className="inline-block px-6 py-3 rounded-full bg-brand-700 text-cream-50 font-semibold text-sm shadow-warm-lg active:scale-95 transition"
        >
          {t('orderDetail.backToList')}
        </Link>
      </div>
    );
  }

  return (
    <div className="px-4 pt-4 pb-10 space-y-3">
      <button
        type="button"
        onClick={() => navigate(-1)}
        className="text-sm text-brand-500 font-medium active:text-brand-700"
      >
        ← {t('orderDetail.back')}
      </button>

      <div className="bg-white rounded-3xl shadow-warm p-5 flex justify-between items-start gap-3">
        <div className="min-w-0">
          <h1 className="text-xl font-bold text-brand-800 truncate">{order.code}</h1>
          <p className="text-xs text-brand-500 mt-1 flex items-center gap-1">
            <span aria-hidden="true">🕐</span>
            {formatDateTime(order.createdAt, i18n.language)}
          </p>
        </div>
        <OrderStatusBadge status={order.status} />
      </div>

      {order.status === 'DELIVERING' && (
        <div className="rounded-3xl bg-gradient-to-br from-brand-100 to-cream-200 p-5 shadow-warm">
          <p className="font-bold text-brand-800 flex items-center gap-2">
            <span aria-hidden="true">📍</span>
            {t('status.DELIVERING')}
          </p>
          <p className="text-xs text-brand-600 mt-1.5 leading-relaxed">{t('orderDetail.deliveredHintZalo')}</p>
        </div>
      )}

      <section className={SECTION_CARD_CLS}>
        <h2 className={SECTION_TITLE_CLS}>{t('orderDetail.items')}</h2>
        <div className="space-y-3">
          {order.items.map(i => (
            <div key={i.id} className="flex gap-3 items-center">
              {i.productImageUrl ? (
                <img
                  src={i.productImageUrl}
                  alt={i.productName}
                  className="w-14 h-14 rounded-2xl object-cover bg-brand-100 flex-shrink-0"
                />
              ) : (
                <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-brand-400 to-brand-600 flex-shrink-0 flex items-center justify-center text-white font-bold text-lg">
                  {i.productName.charAt(0).toUpperCase()}
                </div>
              )}
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-brand-800 truncate">{i.productName}</p>
                <p className="text-xs text-brand-500 mt-0.5">
                  {formatVnd(i.unitPrice, i18n.language)} × {i.quantity}
                </p>
              </div>
              <span className="text-sm font-bold text-brand-800 whitespace-nowrap">
                {formatVnd(i.subtotal, i18n.language)}
              </span>
            </div>
          ))}
        </div>
        <div className="border-t border-brand-100 mt-4 pt-3 space-y-1.5 text-sm">
          <div className="flex justify-between">
            <span className="text-brand-500">{t('orderDetail.subtotal')}</span>
            <span className="text-brand-800">{formatVnd(order.subtotal, i18n.language)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-brand-500">{t('orderDetail.shippingFee', { km: order.distanceKm })}</span>
            <span className="text-brand-800">{formatVnd(order.deliveryFee, i18n.language)}</span>
          </div>
          <div className="flex justify-between font-bold pt-2 mt-1 border-t border-brand-100 text-base">
            <span className="text-brand-800">{t('orderDetail.total')}</span>
            <span className="text-brand-700">{formatVnd(order.total, i18n.language)}</span>
          </div>
        </div>
      </section>

      <section className={SECTION_CARD_CLS}>
        <h2 className={SECTION_TITLE_CLS}>{t('orderDetail.deliveryTo')}</h2>
        <p className="text-sm text-brand-800 flex items-start gap-2 leading-relaxed">
          <span aria-hidden="true" className="text-brand-600 flex-shrink-0">📍</span>
          <span>{order.deliveryAddress}</span>
        </p>
        {order.customerPhone && (
          <p className="text-sm text-brand-500 mt-2 flex items-center gap-2">
            <span aria-hidden="true">📞</span>
            {order.customerPhone}
          </p>
        )}
        {order.note && (
          <p className="text-sm text-brand-600 mt-3 italic bg-brand-50 rounded-2xl px-3 py-2.5 leading-relaxed">
            <span aria-hidden="true" className="mr-1">💬</span>
            {order.note}
          </p>
        )}
      </section>

      <section className={SECTION_CARD_CLS}>
        <h2 className={SECTION_TITLE_CLS}>{t('orderDetail.payment')}</h2>
        <div className="flex justify-between items-center text-sm">
          <span className="text-brand-500">{t('orderDetail.payment')}</span>
          <span className="font-semibold text-brand-800">
            {order.paymentMethod === 'VNPAY' ? '💳 VNPay' : '💵 COD'}
          </span>
        </div>
        <div className="flex justify-between mt-2.5 items-center text-sm">
          <span className="text-brand-500">{t('orderDetail.paymentStatus')}</span>
          <PaymentStatusInline status={order.paymentStatus} />
        </div>
        {order.paymentMethod === 'VNPAY' && order.paymentStatus === 'PENDING' && !pollingExpired && (
          <div className="mt-3 rounded-2xl bg-amber-50 border border-amber-200 px-3 py-2.5 text-xs text-amber-800 flex items-center gap-2">
            <span className="inline-block w-3 h-3 border-2 border-amber-300 border-t-amber-700 rounded-full animate-spin"></span>
            {t('orderDetail.vnpayPending')}
          </div>
        )}
        {order.paymentMethod === 'VNPAY' && order.paymentStatus === 'PENDING' && pollingExpired && (
          <p className="mt-3 rounded-2xl bg-amber-50 border border-amber-200 px-3 py-2.5 text-xs text-amber-800">
            {t('orderDetail.vnpayTimeout')}
          </p>
        )}
      </section>

      {order.status === 'DELIVERED' && (
        <div className="rounded-3xl bg-gradient-to-br from-brand-100 to-cream-200 p-5 shadow-warm">
          <p className="font-bold text-brand-800 flex items-center gap-2">
            <span aria-hidden="true">⭐</span>
            {t('orderDetail.delivered')}
          </p>
          <p className="text-xs text-brand-600 mt-1.5 leading-relaxed">{t('orderDetail.deliveredHintZalo')}</p>
        </div>
      )}

      {CANCELLABLE.has(order.status) && (
        <button
          onClick={handleCancel}
          disabled={cancelMut.isPending}
          className="w-full py-3.5 border-2 border-rose-300 text-rose-600 rounded-2xl font-semibold disabled:opacity-50 active:scale-[0.98] transition bg-white"
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
    PENDING:  'bg-stone-200 text-stone-700',
    SUCCESS:  'bg-emerald-100 text-emerald-700',
    FAILED:   'bg-rose-100 text-rose-700',
    REFUNDED: 'bg-amber-100 text-amber-700',
  };
  const cls = styles[status] ?? 'bg-stone-200 text-stone-700';
  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-bold uppercase tracking-wide ${cls}`}>
      {t(`payment.${status}`)}
    </span>
  );
}
