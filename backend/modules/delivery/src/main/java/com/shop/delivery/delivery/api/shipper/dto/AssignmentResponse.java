package com.shop.delivery.delivery.api.shipper.dto;

import com.shop.delivery.delivery.domain.AssignmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(
    UUID id,
    UUID orderId,
    String orderCode,
    Long customerId,
    String customerName,
    String customerPhone,
    String deliveryAddress,
    BigDecimal deliveryLat,
    BigDecimal deliveryLng,
    BigDecimal distanceKm,
    BigDecimal deliveryFee,
    BigDecimal total,
    AssignmentStatus status,
    String orderStatus,
    Instant assignedAt,
    Instant acceptedAt,
    Instant startedAt,
    Instant deliveredAt
) {}
