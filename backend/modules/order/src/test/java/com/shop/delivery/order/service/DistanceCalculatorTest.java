package com.shop.delivery.order.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DistanceCalculatorTest {

    DistanceCalculator calc = new DistanceCalculator();

    @Test
    void zeroDistanceWhenSamePoint() {
        BigDecimal d = calc.haversineKm(
            new BigDecimal("21.0285"), new BigDecimal("105.8542"),
            new BigDecimal("21.0285"), new BigDecimal("105.8542")
        );
        assertThat(d.doubleValue()).isCloseTo(0.0, within(0.001));
    }

    @Test
    void shouldComputeKnownDistanceHanoiToHoChiMinh() {
        // Hà Nội (Hoàn Kiếm) → TP.HCM (Bến Thành): ~1140 km
        BigDecimal d = calc.haversineKm(
            new BigDecimal("21.0285"), new BigDecimal("105.8542"),  // Hà Nội
            new BigDecimal("10.7720"), new BigDecimal("106.6986")   // TP.HCM
        );
        assertThat(d.doubleValue()).isCloseTo(1140, within(20.0));
    }

    @Test
    void shouldHandleShortUrbanDistance() {
        // 2 điểm cách nhau ~1km trong Hà Nội
        BigDecimal d = calc.haversineKm(
            new BigDecimal("21.0285"), new BigDecimal("105.8542"),
            new BigDecimal("21.0193"), new BigDecimal("105.8503")
        );
        assertThat(d.doubleValue()).isCloseTo(1.07, within(0.2));
    }
}
