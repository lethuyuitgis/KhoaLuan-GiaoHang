import { describe, expect, it } from 'vitest';
import { formatDateTime, formatRelative } from './format-date';

describe('formatDateTime', () => {
  it('formats an ISO string into HH:mm dd/MM/yyyy in vi locale', () => {
    // 2026-06-01T08:30:00Z. Locale only affects month/day names, not the layout.
    const out = formatDateTime('2026-06-01T08:30:00Z');
    // Hour depends on the local TZ where tests run — assert layout, not the hour.
    expect(out).toMatch(/^\d{2}:\d{2} \d{2}\/\d{2}\/2026$/);
  });

  it('returns the input verbatim when given a non-parseable string', () => {
    expect(formatDateTime('not-a-date')).toBe('not-a-date');
  });
});

describe('formatRelative', () => {
  it('produces a Vietnamese relative-time string', () => {
    const out = formatRelative(new Date(Date.now() - 60_000).toISOString());
    // Should contain "trước" (Vietnamese for "ago") for past timestamps.
    expect(out).toContain('trước');
  });

  it('falls back to the raw input on parse failure', () => {
    expect(formatRelative('also-bad')).toBe('also-bad');
  });
});
