package com.shop.delivery.order.spi;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * SPI implemented by the promotion module (loaded at runtime by Spring) so that
 * the order module can apply voucher discounts without taking a compile-time
 * dependency on promotion.
 *
 * Implementations should validate both codes first (without writing), then —
 * after the order row is committed — redeem each non-null code by id.
 */
public interface VoucherApplicator {
    record AppliedDiscount(BigDecimal products, BigDecimal shipping) {}

    /** Compute discount for both targets. Throws IllegalArgumentException with
     *  message "INVALID_VOUCHER_&lt;TARGET&gt;_&lt;REASON&gt;" if either code is invalid. */
    AppliedDiscount validate(String productsCode, String shippingCode,
                             BigDecimal subtotal, BigDecimal deliveryFee, Long customerId);

    /** After order persisted, increment used_count + log redemption.
     *  Pass discount amounts so log records actual applied value. */
    void redeem(String productsCode, String shippingCode,
                BigDecimal productsDiscount, BigDecimal shippingDiscount,
                UUID orderId, Long customerId);
}
