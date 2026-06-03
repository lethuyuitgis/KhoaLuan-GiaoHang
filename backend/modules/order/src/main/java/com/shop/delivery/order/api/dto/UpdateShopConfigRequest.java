package com.shop.delivery.order.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Admin write payload for updating the singleton shop config.
 * Hex colors validated strict (#RRGGBB only — no shorthand or alpha).
 */
public record UpdateShopConfigRequest(
    @NotBlank @Size(min = 3, max = 128) String name,
    @Size(max = 256)                    String tagline,
                                        String logoUrl,
    @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String brandPrimary,
    @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String brandSecondary,
    @Pattern(regexp = "^[+0-9\\s\\-]{8,16}$") String contactPhone,
    @Email                              String contactEmail,
    @Size(max = 64)                     String openingHours,

    @NotNull                            BigDecimal pickupLat,
    @NotNull                            BigDecimal pickupLng,
    @NotBlank @Size(min = 5, max = 256) String pickupAddress,

    @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal feeBase,
    @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal feePerKm,
    @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal freeKm
) {
}
