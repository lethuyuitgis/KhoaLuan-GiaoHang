import { useState } from 'react';
import type { VoucherTarget, ValidateVoucherResponse } from '@shop/shared';
import { formatVnd } from '@shop/shared';
import { useVoucherValidation } from '@/hooks/useVoucherValidation';

interface Props {
  label: string;
  target: VoucherTarget;
  subtotal: number;
  deliveryFee: number;
  /** Called once a voucher passes validation. Parent stores it and includes in order create. */
  onApplied: (v: ValidateVoucherResponse) => void;
  /** Called when user removes the applied voucher. */
  onRemoved: () => void;
}

export function VoucherInput({ label, target, subtotal, deliveryFee, onApplied, onRemoved }: Props) {
  const [code, setCode] = useState('');
  const [applied, setApplied] = useState<ValidateVoucherResponse | null>(null);
  const mut = useVoucherValidation({ target, subtotal, deliveryFee });

  const apply = async () => {
    if (!code.trim()) return;
    try {
      const r = await mut.mutateAsync(code.trim().toUpperCase());
      setApplied(r);
      onApplied(r);
    } catch {
      // error rendered via mut.isError below
    }
  };

  const remove = () => {
    setApplied(null);
    setCode('');
    onRemoved();
  };

  return (
    <div className="space-y-2">
      <label className="block text-sm font-medium text-gray-700">{label}</label>
      <div className="flex gap-2">
        <input
          type="text"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="Nhập mã"
          disabled={!!applied || mut.isPending}
          className="flex-1 rounded-lg border border-gray-300 px-3 py-2"
        />
        {!applied ? (
          <button
            onClick={apply}
            disabled={!code.trim() || mut.isPending}
            type="button"
            className="px-4 py-2 rounded-lg bg-[var(--brand-primary)] text-white disabled:opacity-50"
          >
            {mut.isPending ? '...' : 'Áp'}
          </button>
        ) : (
          <button
            onClick={remove}
            type="button"
            className="px-4 py-2 rounded-lg bg-gray-200 text-gray-700"
          >
            × Gỡ
          </button>
        )}
      </div>
      {applied && (
        <p className="text-xs text-green-700">
          ✓ {applied.code} — Giảm {formatVnd(applied.discountAmount)}
        </p>
      )}
      {mut.isError && !applied && (
        <p className="text-xs text-red-600">
          Mã không hợp lệ hoặc không áp dụng được cho đơn này.
        </p>
      )}
    </div>
  );
}
