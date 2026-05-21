export function formatVnd(amount: number | string): string {
  const n = typeof amount === 'string' ? Number(amount) : amount;
  if (!Number.isFinite(n)) return '0 ₫';
  return new Intl.NumberFormat('vi-VN').format(Math.round(n)) + ' ₫';
}
