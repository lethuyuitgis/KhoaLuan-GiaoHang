package com.shop.delivery.delivery.api.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One bucket of revenue / order-count data. Used by both the dashboard
 * 7-day mini-series and the reports endpoint with date / week buckets.
 */
public record RevenuePoint(
    LocalDate date,
    BigDecimal revenue,
    long orderCount
) {}
