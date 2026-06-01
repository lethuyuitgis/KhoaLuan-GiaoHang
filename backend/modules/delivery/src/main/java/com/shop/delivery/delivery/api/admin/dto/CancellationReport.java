package com.shop.delivery.delivery.api.admin.dto;

import java.util.List;

public record CancellationReport(
    long totalOrders,
    long cancelledCount,
    double cancelRate,
    List<ReasonCount> byReason
) {
    public record ReasonCount(String reason, long count) {}
}
