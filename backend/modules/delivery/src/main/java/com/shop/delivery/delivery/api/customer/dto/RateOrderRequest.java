package com.shop.delivery.delivery.api.customer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RateOrderRequest(
    @NotNull @Min(1) @Max(5) Integer stars,
    @Size(max = 1000) String comment
) {}
