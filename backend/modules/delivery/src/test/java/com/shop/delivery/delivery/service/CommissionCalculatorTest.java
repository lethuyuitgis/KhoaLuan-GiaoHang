package com.shop.delivery.delivery.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class CommissionCalculatorTest {

    private final CommissionCalculator calc = new CommissionCalculator();

    @Test
    void typical_80pct_30k_fee_yields_24k() {
        assertThat(calc.commission(bd("30000"), bd("80")))
            .isEqualByComparingTo("24000");
    }

    @Test
    void zero_fee_yields_zero_commission() {
        assertThat(calc.commission(BigDecimal.ZERO, bd("80")))
            .isEqualByComparingTo("0");
    }

    @Test
    void zero_pct_yields_zero_commission() {
        assertThat(calc.commission(bd("30000"), BigDecimal.ZERO))
            .isEqualByComparingTo("0");
    }

    @Test
    void hundred_pct_yields_full_fee() {
        assertThat(calc.commission(bd("30000"), bd("100")))
            .isEqualByComparingTo("30000");
    }

    @Test
    void rounding_HALF_UP_to_zero_decimals() {
        // 33.33% of 15000 = 4999.5 → 5000
        assertThat(calc.commission(bd("15000"), bd("33.33")))
            .isEqualByComparingTo("5000");
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
}
