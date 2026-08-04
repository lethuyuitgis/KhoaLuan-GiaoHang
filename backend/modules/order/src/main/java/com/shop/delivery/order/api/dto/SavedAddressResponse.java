package com.shop.delivery.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** A customer's saved delivery address, for Mini App autocomplete. */
public record SavedAddressResponse(
    Long id,
    String address,
    BigDecimal lat,
    BigDecimal lng,
    Instant lastUsedAt
) {
}
