import { useEffect, type ReactNode } from 'react';
import { useShopConfig } from '@/hooks/useShopConfig';

/**
 * Reads brandPrimary/brandSecondary from the public shop-config endpoint and
 * publishes CSS variables (--brand-primary, --brand-primary-dark,
 * --brand-primary-light, --brand-secondary) so any component can opt in via
 * `bg-[var(--brand-primary)]` etc.
 *
 * Must wrap children inside the QueryProvider — the hook depends on it.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const { data } = useShopConfig();

  useEffect(() => {
    if (!data) return;
    const root = document.documentElement;
    root.style.setProperty('--brand-primary', data.brandPrimary);
    root.style.setProperty('--brand-primary-dark', darken(data.brandPrimary, 0.15));
    root.style.setProperty('--brand-primary-light', lighten(data.brandPrimary, 0.10));
    root.style.setProperty('--brand-secondary', data.brandSecondary);
  }, [data]);

  return <>{children}</>;
}

/** Darken a hex color by `pct` (0..1). Returns #RRGGBB. */
function darken(hex: string, pct: number): string {
  return shade(hex, -pct);
}

/** Lighten a hex color by `pct` (0..1). Returns #RRGGBB. */
function lighten(hex: string, pct: number): string {
  return shade(hex, pct);
}

function shade(hex: string, amount: number): string {
  const parsed = hex.replace('#', '');
  if (parsed.length !== 6) return hex;
  const r = clamp(Math.round(parseInt(parsed.slice(0, 2), 16) * (1 + amount)));
  const g = clamp(Math.round(parseInt(parsed.slice(2, 4), 16) * (1 + amount)));
  const b = clamp(Math.round(parseInt(parsed.slice(4, 6), 16) * (1 + amount)));
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

function clamp(n: number): number {
  return Math.max(0, Math.min(255, n));
}

function toHex(n: number): string {
  return n.toString(16).padStart(2, '0');
}
