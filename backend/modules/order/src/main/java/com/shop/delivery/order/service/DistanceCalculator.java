package com.shop.delivery.order.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class DistanceCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    /**
     * Haversine: tính khoảng cách great-circle (km) giữa 2 điểm trên Trái Đất.
     * Trả về BigDecimal scale 3 (làm tròn HALF_UP).
     */
    public BigDecimal haversineKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double dPhi = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLambda = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());

        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                 + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double km = EARTH_RADIUS_KM * c;
        return BigDecimal.valueOf(km).setScale(3, RoundingMode.HALF_UP);
    }
}
