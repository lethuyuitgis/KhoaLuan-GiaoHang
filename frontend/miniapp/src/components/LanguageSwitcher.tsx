import { useState, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { SUPPORTED_LANGS, type SupportedLang } from '@/i18n';

const FLAGS: Record<SupportedLang, string> = {
  vi: '🇻🇳',
  en: '🇬🇧',
  ko: '🇰🇷',
  ja: '🇯🇵',
  zh: '🇨🇳',
};

const SHORT: Record<SupportedLang, string> = {
  vi: 'VI',
  en: 'EN',
  ko: 'KO',
  ja: 'JA',
  zh: 'ZH',
};

interface Props {
  /** Pill style ('light' for hero on colored bg, 'dark' for white surfaces). */
  variant?: 'light' | 'dark';
}

/**
 * Compact language picker — flag + 2-letter code, opens a small list on click.
 * Designed to live on the catalog hero (light variant) but works anywhere.
 */
export function LanguageSwitcher({ variant = 'light' }: Props) {
  const { i18n, t } = useTranslation();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement | null>(null);

  // Resolve current to a supported code (i18n.language may carry e.g. "en-US").
  const current = (SUPPORTED_LANGS as readonly string[]).includes(i18n.language)
    ? (i18n.language as SupportedLang)
    : (i18n.resolvedLanguage as SupportedLang | undefined) ?? 'vi';

  useEffect(() => {
    if (!open) return;
    function onDocClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [open]);

  function pick(code: SupportedLang) {
    i18n.changeLanguage(code);
    setOpen(false);
  }

  const triggerCls =
    variant === 'light'
      ? 'bg-white/15 backdrop-blur text-white hover:bg-white/25'
      : 'bg-gray-100 text-gray-700 hover:bg-gray-200';

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen(o => !o)}
        className={`rounded-full px-2.5 py-1.5 text-xs font-semibold inline-flex items-center gap-1.5 transition ${triggerCls}`}
        aria-label={t('lang.label')}
        aria-haspopup="listbox"
        aria-expanded={open}
      >
        <span aria-hidden="true">{FLAGS[current]}</span>
        <span>{SHORT[current]}</span>
        <span aria-hidden="true" className="text-[10px] opacity-75">▾</span>
      </button>
      {open && (
        <ul
          role="listbox"
          className="absolute right-0 top-full mt-1 z-[1200] w-36 bg-white rounded-xl shadow-lg ring-1 ring-black/5 py-1 overflow-hidden"
        >
          {SUPPORTED_LANGS.map(code => (
            <li key={code} role="option" aria-selected={current === code}>
              <button
                type="button"
                onClick={() => pick(code)}
                className={
                  'w-full text-left px-3 py-2 text-sm flex items-center gap-2 transition ' +
                  (current === code
                    ? 'bg-orange-50 text-orange-700 font-semibold'
                    : 'text-gray-700 hover:bg-gray-50')
                }
              >
                <span aria-hidden="true">{FLAGS[code]}</span>
                <span className="flex-1">{t(`lang.${code}`)}</span>
                {current === code && <span aria-hidden="true">✓</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
