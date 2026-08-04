package com.shop.delivery.delivery.api.admin.dto;

import com.shop.delivery.delivery.domain.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An AVAILABLE shipper offered as a candidate for assigning a specific order.
 * Enriches the shipper's rating with a best-effort distance to the order's
 * pickup point, derived from the shipper's last-known location ping.
 *
 * <p>{@code distanceKm} and {@code lastLocationAt} are null when the shipper has
 * no location history yet (never been on a delivery) — the Web Admin shows
 * "chưa rõ vị trí" and sorts them last.
 */
public record ShipperCandidateResponse(
    Long userId,
    String firstName,
    String lastName,
    String username,
    VehicleType vehicleType,
    String licensePlate,
    BigDecimal ratingAvg,
    Integer ratingCount,
    Integer totalDeliveries,
    BigDecimal distanceKm,
    Instant lastLocationAt
) {
}
