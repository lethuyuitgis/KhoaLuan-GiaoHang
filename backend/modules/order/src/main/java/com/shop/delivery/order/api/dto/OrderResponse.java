package com.shop.delivery.order.api.dto;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    String code,
    Long customerId,
    String customerName,
    String customerPhone,
    BigDecimal pickupLat,
    BigDecimal pickupLng,
    String deliveryAddress,
    BigDecimal deliveryLat,
    BigDecimal deliveryLng,
    BigDecimal distanceKm,
    BigDecimal subtotal,
    BigDecimal deliveryFee,
    BigDecimal total,
    PaymentMethod paymentMethod,
    PaymentStatus paymentStatus,
    OrderStatus status,
    String note,
    Instant createdAt,
    List<OrderItemResponse> items
) {
}
