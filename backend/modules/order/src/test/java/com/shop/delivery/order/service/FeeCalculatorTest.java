package com.shop.delivery.order.service;

import com.shop.delivery.order.config.ShopConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FeeCalculatorTest {

    FeeCalculator calc;
    ShopConfigProperties.Fee fee;

    @BeforeEach
    void setup() {
        fee = new ShopConfigProperties.Fee();
        fee.setBase(new BigDecimal("15000"));
        fee.setPerKm(new BigDecimal("5000"));
        fee.setFreeKm(new BigDecimal("1.0"));
        ShopConfigProperties props = new ShopConfigProperties();
        props.setFee(fee);
        calc = new FeeCalculator(props);
    }

    @Test
    void shortDistanceWithinFreeKmShouldChargeBase() {
        BigDecimal feeAmt = calc.calculate(new BigDecimal("0.8"));
        assertThat(feeAmt).isEqualByComparingTo("15000");
    }

    @Test
    void distanceExceedingFreeKmShouldChargeBaseAndOverage() {
        // 3km - 1km free = 2km * 5000 = 10000 + base 15000 = 25000
        BigDecimal feeAmt = calc.calculate(new BigDecimal("3.0"));
        assertThat(feeAmt).isEqualByComparingTo("25000");
    }

    @Test
    void exactlyFreeKmShouldChargeBaseOnly() {
        BigDecimal feeAmt = calc.calculate(new BigDecimal("1.0"));
        assertThat(feeAmt).isEqualByComparingTo("15000");
    }
}
