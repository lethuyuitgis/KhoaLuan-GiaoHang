import { tg } from './telegram';

export const INIT_DATA_HEADER = 'X-Telegram-Init-Data';

export function getAuthHeaders(): Record<string, string> {
  const initData = tg.initData();
  if (!initData) return {};
  return { [INIT_DATA_HEADER]: initData };
}
