package com.shop.delivery.delivery.service.event;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderAssignedEvent(
    UUID assignmentId,
    UUID orderId,
    String orderCode,
    Long shipperId,
    String deliveryAddress,
    BigDecimal distanceKm,
    BigDecimal deliveryFee
) {}
