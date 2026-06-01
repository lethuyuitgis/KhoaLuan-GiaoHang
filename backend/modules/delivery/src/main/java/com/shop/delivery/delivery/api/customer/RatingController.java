package com.shop.delivery.delivery.api.customer;

import com.shop.delivery.auth.api.CurrentUser;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.delivery.api.customer.dto.RateOrderRequest;
import com.shop.delivery.delivery.entity.Rating;
import com.shop.delivery.delivery.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping("/{id}/rating")
    public ResponseEntity<Rating> rate(
        @PathVariable("id") UUID orderId,
        @Valid @RequestBody RateOrderRequest body,
        @CurrentUser TelegramUser user
    ) {
        Rating r = ratingService.rate(orderId, user.getId(), body.stars(), body.comment());
        return ResponseEntity.ok(r);
    }
}
