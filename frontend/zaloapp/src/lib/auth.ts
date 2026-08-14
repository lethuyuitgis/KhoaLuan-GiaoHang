import { zalo } from './zalo';

/**
 * Zalo OAuth access token header — backend ZaloAuthFilter verifies via
 * Zalo OpenAPI GET /v2/me. Sibling to the Telegram initData header used by
 * the Telegram Mini App; backend filter chain handles whichever is present.
 */
export const ZALO_TOKEN_HEADER = 'X-Zalo-Access-Token';
export const DEV_USER_HEADER = 'X-Dev-User-Id';

/**
 * Default demo customer that exists in V11 seed (telegram_user.id = 9000000001,
 * username = "demo_cust_thuy"). Backend `dev` profile accepts this header as a
 * shortcut so the Zalo app can be smoke-tested in a regular browser without
 * a Zalo Developer Account. Header is silently ignored in non-dev profiles.
 */
const DEV_FALLBACK_USER_ID = '9000000001';
/** Shipper seed (telegram_user.id = 9000000105, role SHIPPER, có đơn đang giao để demo). */
const DEV_SHIPPER_USER_ID = '9000000105';

export function getAuthHeaders(): Record<string, string> {
  // Khu vực shipper (route #/shipper/…) đăng nhập như shipper seed qua dev-bypass —
  // Zalo Mini App không có role thật nên demo dùng X-Dev-User-Id (backend dev profile).
  if (typeof window !== 'undefined' && window.location.hash.includes('/shipper')) {
    return { [DEV_USER_HEADER]: DEV_SHIPPER_USER_ID };
  }
  const token = zalo.accessToken();
  if (token) {
    return { [ZALO_TOKEN_HEADER]: token };
  }
  // Browser-only mode: ride the backend dev-bypass so cart/checkout/orders still work.
  // In prod the backend ignores this header (dev profile only).
  if (import.meta.env.DEV) {
    return { [DEV_USER_HEADER]: DEV_FALLBACK_USER_ID };
  }
  return {};
}
