import { afterEach, describe, expect, it, vi } from 'vitest';
import { zalo } from './zalo';

describe('zalo SDK mock wrapper', () => {
  afterEach(() => {
    // Reset any stubs the test installed on window.
    if (typeof window !== 'undefined') {
      delete (window as { ZaloJavaScriptInterface?: unknown }).ZaloJavaScriptInterface;
    }
    vi.restoreAllMocks();
  });

  it('isInZalo() returns false when ZaloJavaScriptInterface is absent', () => {
    expect(zalo.isInZalo()).toBe(false);
  });

  it('isInZalo() returns true when ZaloJavaScriptInterface is present', () => {
    (window as { ZaloJavaScriptInterface?: unknown }).ZaloJavaScriptInterface = {};
    expect(zalo.isInZalo()).toBe(true);
  });

  it('accessToken() returns null in mock mode (no real SDK yet)', () => {
    expect(zalo.accessToken()).toBeNull();
  });

  it('openLink() delegates to window.open with safe rel flags', () => {
    const spy = vi.spyOn(window, 'open').mockImplementation(() => null);
    zalo.openLink('https://example.com/pay');
    expect(spy).toHaveBeenCalledWith('https://example.com/pay', '_blank', 'noopener,noreferrer');
  });

  it('ready() is a no-op and does not throw', () => {
    expect(() => zalo.ready()).not.toThrow();
  });
});
