import type { PaymentStatus } from '@shop/shared';

const COLORS: Record<PaymentStatus, { bg: string; fg: string; label: string }> = {
  PENDING:  { bg: 'bg-gray-100',   fg: 'text-gray-700',  label: 'Đang chờ' },
  SUCCESS:  { bg: 'bg-green-100',  fg: 'text-green-700', label: 'Đã thanh toán' },
  FAILED:   { bg: 'bg-red-100',    fg: 'text-red-700',   label: 'Thanh toán thất bại' },
  REFUNDED: { bg: 'bg-amber-100',  fg: 'text-amber-700', label: 'Hoàn tiền' },
};

export function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  const c = COLORS[status] ?? { bg: 'bg-gray-100', fg: 'text-gray-700', label: status };
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium ${c.bg} ${c.fg}`}>
      {c.label}
    </span>
  );
}
