import { tg } from './telegram';

export const INIT_DATA_HEADER = 'X-Telegram-Init-Data';
export const DEV_USER_HEADER  = 'X-Dev-User-Id';

/**
 * Default demo customer that exists in V11 seed (telegram_user.id = 9000000001,
 * username = "demo_cust_thuy"). Backend `dev` profile accepts this header as a
 * shortcut so the Mini App can be smoke-tested in a regular browser without
 * Telegram WebApp wiring. Header is silently ignored in non-dev profiles.
 */
const DEV_FALLBACK_USER_ID = '9000000001';

export function getAuthHeaders(): Record<string, string> {
  const initData = tg.initData();
  if (initData) {
    return { [INIT_DATA_HEADER]: initData };
  }
  // Browser-only mode: ride the backend dev-bypass so cart/checkout/orders still work.
  // In prod the backend ignores this header (dev profile only).
  if (import.meta.env.DEV) {
    return { [DEV_USER_HEADER]: DEV_FALLBACK_USER_ID };
  }
  return {};
}
