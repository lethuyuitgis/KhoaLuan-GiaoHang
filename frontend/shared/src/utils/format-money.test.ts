import { describe, expect, it } from 'vitest';
import { formatVnd } from './format-money';

describe('formatVnd', () => {
  it('formats integers with thousands separators (vi-VN uses dot)', () => {
    expect(formatVnd(55000)).toBe('55.000 ₫');
    expect(formatVnd(1_234_567)).toBe('1.234.567 ₫');
  });

  it('handles zero', () => {
    expect(formatVnd(0)).toBe('0 ₫');
  });

  it('accepts numeric string input', () => {
    expect(formatVnd('25000')).toBe('25.000 ₫');
  });

  it('rounds decimal amounts', () => {
    expect(formatVnd(55_000.4)).toBe('55.000 ₫');
    expect(formatVnd(55_000.6)).toBe('55.001 ₫');
  });

  it('falls back to "0 ₫" for non-finite/NaN inputs (defensive)', () => {
    expect(formatVnd(Number.NaN)).toBe('0 ₫');
    expect(formatVnd(Number.POSITIVE_INFINITY)).toBe('0 ₫');
    expect(formatVnd('abc')).toBe('0 ₫');
  });
});
