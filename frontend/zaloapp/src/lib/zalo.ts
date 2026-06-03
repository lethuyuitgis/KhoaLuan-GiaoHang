/**
 * Mock Zalo Mini App SDK wrapper.
 *
 * The real `zmp-sdk` package can only be installed after we register a Zalo
 * Official Account + Mini App at https://mini.zalo.me/devtools/ (the install
 * pulls an auth payload from the dev console). Until then this module exposes
 * the same surface area so the rest of the app can be developed/tested in a
 * regular browser.
 *
 * To upgrade to the real SDK later:
 * 1. `pnpm --filter @shop/zaloapp add zmp-sdk zmp-ui`
 * 2. Replace each method below with the corresponding zmp-sdk call:
 *      isInZalo()    → `typeof window.ZaloJavaScriptInterface !== 'undefined'`
 *      accessToken() → `await getAccessToken({ success, fail })`
 *      openLink(url) → `openWebview({ url })`
 *      ready()       → `events.on(EventName.OnDataCallback, ...)` registration
 * 3. Drop the global declare below.
 */

declare global {
  interface Window {
    ZaloJavaScriptInterface?: unknown;
  }
}

export interface ZaloSdk {
  /** True when running inside the actual Zalo Mini App container. */
  isInZalo(): boolean;
  /** Real SDK: getAccessToken({ success: cb }). Mock returns null. */
  accessToken(): string | null;
  /** Real SDK: openWebview({ url }). Mock falls back to window.open. */
  openLink(url: string): void;
  /** Real SDK fires this once the container is ready. Mock is a no-op. */
  ready(): void;
}

export const zalo: ZaloSdk = {
  isInZalo(): boolean {
    return typeof window !== 'undefined' && typeof window.ZaloJavaScriptInterface !== 'undefined';
  },

  accessToken(): string | null {
    return null;
  },

  openLink(url: string): void {
    if (typeof window !== 'undefined') {
      window.open(url, '_blank', 'noopener,noreferrer');
    }
  },

  ready(): void {
    // no-op for mock
  },
};
