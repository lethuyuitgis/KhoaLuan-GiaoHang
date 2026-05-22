package com.shop.delivery.delivery.api.customer.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LocationPingResponse(
    BigDecimal lat,
    BigDecimal lng,
    BigDecimal accuracy,
    BigDecimal heading,
    Instant recordedAt
) {}
