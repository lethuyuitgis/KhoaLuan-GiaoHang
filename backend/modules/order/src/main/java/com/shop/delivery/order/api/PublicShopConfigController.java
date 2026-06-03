package com.shop.delivery.order.api;

import com.shop.delivery.order.api.dto.ShopConfigResponse;
import com.shop.delivery.order.service.ShopConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint — no auth required. Mini apps fetch this on splash to
 * theme themselves (brand colors, logo, name). Safe to expose because we
 * never return secrets here.
 */
@RestController
@RequestMapping("/api/public/shop-config")
public class PublicShopConfigController {

    private final ShopConfigService service;

    public PublicShopConfigController(ShopConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ShopConfigResponse get() {
        return service.getConfig();
    }
}
