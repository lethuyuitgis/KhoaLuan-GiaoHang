package com.shop.delivery.delivery.service.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LocationPingReceivedEvent(
    UUID assignmentId,
    UUID orderId,
    Long customerId,
    BigDecimal lat,
    BigDecimal lng,
    BigDecimal accuracy,
    BigDecimal heading,
    Instant recordedAt
) {}
