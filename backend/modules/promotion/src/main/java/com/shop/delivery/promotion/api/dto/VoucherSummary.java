package com.shop.delivery.promotion.api.dto;

import com.shop.delivery.promotion.domain.DiscountType;
import com.shop.delivery.promotion.domain.VoucherTarget;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record VoucherSummary(
    Long id, String code, String name,
    VoucherTarget target, DiscountType discountType,
    BigDecimal discountValue, BigDecimal maxDiscount,
    BigDecimal minOrderAmount,
    int usedCount, Integer maxUsesTotal,
    int maxUsesPerCustomer,
    OffsetDateTime validFrom, OffsetDateTime validUntil,
    boolean active
) {}
