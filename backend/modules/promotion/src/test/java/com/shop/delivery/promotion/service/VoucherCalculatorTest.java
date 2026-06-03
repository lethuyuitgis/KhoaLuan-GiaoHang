package com.shop.delivery.promotion.service;

import com.shop.delivery.promotion.domain.DiscountType;
import com.shop.delivery.promotion.entity.Voucher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherCalculatorTest {

    private final VoucherCalculator calc = new VoucherCalculator();

    @Test
    void fixed_belowBase_returnsValue() {
        Voucher v = vFixed("20000");
        assertThat(calc.discountFor(v, bd("100000"))).isEqualByComparingTo("20000");
    }

    @Test
    void fixed_aboveBase_cappedAtBase() {
        Voucher v = vFixed("50000");
        assertThat(calc.discountFor(v, bd("30000"))).isEqualByComparingTo("30000");
    }

    @Test
    void fixed_zeroBase_returnsZero() {
        Voucher v = vFixed("20000");
        assertThat(calc.discountFor(v, BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void percent_withoutCap_appliesPercent() {
        Voucher v = vPercent("20", null);
        assertThat(calc.discountFor(v, bd("100000"))).isEqualByComparingTo("20000");
    }

    @Test
    void percent_withCap_belowCap_appliesPercent() {
        Voucher v = vPercent("20", "50000");
        assertThat(calc.discountFor(v, bd("100000"))).isEqualByComparingTo("20000");
    }

    @Test
    void percent_withCap_aboveCap_capsAtMax() {
        Voucher v = vPercent("50", "30000");
        assertThat(calc.discountFor(v, bd("100000"))).isEqualByComparingTo("30000");
    }

    @Test
    void percent_resultRoundedHalfUp() {
        // 12.5% of 9999 = 1249.875 → 1250
        Voucher v = vPercent("12.5", null);
        assertThat(calc.discountFor(v, bd("9999"))).isEqualByComparingTo("1250");
    }

    @Test
    void discount_neverExceedsBase() {
        // Even a 200% voucher caps at base.
        Voucher v = vPercent("200", null);
        assertThat(calc.discountFor(v, bd("100000"))).isEqualByComparingTo("100000");
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private static Voucher vFixed(String value) {
        Voucher v = new Voucher();
        v.setDiscountType(DiscountType.FIXED);
        v.setDiscountValue(bd(value));
        return v;
    }

    private static Voucher vPercent(String pct, String cap) {
        Voucher v = new Voucher();
        v.setDiscountType(DiscountType.PERCENT);
        v.setDiscountValue(bd(pct));
        if (cap != null) v.setMaxDiscount(bd(cap));
        return v;
    }
}
