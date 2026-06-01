package com.shop.delivery.delivery.api.admin.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardSummary(
    OrdersTodaySummary ordersToday,
    BigDecimal revenueToday,
    long activeShippers,
    long newCustomersToday,
    List<RevenuePoint> revenueLast7Days
) {
    public record OrdersTodaySummary(
        long total,
        Map<String, Long> byStatus
    ) {}
}
