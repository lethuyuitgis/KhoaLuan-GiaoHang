package com.shop.delivery.promotion.api.dto;

import com.shop.delivery.promotion.domain.DiscountType;
import com.shop.delivery.promotion.domain.VoucherTarget;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateVoucherRequest(
    @NotBlank @Size(max=32) @Pattern(regexp = "^[A-Z0-9_-]+$") String code,
    @NotBlank @Size(max=128) String name,
    @NotNull VoucherTarget target,
    @NotNull DiscountType discountType,
    @NotNull @Positive BigDecimal discountValue,
    @PositiveOrZero BigDecimal maxDiscount,
    @PositiveOrZero BigDecimal minOrderAmount,
    @NotNull OffsetDateTime validFrom,
    @NotNull OffsetDateTime validUntil,
    @PositiveOrZero Integer maxUsesTotal,
    @Positive int maxUsesPerCustomer
) {}
