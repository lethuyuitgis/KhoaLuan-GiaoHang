package com.shop.delivery.promotion.service;

import com.shop.delivery.order.spi.VoucherApplicator;
import com.shop.delivery.promotion.domain.VoucherTarget;
import com.shop.delivery.promotion.domain.VoucherValidationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class VoucherApplicatorImpl implements VoucherApplicator {

    private final VoucherService service;

    public VoucherApplicatorImpl(VoucherService service) {
        this.service = service;
    }

    @Override
    public AppliedDiscount validate(String productsCode, String shippingCode,
                                    BigDecimal subtotal, BigDecimal deliveryFee, Long customerId) {
        BigDecimal dp = applyOne(productsCode, VoucherTarget.PRODUCTS, subtotal, deliveryFee, customerId);
        BigDecimal ds = applyOne(shippingCode, VoucherTarget.SHIPPING, subtotal, deliveryFee, customerId);
        return new AppliedDiscount(dp, ds);
    }

    private BigDecimal applyOne(String code, VoucherTarget target,
                                BigDecimal subtotal, BigDecimal deliveryFee, Long customerId) {
        if (code == null || code.isBlank()) return BigDecimal.ZERO;
        VoucherValidationResult r = service.validate(code, target, subtotal, deliveryFee, customerId);
        if (!r.isValid()) {
            throw new IllegalArgumentException("INVALID_VOUCHER_" + target + "_" + r.reason());
        }
        return r.discountAmount();
    }

    @Override
    public void redeem(String productsCode, String shippingCode,
                       BigDecimal productsDiscount, BigDecimal shippingDiscount,
                       UUID orderId, Long customerId) {
        if (productsCode != null && !productsCode.isBlank() && productsDiscount.signum() > 0) {
            service.redeem(productsCode, orderId, customerId, productsDiscount);
        }
        if (shippingCode != null && !shippingCode.isBlank() && shippingDiscount.signum() > 0) {
            service.redeem(shippingCode, orderId, customerId, shippingDiscount);
        }
    }
}
