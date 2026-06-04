package com.shop.delivery.delivery.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure function: commission = deliveryFeeOriginal × pct / 100, rounded HALF_UP to VND.
 *
 * `deliveryFeeOriginal` is the snapshot from `orders.delivery_fee_original` (Phase 14)
 * — NOT `orders.delivery_fee`. This is intentional: SHIPPING vouchers reduce what
 * the customer pays but shop absorbs the cost; shipper still earns commission on
 * the original fee.
 */
@Component
public class CommissionCalculator {

    public BigDecimal commission(BigDecimal deliveryFeeOriginal, BigDecimal commissionPct) {
        if (deliveryFeeOriginal == null || deliveryFeeOriginal.signum() <= 0) return BigDecimal.ZERO;
        if (commissionPct == null || commissionPct.signum() <= 0) return BigDecimal.ZERO;
        return deliveryFeeOriginal.multiply(commissionPct)
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }
}
