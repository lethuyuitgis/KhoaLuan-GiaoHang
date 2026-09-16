package com.shop.delivery.order.service;

import com.shop.delivery.order.config.ShopConfigProperties;
import com.shop.delivery.order.entity.ShopConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FeeCalculator {

    private static final Logger log = LoggerFactory.getLogger(FeeCalculator.class);

    /** Phí LIVE từ DB shop_config (admin chỉnh Web Admin → Cài đặt là ăn ngay). */
    private final ShopConfigService shopConfigService;
    /** Fallback dùng env (SHOP_FEE_*) khi shop_config chưa sẵn sàng. */
    private final ShopConfigProperties.Fee fallback;

    public FeeCalculator(ShopConfigService shopConfigService, ShopConfigProperties props) {
        this.shopConfigService = shopConfigService;
        this.fallback = props.getFee();
    }

    /** fee = base + max(0, distanceKm - freeKm) × perKm. base/perKm/freeKm đọc LIVE từ shop_config. */
    public BigDecimal calculate(BigDecimal distanceKm) {
        BigDecimal base, perKm, freeKm;
        try {
            ShopConfig c = shopConfigService.currentConfig();
            base = c.getFeeBase();
            perKm = c.getFeePerKm();
            freeKm = c.getFreeKm();
        } catch (Exception e) {
            log.warn("shop_config chưa sẵn sàng — dùng phí env fallback: {}", e.getMessage());
            base = fallback.getBase();
            perKm = fallback.getPerKm();
            freeKm = fallback.getFreeKm();
        }
        BigDecimal chargeable = distanceKm.subtract(freeKm).max(BigDecimal.ZERO);
        BigDecimal overage = chargeable.multiply(perKm).setScale(0, RoundingMode.HALF_UP);
        return base.add(overage).setScale(0, RoundingMode.HALF_UP);
    }
}
