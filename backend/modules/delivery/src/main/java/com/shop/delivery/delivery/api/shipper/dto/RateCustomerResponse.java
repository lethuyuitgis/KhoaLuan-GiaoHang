package com.shop.delivery.delivery.api.shipper.dto;

import com.shop.delivery.delivery.entity.ShipperRating;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for {@code POST /api/shipper/orders/{id}/rate-customer}.
 * Omits customerId/shipperId — they're implicit from auth context.
 */
public record RateCustomerResponse(
    Long ratingId,
    UUID orderId,
    int stars,
    String comment,
    Instant createdAt
) {
    public static RateCustomerResponse from(ShipperRating rating) {
        return new RateCustomerResponse(
            rating.getId(),
            rating.getOrderId(),
            rating.getStars().intValue(),
            rating.getComment(),
            rating.getCreatedAt()
        );
    }
}
