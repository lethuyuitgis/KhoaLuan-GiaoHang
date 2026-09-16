package com.shop.delivery.order.service;

import com.shop.delivery.order.config.ShopConfigProperties;
import com.shop.delivery.order.entity.ShopConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeeCalculatorTest {

    FeeCalculator calc;

    @BeforeEach
    void setup() {
        // FeeCalculator giờ đọc phí LIVE từ shop_config (DB) qua ShopConfigService.
        ShopConfig cfg = new ShopConfig();
        cfg.setFeeBase(new BigDecimal("15000"));
        cfg.setFeePerKm(new BigDecimal("5000"));
        cfg.setFreeKm(new BigDecimal("1.0"));
        ShopConfigService svc = mock(ShopConfigService.class);
        when(svc.currentConfig()).thenReturn(cfg);

        ShopConfigProperties props = new ShopConfigProperties();
        props.setFee(new ShopConfigProperties.Fee());   // fallback (không dùng khi DB có)
        calc = new FeeCalculator(svc, props);
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
