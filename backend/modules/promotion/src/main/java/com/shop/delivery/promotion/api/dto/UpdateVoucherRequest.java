package com.shop.delivery.promotion.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record UpdateVoucherRequest(
    @NotBlank String name,
    @NotNull BigDecimal discountValue,
    BigDecimal maxDiscount,
    BigDecimal minOrderAmount,
    @NotNull OffsetDateTime validFrom,
    @NotNull OffsetDateTime validUntil,
    Integer maxUsesTotal,
    int maxUsesPerCustomer,
    boolean active
) {}
