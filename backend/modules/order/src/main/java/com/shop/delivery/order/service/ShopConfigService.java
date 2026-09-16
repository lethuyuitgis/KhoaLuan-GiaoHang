package com.shop.delivery.order.service;

import com.shop.delivery.order.api.dto.ShopConfigResponse;
import com.shop.delivery.order.api.dto.UpdateShopConfigRequest;
import com.shop.delivery.order.entity.ShopConfig;
import com.shop.delivery.order.repository.ShopConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * CRUD around the singleton shop config row. The DB migration seeds id = 1
 * with sensible defaults so this service can always assume the row exists.
 */
@Service
public class ShopConfigService {

    private final ShopConfigRepository repo;

    public ShopConfigService(ShopConfigRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public ShopConfigResponse getConfig() {
        return toResponse(loadOrThrow());
    }

    /**
     * Bản ghi cấu hình shop hiện tại (singleton) đọc trực tiếp từ DB — để FeeCalculator
     * và tính khoảng cách dùng phí/toạ độ pickup LIVE (admin chỉnh Settings là ăn ngay),
     * không còn phụ thuộc env cố định lúc khởi động.
     */
    @Transactional(readOnly = true)
    public ShopConfig currentConfig() {
        return loadOrThrow();
    }

    @Transactional
    public ShopConfigResponse updateConfig(UpdateShopConfigRequest req) {
        ShopConfig c = loadOrThrow();
        c.setName(req.name());
        c.setTagline(req.tagline() == null ? "" : req.tagline());
        c.setLogoUrl(req.logoUrl());
        c.setBrandPrimary(req.brandPrimary());
        c.setBrandSecondary(req.brandSecondary());
        c.setContactPhone(req.contactPhone());
        c.setContactEmail(req.contactEmail());
        c.setOpeningHours(req.openingHours());
        c.setPickupLat(req.pickupLat());
        c.setPickupLng(req.pickupLng());
        c.setPickupAddress(req.pickupAddress());
        c.setFeeBase(req.feeBase());
        c.setFeePerKm(req.feePerKm());
        c.setFreeKm(req.freeKm());
        c.setShipperCommissionPct(req.shipperCommissionPct());
        return toResponse(repo.save(c));
    }

    private ShopConfig loadOrThrow() {
        return repo.findById(ShopConfigRepository.SINGLETON_ID)
            .orElseThrow(() -> new NoSuchElementException(
                "shop_config row missing — V13 migration must run first"));
    }

    private ShopConfigResponse toResponse(ShopConfig c) {
        return new ShopConfigResponse(
            c.getName(),
            c.getTagline(),
            c.getLogoUrl(),
            c.getBrandPrimary(),
            c.getBrandSecondary(),
            c.getContactPhone(),
            c.getContactEmail(),
            c.getOpeningHours(),
            c.getPickupLat(),
            c.getPickupLng(),
            c.getPickupAddress(),
            c.getFeeBase(),
            c.getFeePerKm(),
            c.getFreeKm(),
            c.getShipperCommissionPct()
        );
    }
}
