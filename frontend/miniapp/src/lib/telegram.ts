import WebApp from '@twa-dev/sdk';

/**
 * Wrapper xung quanh Telegram WebApp SDK.
 * Khi mở Mini App từ Telegram → window.Telegram.WebApp có sẵn.
 * Khi mở từ browser thường → WebApp.initData rỗng → dev mode banner sẽ hiển thị.
 */
export const tg = {
  isInTelegram(): boolean {
    return Boolean(WebApp.initData && WebApp.initData.length > 0);
  },

  initData(): string {
    return WebApp.initData;
  },

  unsafeUser() {
    return WebApp.initDataUnsafe.user ?? null;
  },

  ready() {
    WebApp.ready();
  },

  expand() {
    WebApp.expand();
  },

  close() {
    WebApp.close();
  },

  showAlert(message: string): Promise<void> {
    return new Promise(resolve => WebApp.showAlert(message, () => resolve()));
  },

  showConfirm(message: string): Promise<boolean> {
    return new Promise(resolve => WebApp.showConfirm(message, ok => resolve(ok)));
  },

  haptic: WebApp.HapticFeedback,
  mainButton: WebApp.MainButton,
  backButton: WebApp.BackButton,
  theme: () => WebApp.colorScheme,
};
