package com.shop.delivery.delivery.service.event;

import java.util.UUID;

public record OrderDeliveredEvent(
    UUID assignmentId,
    UUID orderId,
    String orderCode,
    Long shipperId,
    Long customerId
) {}
