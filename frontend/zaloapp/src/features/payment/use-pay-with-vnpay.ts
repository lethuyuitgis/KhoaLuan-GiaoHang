import { useMutation } from '@tanstack/react-query';
import { createVnpayPayment } from '@shop/shared';
import { api } from '@/lib/api';
import { zalo } from '@/lib/zalo';
import { useToast } from '@/components/Toast';

/**
 * Calls POST /api/payment/vnpay/create for the given order, then opens the
 * resulting VNPay URL via the Zalo SDK wrapper (real SDK uses
 * `openWebview({ url })`; mock falls back to window.open).
 *
 * OrderDetailPage's polling picks up the SUCCESS status when IPN completes.
 */
export function usePayWithVnpay() {
  const toast = useToast();
  return useMutation({
    mutationFn: (orderId: string) => createVnpayPayment(api, { orderId }),
    onSuccess: ({ paymentUrl }) => {
      zalo.openLink(paymentUrl);
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? 'Không khởi tạo được thanh toán VNPay';
      toast.error(msg);
    },
  });
}
