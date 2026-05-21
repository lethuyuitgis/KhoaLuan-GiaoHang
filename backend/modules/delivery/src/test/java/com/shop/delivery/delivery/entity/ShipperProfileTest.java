package com.shop.delivery.delivery.entity;

import com.shop.delivery.delivery.domain.ShipperState;
import com.shop.delivery.delivery.domain.VehicleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ShipperProfileTest {

    @Test
    void shouldExposeFieldsAndDefaults() {
        ShipperProfile p = new ShipperProfile();
        p.setUserId(8888L);
        p.setVehicleType(VehicleType.MOTORBIKE);
        p.setLicensePlate("29A-12345");
        p.setCurrentState(ShipperState.AVAILABLE);

        assertThat(p.getUserId()).isEqualTo(8888L);
        assertThat(p.getVehicleType()).isEqualTo(VehicleType.MOTORBIKE);
        assertThat(p.getLicensePlate()).isEqualTo("29A-12345");
        assertThat(p.getCurrentState()).isEqualTo(ShipperState.AVAILABLE);
        assertThat(p.getRatingAvg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(p.getRatingCount()).isZero();
        assertThat(p.getTotalDeliveries()).isZero();
    }

    @Test
    void newInstanceShouldDefaultToOfflineState() {
        ShipperProfile p = new ShipperProfile();
        assertThat(p.getCurrentState()).isEqualTo(ShipperState.OFFLINE);
    }
}
