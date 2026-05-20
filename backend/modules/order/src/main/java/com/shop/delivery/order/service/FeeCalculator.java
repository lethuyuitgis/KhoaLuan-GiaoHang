package com.shop.delivery.order.service;

import com.shop.delivery.order.config.ShopConfigProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FeeCalculator {

    private final ShopConfigProperties.Fee fee;

    public FeeCalculator(ShopConfigProperties props) {
        this.fee = props.getFee();
    }

    /** fee = base + max(0, distanceKm - freeKm) × perKm. Làm tròn xuống đồng VND (scale 0). */
    public BigDecimal calculate(BigDecimal distanceKm) {
        BigDecimal chargeable = distanceKm.subtract(fee.getFreeKm()).max(BigDecimal.ZERO);
        BigDecimal overage = chargeable.multiply(fee.getPerKm()).setScale(0, RoundingMode.HALF_UP);
        return fee.getBase().add(overage).setScale(0, RoundingMode.HALF_UP);
    }
}
