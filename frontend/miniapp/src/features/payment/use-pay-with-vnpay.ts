import { useMutation } from '@tanstack/react-query';
import WebApp from '@twa-dev/sdk';
import { createVnpayPayment } from '@shop/shared';
import { api } from '@/lib/api';
import { tg } from '@/lib/telegram';

/**
 * Calls POST /api/payment/vnpay/create for the given order, then opens the
 * resulting VNPay URL via Telegram.WebApp.openLink (NOT window.location.href —
 * see research §Pitfall 6: window.location closes the Mini App container).
 *
 * onSuccess returns immediately after opening the link; the Mini App stays
 * alive underneath the Telegram overlay browser. OrderDetailPage's polling
 * picks up the SUCCESS status when IPN completes.
 */
export function usePayWithVnpay() {
  return useMutation({
    mutationFn: (orderId: string) => createVnpayPayment(api, { orderId }),
    onSuccess: ({ paymentUrl }) => {
      if (tg.isInTelegram()) {
        WebApp.openLink(paymentUrl, { try_instant_view: false });
      } else {
        // Dev / outside-Telegram fallback (Vite dev mode in plain browser)
        window.open(paymentUrl, '_blank', 'noopener,noreferrer');
      }
    },
    onError: async (err: any) => {
      const msg = err?.response?.data?.message ?? 'Không khởi tạo được thanh toán VNPay';
      if (tg.isInTelegram()) await tg.showAlert(msg);
      else alert(msg);
    },
  });
}
