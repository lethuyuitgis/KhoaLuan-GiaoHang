package com.shop.delivery.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
    Long id,
    String name,
    String description,
    BigDecimal price,
    String imageUrl,
    String category,
    Integer stock,
    boolean active,
    Instant createdAt
) {
}
