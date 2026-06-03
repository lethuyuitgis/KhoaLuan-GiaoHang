package com.shop.delivery.promotion.service;

import com.shop.delivery.promotion.domain.VoucherInvalidReason;

public class VoucherRedeemException extends RuntimeException {
    private final VoucherInvalidReason reason;

    public VoucherRedeemException(VoucherInvalidReason reason) {
        super("Cannot redeem voucher: " + reason);
        this.reason = reason;
    }

    public VoucherInvalidReason getReason() { return reason; }
}
