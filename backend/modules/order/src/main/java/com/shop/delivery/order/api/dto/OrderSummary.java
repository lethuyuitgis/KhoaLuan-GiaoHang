package com.shop.delivery.order.api.dto;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummary(
    UUID id,
    String code,
    BigDecimal total,
    OrderStatus status,
    PaymentMethod paymentMethod,
    PaymentStatus paymentStatus,
    Instant createdAt
) {
}
