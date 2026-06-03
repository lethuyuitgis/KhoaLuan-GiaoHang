/**
 * Format an integer Vietnamese dong amount.
 *
 * Default locale is `vi-VN` (thousands as ".", suffix " ₫") to preserve
 * existing UI and tests. Pass a different locale (e.g. `en`, `ko`, `ja`,
 * `zh`) to render the same value with that locale's number conventions —
 * still in VND since shop only accepts VND.
 *
 * Note: We keep the trailing "₫" symbol manually rather than using
 * `style: 'currency'` because Intl emits "₫" placement that varies wildly
 * across locales (some prefix, some suffix, some use "VND"). A consistent
 * suffix is friendlier on a delivery UI where price always means dong.
 */
export function formatVnd(amount: number | string, locale: string = 'vi-VN'): string {
  const n = typeof amount === 'string' ? Number(amount) : amount;
  if (!Number.isFinite(n)) return '0 ₫';
  return new Intl.NumberFormat(locale).format(Math.round(n)) + ' ₫';
}
