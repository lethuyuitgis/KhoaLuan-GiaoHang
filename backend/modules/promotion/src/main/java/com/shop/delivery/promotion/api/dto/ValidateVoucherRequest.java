package com.shop.delivery.promotion.api.dto;

import com.shop.delivery.promotion.domain.VoucherTarget;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ValidateVoucherRequest(
    @NotBlank String code,
    @NotNull VoucherTarget target,
    @NotNull @PositiveOrZero BigDecimal subtotal,
    @NotNull @PositiveOrZero BigDecimal deliveryFee
) {}
