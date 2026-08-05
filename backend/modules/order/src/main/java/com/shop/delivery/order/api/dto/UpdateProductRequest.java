package com.shop.delivery.order.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record UpdateProductRequest(
    String name,
    String description,
    @DecimalMin(value = "0.0", inclusive = true) BigDecimal price,
    String imageUrl,
    @Pattern(regexp = "food|drink|dessert") String category,
    @Min(0) Integer stock
) {
}
