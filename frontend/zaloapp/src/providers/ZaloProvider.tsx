import { useEffect, type ReactNode } from 'react';
import i18n from 'i18next';
import { zalo } from '@/lib/zalo';
import { normalizeLang } from '@/i18n';

interface Props {
  children: ReactNode;
}

/**
 * Mirrors TelegramProvider — calls into the SDK wrapper once on mount so
 * the Zalo container (or mock) can finish its handshake before any page
 * tries to read accessToken / open links.
 *
 * The real Zalo Mini App SDK exposes `getUserInfo()` which can return
 * `language` (e.g. `en`, `vi`). For now we rely on
 * i18next-browser-languagedetector against `navigator.language` plus the
 * persisted `shop-lang` localStorage choice; once `zmp-sdk` is wired in,
 * read `language` from `getUserInfo` and `i18n.changeLanguage(normalizeLang(...))`.
 */
export function ZaloProvider({ children }: Props) {
  useEffect(() => {
    zalo.ready();
    // Touch normalizeLang so the import is not tree-shaken when the SDK
    // upgrade is performed later. Detection in this mock is browser-driven.
    void normalizeLang;
    void i18n;
  }, []);

  return <>{children}</>;
}
