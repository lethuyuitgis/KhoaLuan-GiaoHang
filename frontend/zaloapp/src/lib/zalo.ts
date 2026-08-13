/**
 * Zalo Mini App SDK wrapper.
 *
 * Uses the real `zmp-sdk` (v2) when running inside the Zalo container, and
 * degrades to a browser-safe fallback otherwise so the app still runs in a
 * normal browser during development (rides the backend dev-bypass — see
 * `lib/auth.ts`).
 *
 * `zmp-sdk` is imported lazily (dynamic `import()`), only inside the Zalo
 * runtime — so its container-only code never loads in a plain browser and never
 * bloats the initial bundle. `getAccessToken()` is async in zmp-sdk, but our
 * axios interceptor reads the token synchronously, so we fetch it once on
 * `ready()` and cache it here.
 */

declare global {
  interface Window {
    ZaloJavaScriptInterface?: unknown;
  }
}

export interface ZaloSdk {
  /** True when running inside the actual Zalo Mini App container. */
  isInZalo(): boolean;
  /** Cached Zalo access token (fetched on ready()); null in browser/before init. */
  accessToken(): string | null;
  /** Open an external URL (zmp-sdk openWebview inside Zalo; window.open otherwise). */
  openLink(url: string): void;
  /** Fetch + cache the access token once the container is ready. */
  ready(): void;
}

let cachedToken: string | null = null;

function inZalo(): boolean {
  return typeof window !== 'undefined' && typeof window.ZaloJavaScriptInterface !== 'undefined';
}

function browserOpen(url: string): void {
  if (typeof window !== 'undefined') {
    window.open(url, '_blank', 'noopener,noreferrer');
  }
}

export const zalo: ZaloSdk = {
  isInZalo(): boolean {
    return inZalo();
  },

  accessToken(): string | null {
    return cachedToken;
  },

  openLink(url: string): void {
    if (!inZalo()) {
      browserOpen(url);
      return;
    }
    import('zmp-sdk')
      .then(({ openWebview }) => openWebview({ url }))
      .catch(() => browserOpen(url));
  },

  ready(): void {
    if (!inZalo()) return;
    import('zmp-sdk')
      .then(({ getAccessToken }) => getAccessToken())
      .then(token => { cachedToken = token; })
      .catch(err => console.warn('zmp-sdk getAccessToken failed', err));
  },
};
