import type { OrderStatus } from '@shop/shared';

const LABELS: Record<OrderStatus, { label: string; className: string }> = {
  PENDING:    { label: 'Chờ xác nhận', className: 'bg-yellow-100 text-yellow-800' },
  CONFIRMED:  { label: 'Đã xác nhận',  className: 'bg-blue-100 text-blue-800' },
  ASSIGNED:   { label: 'Đã gán shipper', className: 'bg-indigo-100 text-indigo-800' },
  DELIVERING: { label: 'Đang giao',    className: 'bg-purple-100 text-purple-800' },
  DELIVERED:  { label: 'Đã giao',      className: 'bg-green-100 text-green-800' },
  CANCELLED:  { label: 'Đã hủy',       className: 'bg-gray-100 text-gray-800' },
  RETURNED:   { label: 'Hoàn hàng',    className: 'bg-red-100 text-red-800' },
};

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const info = LABELS[status];
  return (
    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${info.className}`}>
      {info.label}
    </span>
  );
}
