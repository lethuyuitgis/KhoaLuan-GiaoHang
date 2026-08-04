import { formatDateTime, type OrderStatus, type StatusHistoryResponse } from '@shop/shared';
import { OrderStatusBadge } from '@/components/OrderStatusBadge';

/**
 * Vertical status timeline for an order, rendered newest→oldest.
 *
 * The "actor" is shown as a coarse role derived from the transition target,
 * not a resolved name — status_history stores only a raw user id, and which
 * role performs each transition is fixed by the order flow (admin confirms /
 * assigns / cancels; shipper delivers / returns; customer places the order).
 */
// CANCELLED is null on purpose: both the shop owner AND the customer can cancel
// (POST /api/orders/{id}/cancel), so the target status alone can't attribute a
// role — we omit the label rather than assert a wrong one; the note carries the
// context (e.g. "Admin hủy" vs the customer's reason).
const ACTOR_BY_STATUS: Record<OrderStatus, string | null> = {
  PENDING:    'Khách hàng',
  CONFIRMED:  'Chủ shop',
  ASSIGNED:   'Chủ shop',
  CANCELLED:  null,
  DELIVERING: 'Shipper',
  DELIVERED:  'Shipper',
  RETURNED:   'Shipper',
};

export function StatusTimeline({ entries }: { entries: StatusHistoryResponse[] }) {
  if (entries.length === 0) {
    return <p className="text-sm text-gray-400">Chưa có lịch sử trạng thái.</p>;
  }
  // Stored oldest→newest; show newest first without mutating the source array.
  const ordered = [...entries].reverse();

  return (
    <ol className="relative border-l-2 border-gray-100 ml-2">
      {ordered.map((e, idx) => (
        <li key={e.id} className="ml-4 pb-5 last:pb-0">
          <span
            className={`absolute -left-[7px] w-3 h-3 rounded-full border-2 border-white ${idx === 0 ? 'bg-brand-600' : 'bg-gray-300'}`}
            aria-hidden="true"
          />
          <div className="flex items-center gap-2 flex-wrap">
            <OrderStatusBadge status={e.toStatus} />
            {ACTOR_BY_STATUS[e.toStatus] && (
              <span className="text-xs text-gray-500">{ACTOR_BY_STATUS[e.toStatus]}</span>
            )}
          </div>
          <p className="text-xs text-gray-400 mt-0.5">{formatDateTime(e.changedAt)}</p>
          {e.note && <p className="text-sm text-gray-600 mt-1">{e.note}</p>}
        </li>
      ))}
    </ol>
  );
}
