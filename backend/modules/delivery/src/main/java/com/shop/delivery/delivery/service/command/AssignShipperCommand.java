package com.shop.delivery.delivery.service.command;

import java.util.UUID;

public record AssignShipperCommand(
    UUID orderId,
    Long shipperId,
    Long adminUserId
) {}
