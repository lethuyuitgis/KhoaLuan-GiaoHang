package com.shop.delivery.delivery.service.command;

import com.shop.delivery.delivery.domain.VehicleType;

public record CreateShipperCommand(
    Long telegramUserId,
    VehicleType vehicleType,
    String licensePlate
) {}
