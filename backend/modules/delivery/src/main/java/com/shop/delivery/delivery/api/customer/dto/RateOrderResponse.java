package com.shop.delivery.delivery.api.customer.dto;

import com.shop.delivery.delivery.entity.Rating;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for {@code POST /api/orders/{id}/rating} and the
 * {@code PATCH} comment-update endpoint.
 *
 * <p>Deliberately omits {@code customerId} (the rater's identity is
 * implicit from auth context) and {@code shipperId} (the FE/bot
 * already knows which shipper they're rating). Only the user-visible
 * fields surface.
 *
 * @param ratingId  surrogate PK
 * @param orderId   the rated order
 * @param stars     1..5
 * @param comment   nullable; the customer may rate without commenting
 * @param createdAt when the rating was first submitted
 */
public record RateOrderResponse(
    Long ratingId,
    UUID orderId,
    int stars,
    String comment,
    Instant createdAt   // BLOCKER fix B4: Rating.getCreatedAt() returns Instant, not OffsetDateTime
) {

    /** Convenience factory — keeps the controller call site clean. */
    public static RateOrderResponse from(Rating rating) {
        return new RateOrderResponse(
            rating.getId(),
            rating.getOrderId(),
            rating.getStars().intValue(),
            rating.getComment(),
            rating.getCreatedAt()
        );
    }
}
