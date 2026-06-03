import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { listMyOrders, formatVnd, formatRelative } from '@shop/shared';
import { api } from '@/lib/api';
import { OrderStatusBadge } from '@/components/OrderStatusBadge';

export function OrdersPage() {
  const { t, i18n } = useTranslation();
  const { data, isLoading, error } = useQuery({
    queryKey: ['orders', 'mine'],
    queryFn: () => listMyOrders(api, 0, 50),
  });

  return (
    <div className="px-5 pt-6">
      <h1 className="text-2xl font-bold text-brand-800 mb-5">{t('orders.title')}</h1>

      {isLoading && (
        <div className="space-y-3">
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="h-24 bg-white rounded-3xl shadow-warm animate-pulse" />
          ))}
        </div>
      )}

      {error && (
        <div className="rounded-3xl bg-rose-50 border border-rose-200 p-4 text-sm text-rose-700">
          <p className="font-semibold mb-1">{t('orders.errorLoad')}</p>
          <p className="text-xs opacity-80">{t('orders.errorLoadHint')}</p>
        </div>
      )}

      {data && data.content.length === 0 && (
        <div className="text-center py-16">
          <div className="w-24 h-24 rounded-full bg-brand-100 mx-auto flex items-center justify-center mb-5">
            <span className="text-5xl" aria-hidden="true">📋</span>
          </div>
          <p className="font-bold text-lg text-brand-800 mb-1.5">{t('orders.empty')}</p>
          <p className="text-sm text-brand-500 mb-6">{t('orders.emptyHint')}</p>
          <Link
            to="/customer/shop"
            className="inline-block px-6 py-3 rounded-full bg-brand-700 text-cream-50 font-semibold text-sm shadow-warm-lg active:scale-95 transition"
          >
            {t('orders.emptyCta')}
          </Link>
        </div>
      )}

      <div className="space-y-3">
        {data?.content.map(o => (
          <Link
            key={o.id}
            to={`/customer/orders/${o.id}`}
            className="block bg-white rounded-3xl shadow-warm p-4 active:scale-[0.99] transition"
          >
            <div className="flex justify-between items-start gap-2">
              <div className="min-w-0">
                <p className="font-bold text-brand-800 truncate">{o.code}</p>
                <p className="text-xs text-brand-500 mt-1 flex items-center gap-1">
                  <span aria-hidden="true">🕐</span>
                  {formatRelative(o.createdAt, i18n.language)}
                </p>
              </div>
              <OrderStatusBadge status={o.status} />
            </div>
            <div className="mt-3 pt-3 border-t border-brand-100 flex justify-between items-end">
              <span className="inline-flex items-center gap-1.5 text-xs text-brand-500 font-medium">
                <span aria-hidden="true">{o.paymentMethod === 'VNPAY' ? '💳' : '💵'}</span>
                {o.paymentMethod === 'VNPAY' ? 'VNPay' : 'COD'}
              </span>
              <span className="font-bold text-brand-700 text-lg">{formatVnd(o.total, i18n.language)}</span>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
