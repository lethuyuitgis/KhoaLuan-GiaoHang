package com.shop.delivery.delivery.api.shipper.dto;

import com.shop.delivery.delivery.domain.AssignmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
    // Shipper cần biết lấy món gì và có phải thu tiền không —
    // COD chưa SUCCESS nghĩa là thu `total` của khách khi giao.
    String paymentMethod,
    String paymentStatus,
    String note,
    List<AssignmentItem> items,
    BigDecimal shipperCommission,
    AssignmentStatus status,
    String orderStatus,
    Instant assignedAt,
    Instant acceptedAt,
    Instant startedAt,
    Instant deliveredAt
) {
    public record AssignmentItem(String productName, Integer quantity) {}
}
