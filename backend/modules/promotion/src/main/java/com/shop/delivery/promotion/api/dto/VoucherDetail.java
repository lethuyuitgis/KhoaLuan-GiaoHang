package com.shop.delivery.promotion.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record VoucherDetail(VoucherSummary summary, List<RedemptionRow> recentRedemptions) {
    public record RedemptionRow(UUID orderId, Long customerId,
                                BigDecimal discountApplied, OffsetDateTime createdAt) {}
}
