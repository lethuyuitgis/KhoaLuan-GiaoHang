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
    <div>
      <h1 className="text-2xl font-bold mb-4">{t('orders.title')}</h1>

      {isLoading && (
        <div className="space-y-3">
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="h-20 bg-white rounded-2xl animate-pulse border border-gray-100" />
          ))}
        </div>
      )}

      {error && (
        <div className="rounded-2xl bg-red-50 border border-red-200 p-4 text-sm text-red-700">
          <p className="font-medium mb-1">{t('orders.errorLoad')}</p>
          <p className="text-xs opacity-80">{t('orders.errorLoadHint')}</p>
        </div>
      )}

      {data && data.content.length === 0 && (
        <div className="text-center py-16">
          <p className="text-5xl mb-3">📦</p>
          <p className="font-medium text-gray-700 mb-1">{t('orders.empty')}</p>
          <p className="text-sm text-gray-500 mb-5">{t('orders.emptyHint')}</p>
          <Link
            to="/customer/shop"
            className="inline-block px-5 py-2.5 rounded-2xl bg-orange-500 text-white font-semibold text-sm shadow-md shadow-orange-500/30 active:scale-[0.98] transition"
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
            className="block bg-white rounded-2xl shadow-sm border border-gray-100 p-3 active:scale-[0.99] transition"
          >
            <div className="flex justify-between items-start gap-2">
              <div className="min-w-0">
                <p className="font-semibold truncate">{o.code}</p>
                <p className="text-xs text-gray-500 mt-0.5">{formatRelative(o.createdAt, i18n.language)}</p>
              </div>
              <OrderStatusBadge status={o.status} />
            </div>
            <div className="mt-2.5 flex justify-between items-end">
              <span className="inline-flex items-center gap-1 text-xs text-gray-500">
                {o.paymentMethod === 'VNPAY' ? '💳 VNPay' : '💰 COD'}
              </span>
              <span className="font-bold text-orange-600">{formatVnd(o.total, i18n.language)}</span>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
