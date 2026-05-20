package com.shop.delivery.order.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties(prefix = "shop")
public class ShopConfigProperties {

    @NotNull
    private Pickup pickup = new Pickup();

    @NotNull
    private Fee fee = new Fee();

    public Pickup getPickup() { return pickup; }
    public void setPickup(Pickup pickup) { this.pickup = pickup; }
    public Fee getFee() { return fee; }
    public void setFee(Fee fee) { this.fee = fee; }

    public static class Pickup {
        @NotNull private BigDecimal lat;
        @NotNull private BigDecimal lng;
        private String address = "Shop";

        public BigDecimal getLat() { return lat; }
        public void setLat(BigDecimal lat) { this.lat = lat; }
        public BigDecimal getLng() { return lng; }
        public void setLng(BigDecimal lng) { this.lng = lng; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
    }

    public static class Fee {
        @NotNull private BigDecimal base = new BigDecimal("15000");
        @NotNull private BigDecimal perKm = new BigDecimal("5000");
        @NotNull private BigDecimal freeKm = BigDecimal.ZERO;

        public BigDecimal getBase() { return base; }
        public void setBase(BigDecimal base) { this.base = base; }
        public BigDecimal getPerKm() { return perKm; }
        public void setPerKm(BigDecimal perKm) { this.perKm = perKm; }
        public BigDecimal getFreeKm() { return freeKm; }
        public void setFreeKm(BigDecimal freeKm) { this.freeKm = freeKm; }
    }
}
