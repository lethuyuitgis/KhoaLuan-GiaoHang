package com.shop.delivery.delivery.api.shipper.dto;

import java.math.BigDecimal;

public record EarningsSummary(
    BigDecimal today,
    BigDecimal week,
    BigDecimal month,
    BigDecimal balance,
    int todayOrders,
    int weekOrders
) {}
