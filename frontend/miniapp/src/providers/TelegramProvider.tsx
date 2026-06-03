import { useEffect, type ReactNode } from 'react';
import i18n from 'i18next';
import { tg } from '@/lib/telegram';
import { normalizeLang } from '@/i18n';

interface Props {
  children: ReactNode;
}

export function TelegramProvider({ children }: Props) {
  useEffect(() => {
    tg.ready();
    tg.expand();

    // Sync language from Telegram if the user has not explicitly chosen one
    // (i.e. nothing persisted in localStorage). Telegram returns codes like
    // `vi`, `en`, `ko`, `ja`, `zh-Hans` — normalize to our 5 supported langs.
    try {
      const stored = localStorage.getItem('shop-lang');
      if (!stored) {
        const tgCode = tg.unsafeUser()?.language_code;
        if (tgCode) {
          const lng = normalizeLang(tgCode);
          if (lng !== i18n.language) i18n.changeLanguage(lng);
        }
      }
    } catch {
      // localStorage may be disabled in some embedded contexts — silent fallback
    }
  }, []);

  return <>{children}</>;
}
