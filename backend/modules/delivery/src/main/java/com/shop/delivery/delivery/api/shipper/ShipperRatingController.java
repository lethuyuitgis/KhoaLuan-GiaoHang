package com.shop.delivery.delivery.api.shipper;

import com.shop.delivery.auth.api.CurrentUser;
import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.service.RoleResolver;
import com.shop.delivery.delivery.api.shipper.dto.RateCustomerRequest;
import com.shop.delivery.delivery.api.shipper.dto.RateCustomerResponse;
import com.shop.delivery.delivery.entity.ShipperRating;
import com.shop.delivery.delivery.service.ShipperRatingService;
import com.shop.delivery.shared.exception.AuthenticationException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Shipper-side "rate the customer" endpoint. The aggregate result is NOT
 * exposed to the customer — only the shipper (and admin reports) can read it.
 * Counterpart to {@code RatingController} (customer rates shipper).
 */
@RestController
@RequestMapping("/api/shipper/orders")
public class ShipperRatingController {

    private final ShipperRatingService service;
    private final RoleResolver roleResolver;

    public ShipperRatingController(ShipperRatingService service, RoleResolver roleResolver) {
        this.service = service;
        this.roleResolver = roleResolver;
    }

    @PostMapping("/{id}/rate-customer")
    public ResponseEntity<RateCustomerResponse> rateCustomer(
        @PathVariable("id") UUID orderId,
        @Valid @RequestBody RateCustomerRequest body,
        @CurrentUser TelegramUser user
    ) {
        if (!roleResolver.hasRole(user.getId(), Role.SHIPPER)) {
            throw new AuthenticationException("NOT_SHIPPER", "Bạn không phải shipper");
        }
        ShipperRating r = service.rateCustomer(orderId, user.getId(), body.stars(), body.comment());
        return ResponseEntity.ok(RateCustomerResponse.from(r));
    }
}
