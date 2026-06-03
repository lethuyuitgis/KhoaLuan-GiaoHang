package com.shop.delivery.promotion.domain;

import com.shop.delivery.promotion.entity.Voucher;
import java.math.BigDecimal;

/**
 * Either { voucher, discountAmount } if the voucher passes all checks, or
 * { reason } pointing to the first failing rule.
 */
public record VoucherValidationResult(
    Voucher voucher,
    BigDecimal discountAmount,
    VoucherInvalidReason reason
) {
    public boolean isValid() { return reason == null; }

    public static VoucherValidationResult ok(Voucher v, BigDecimal discount) {
        return new VoucherValidationResult(v, discount, null);
    }

    public static VoucherValidationResult invalid(VoucherInvalidReason reason) {
        return new VoucherValidationResult(null, null, reason);
    }
}
