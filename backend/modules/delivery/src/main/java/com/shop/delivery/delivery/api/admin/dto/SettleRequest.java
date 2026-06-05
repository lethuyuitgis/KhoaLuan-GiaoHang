package com.shop.delivery.delivery.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SettleRequest(
    @NotBlank @Pattern(regexp = "PAYOUT|DEPOSIT") String type,
    @NotNull @Positive BigDecimal amount,
    @Size(max = 256) String note
) {}
