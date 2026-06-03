import type { OrderStatus } from '@shop/shared';
import { useTranslation } from 'react-i18next';

const STYLES: Record<OrderStatus, string> = {
  PENDING:    'bg-amber-100 text-amber-800',
  CONFIRMED:  'bg-brand-100 text-brand-700',
  ASSIGNED:   'bg-brand-200 text-brand-800',
  DELIVERING: 'bg-brand-600 text-cream-50',
  DELIVERED:  'bg-emerald-100 text-emerald-800',
  CANCELLED:  'bg-stone-200 text-stone-700',
  RETURNED:   'bg-rose-100 text-rose-800',
};

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const { t } = useTranslation();
  const cls = STYLES[status] ?? 'bg-stone-200 text-stone-700';
  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-bold uppercase tracking-wide ${cls}`}>
      {t(`status.${status}`)}
    </span>
  );
}
