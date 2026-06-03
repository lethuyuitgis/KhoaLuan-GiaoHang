package com.shop.delivery.promotion.entity;

import com.shop.delivery.promotion.domain.DiscountType;
import com.shop.delivery.promotion.domain.VoucherTarget;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherTest {

    @Test
    void newVoucher_hasDefaults() {
        Voucher v = new Voucher();
        v.setCode("DEMO");
        v.setName("Demo");
        v.setTarget(VoucherTarget.PRODUCTS);
        v.setDiscountType(DiscountType.FIXED);
        v.setDiscountValue(new BigDecimal("10000"));
        v.setValidFrom(OffsetDateTime.now());
        v.setValidUntil(OffsetDateTime.now().plusDays(1));

        assertThat(v.getMaxUsesPerCustomer()).isEqualTo(1);
        assertThat(v.getUsedCount()).isZero();
        assertThat(v.isActive()).isTrue();
        assertThat(v.getMinOrderAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
