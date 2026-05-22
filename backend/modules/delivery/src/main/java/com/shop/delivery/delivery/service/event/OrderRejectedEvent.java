package com.shop.delivery.delivery.service.event;

import java.util.UUID;

public record OrderRejectedEvent(
    UUID orderId,
    String orderCode,
    Long shipperId
) {}
