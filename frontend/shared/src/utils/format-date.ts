import { format, formatDistanceToNow, parseISO, type Locale } from 'date-fns';
import { vi, enUS, ko, ja, zhCN } from 'date-fns/locale';

const LOCALES: Record<string, Locale> = {
  vi,
  en: enUS,
  ko,
  ja,
  zh: zhCN,
};

function pickLocale(code?: string): Locale {
  if (!code) return vi;
  const head = code.toLowerCase().split('-')[0];
  return LOCALES[head] ?? vi;
}

export function formatDateTime(iso: string, locale?: string): string {
  try {
    return format(parseISO(iso), 'HH:mm dd/MM/yyyy', { locale: pickLocale(locale) });
  } catch {
    return iso;
  }
}

export function formatRelative(iso: string, locale?: string): string {
  try {
    return formatDistanceToNow(parseISO(iso), { locale: pickLocale(locale), addSuffix: true });
  } catch {
    return iso;
  }
}
