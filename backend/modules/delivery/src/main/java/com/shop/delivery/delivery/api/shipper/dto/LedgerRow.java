package com.shop.delivery.delivery.api.shipper.dto;

import com.shop.delivery.delivery.domain.LedgerEntryType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LedgerRow(
    Long id,
    LedgerEntryType entryType,
    BigDecimal amount,
    UUID orderId,
    String note,
    OffsetDateTime createdAt
) {}
