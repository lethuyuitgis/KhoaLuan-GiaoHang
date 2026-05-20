package com.shop.delivery.order.api.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
    Long id,
    Long productId,
    Integer quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal
) {
}
