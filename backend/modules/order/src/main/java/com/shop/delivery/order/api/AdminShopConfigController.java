package com.shop.delivery.order.api;

import com.shop.delivery.order.api.dto.ShopConfigResponse;
import com.shop.delivery.order.api.dto.UpdateShopConfigRequest;
import com.shop.delivery.order.service.ShopConfigService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint for the Settings page. Same payload shape as public read
 * (so the form pre-fills cleanly) but writable.
 */
@RestController
@RequestMapping("/api/admin/shop-config")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminShopConfigController {

    private final ShopConfigService service;

    public AdminShopConfigController(ShopConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ShopConfigResponse get() {
        return service.getConfig();
    }

    @PutMapping
    public ShopConfigResponse update(@Valid @RequestBody UpdateShopConfigRequest req) {
        return service.updateConfig(req);
    }
}
