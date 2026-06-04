package com.shop.delivery.delivery.api.shipper.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ShipperSelfProfile(
    String name,
    String phone,
    BigDecimal ratingAvg,
    int totalOrders,
    Instant joinedAt
) {}
