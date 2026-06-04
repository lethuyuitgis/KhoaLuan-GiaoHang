package com.shop.delivery.order.api.dto;

import java.math.BigDecimal;

/**
 * Shop branding + pickup info exposed publicly to mini apps.
 * Includes pickup coords (for client-side distance preview) but never sensitive
 * fields. Fees are included because miniapp uses them for the "Order: {{total}} + ship"
 * estimate before checkout.
 */
public record ShopConfigResponse(
    String name,
    String tagline,
    String logoUrl,
    String brandPrimary,
    String brandSecondary,
    String contactPhone,
    String contactEmail,
    String openingHours,
    BigDecimal pickupLat,
    BigDecimal pickupLng,
    String pickupAddress,
    BigDecimal feeBase,
    BigDecimal feePerKm,
    BigDecimal freeKm,
    BigDecimal shipperCommissionPct
) {
}
