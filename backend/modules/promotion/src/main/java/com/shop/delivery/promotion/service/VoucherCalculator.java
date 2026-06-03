package com.shop.delivery.promotion.service;

import com.shop.delivery.promotion.domain.DiscountType;
import com.shop.delivery.promotion.entity.Voucher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Computes the discount a voucher applies against a base amount.
 *
 * `base` is whichever part of the order the voucher reduces — for a SHIPPING
 * voucher pass the delivery fee, for a PRODUCTS voucher pass the subtotal.
 * The returned discount is always non-negative and never exceeds `base`.
 */
@Component
public class VoucherCalculator {

    public BigDecimal discountFor(Voucher voucher, BigDecimal base) {
        if (base == null || base.signum() <= 0) return BigDecimal.ZERO;

        BigDecimal raw = voucher.getDiscountType() == DiscountType.FIXED
            ? voucher.getDiscountValue()
            : base.multiply(voucher.getDiscountValue())
                  .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);

        BigDecimal capped = voucher.getMaxDiscount() != null
            ? raw.min(voucher.getMaxDiscount())
            : raw;

        return capped.min(base).setScale(0, RoundingMode.HALF_UP);
    }
}
