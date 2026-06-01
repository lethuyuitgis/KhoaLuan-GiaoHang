package com.shop.delivery.delivery.api.admin.dto;

import java.math.BigDecimal;

public record TopShipperRow(
    Long shipperId,
    String name,
    long deliveredCount,
    BigDecimal revenueGenerated,
    BigDecimal ratingAvg
) {}
