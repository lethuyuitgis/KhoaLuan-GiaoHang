package com.shop.delivery.promotion.api.dto;

import com.shop.delivery.promotion.domain.VoucherTarget;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ValidateVoucherRequest(
    @NotBlank String code,
    @NotNull VoucherTarget target,
    @NotNull @Positive BigDecimal subtotal,
    @NotNull @Positive BigDecimal deliveryFee
) {}
