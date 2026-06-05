package com.shop.delivery.delivery.api.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BalanceResponse(BigDecimal balance, OffsetDateTime lastSettledAt) {}
