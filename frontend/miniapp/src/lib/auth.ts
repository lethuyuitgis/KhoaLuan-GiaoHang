import { tg } from './telegram';

export const INIT_DATA_HEADER = 'X-Telegram-Init-Data';
export const DEV_USER_HEADER  = 'X-Dev-User-Id';

/**
 * Default demo customer that exists in V11 seed (telegram_user.id = 9000000001,
 * username = "demo_cust_thuy"). Backend `dev` profile accepts this header as a
 * shortcut so the Mini App can be smoke-tested in a regular browser without
 * Telegram WebApp wiring. Header is silently ignored in non-dev profiles.
 *
 * Override for E2E smoke tests: append ?devUserId=<id> to the URL to impersonate
 * a different seeded user (e.g. a shipper). Only honoured in DEV mode.
 */
const DEV_FALLBACK_USER_ID = '9000000001';

function getDevUserId(): string {
  if (typeof window !== 'undefined') {
    const param = new URLSearchParams(window.location.search).get('devUserId');
    if (param) return param;
  }
  return DEV_FALLBACK_USER_ID;
}

export function getAuthHeaders(): Record<string, string> {
  const initData = tg.initData();
  if (initData) {
    return { [INIT_DATA_HEADER]: initData };
  }
  // Ngoài Telegram (mở web trong trình duyệt): gửi X-Dev-User-Id để demo đặt hàng được.
  // Backend CHỈ chấp nhận header này khi bật demo/dev (app.demo-mode) và chỉ map sang
  // user ĐÃ TỒN TẠI; prod thật (demo off) thì header bị bỏ qua → an toàn.
  return { [DEV_USER_HEADER]: getDevUserId() };
}
