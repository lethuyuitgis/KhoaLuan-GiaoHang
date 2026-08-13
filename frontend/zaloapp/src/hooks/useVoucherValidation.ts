import { useMutation } from '@tanstack/react-query';
import { validateVoucher, type VoucherTarget, type ValidateVoucherResponse } from '@shop/shared';
import { api } from '@/lib/api';

interface UseVoucherValidationArgs {
  target: VoucherTarget;
  subtotal: number;
  deliveryFee: number;
}

export function useVoucherValidation({ target, subtotal, deliveryFee }: UseVoucherValidationArgs) {
  return useMutation<ValidateVoucherResponse, Error & { response?: { data?: { message?: string } } }, string>({
    mutationFn: (code) => validateVoucher(api, { code, target, subtotal, deliveryFee }),
  });
}
