package com.shop.delivery.order.api;

import com.shop.delivery.auth.api.CurrentUser;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.order.api.dto.SavedAddressResponse;
import com.shop.delivery.order.entity.SavedAddress;
import com.shop.delivery.order.service.SavedAddressService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer-facing saved-address API. Authentication via TelegramAuthFilter
 * (header X-Telegram-Init-Data → @CurrentUser). Addresses are auto-captured on
 * order placement; this API only reads them back and lets a customer prune the
 * list.
 */
@RestController
@RequestMapping("/api/addresses")
public class SavedAddressController {

    private final SavedAddressService service;

    public SavedAddressController(SavedAddressService service) {
        this.service = service;
    }

    @GetMapping
    public List<SavedAddressResponse> list(@CurrentUser TelegramUser user) {
        return service.list(user.getId()).stream().map(this::toResponse).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser TelegramUser user, @PathVariable Long id) {
        service.delete(id, user.getId());
    }

    private SavedAddressResponse toResponse(SavedAddress a) {
        return new SavedAddressResponse(a.getId(), a.getAddress(), a.getLat(), a.getLng(), a.getLastUsedAt());
    }
}
