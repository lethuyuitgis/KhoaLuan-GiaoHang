import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { formatVnd, type VoucherTarget, type ValidateVoucherResponse } from '@shop/shared';
import { useVoucherValidation } from '@/hooks/useVoucherValidation';

interface Props {
  label: string;
  target: VoucherTarget;
  subtotal: number;
  deliveryFee: number;
  onApplied: (v: ValidateVoucherResponse) => void;
  onRemoved: () => void;
}

export function VoucherInput({ label, target, subtotal, deliveryFee, onApplied, onRemoved }: Props) {
  const { t, i18n } = useTranslation();
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
      <label className="block text-xs font-medium text-brand-500">{label}</label>
      <div className="flex gap-2">
        <input
          type="text"
          value={code}
          onChange={e => setCode(e.target.value)}
          placeholder={t('voucher.placeholder')}
          disabled={!!applied || mut.isPending}
          className="flex-1 rounded-2xl border border-brand-100 bg-brand-50 px-4 py-2.5 text-sm text-brand-800 placeholder-brand-400/70 focus:outline-none focus:ring-2 focus:ring-brand-600 disabled:opacity-60"
          data-testid={`voucher-input-${target}`}
        />
        {!applied ? (
          <button
            onClick={apply}
            disabled={!code.trim() || mut.isPending}
            type="button"
            className="px-4 py-2.5 rounded-2xl bg-brand-700 text-cream-50 text-sm font-bold disabled:bg-brand-200 disabled:text-brand-400"
          >
            {mut.isPending ? '…' : t('voucher.apply')}
          </button>
        ) : (
          <button
            onClick={remove}
            type="button"
            className="px-4 py-2.5 rounded-2xl bg-brand-100 text-brand-700 text-sm font-bold"
          >
            × {t('voucher.remove')}
          </button>
        )}
      </div>
      {applied && (
        <p className="text-xs text-green-700">
          ✓ {applied.code} — {t('voucher.discount', { amount: formatVnd(applied.discountAmount, i18n.language) })}
        </p>
      )}
      {mut.isError && !applied && (
        <p className="text-xs text-red-600">{t('voucher.invalid')}</p>
      )}
    </div>
  );
}
