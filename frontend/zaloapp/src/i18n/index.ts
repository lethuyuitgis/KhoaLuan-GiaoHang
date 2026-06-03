import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import vi from './locales/vi.json';
import en from './locales/en.json';
import ko from './locales/ko.json';
import ja from './locales/ja.json';
import zh from './locales/zh.json';

export const SUPPORTED_LANGS = ['vi', 'en', 'ko', 'ja', 'zh'] as const;
export type SupportedLang = (typeof SUPPORTED_LANGS)[number];

/**
 * Map a BCP-47-ish language code (vi, en-US, zh-Hans, ko-KR, ja-JP, …)
 * to one of our 5 supported languages. Falls back to `vi`.
 *
 * Telegram returns codes like `vi`, `en`, `ko`, `ja`, `zh-Hans`, `zh-Hant`.
 * Browser `navigator.language` may include region (en-US, zh-CN, …).
 */
export function normalizeLang(input: string | undefined | null): SupportedLang {
  if (!input) return 'vi';
  const lower = input.toLowerCase();
  if (lower.startsWith('vi')) return 'vi';
  if (lower.startsWith('en')) return 'en';
  if (lower.startsWith('ko')) return 'ko';
  if (lower.startsWith('ja')) return 'ja';
  if (lower.startsWith('zh')) return 'zh';
  return 'vi';
}

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      vi: { translation: vi },
      en: { translation: en },
      ko: { translation: ko },
      ja: { translation: ja },
      zh: { translation: zh },
    },
    fallbackLng: 'vi',
    supportedLngs: [...SUPPORTED_LANGS],
    nonExplicitSupportedLngs: true, // accept en-US → en, zh-Hans → zh
    load: 'languageOnly',
    interpolation: { escapeValue: false },
    detection: {
      order: ['localStorage', 'navigator', 'htmlTag'],
      caches: ['localStorage'],
      lookupLocalStorage: 'shop-lang',
    },
    returnNull: false,
  });

export default i18n;
