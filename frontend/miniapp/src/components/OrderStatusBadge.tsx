import type { OrderStatus } from '@shop/shared';
import { useTranslation } from 'react-i18next';

const STYLES: Record<OrderStatus, string> = {
  PENDING:    'bg-yellow-100 text-yellow-800',
  CONFIRMED:  'bg-blue-100 text-blue-800',
  ASSIGNED:   'bg-indigo-100 text-indigo-800',
  DELIVERING: 'bg-purple-100 text-purple-800',
  DELIVERED:  'bg-green-100 text-green-800',
  CANCELLED:  'bg-gray-100 text-gray-800',
  RETURNED:   'bg-red-100 text-red-800',
};

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const { t } = useTranslation();
  const cls = STYLES[status] ?? 'bg-gray-100 text-gray-800';
  return (
    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${cls}`}>
      {t(`status.${status}`)}
    </span>
  );
}
