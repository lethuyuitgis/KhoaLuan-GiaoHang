package com.shop.delivery.delivery.api.admin.dto;

import com.shop.delivery.delivery.domain.VehicleType;
import jakarta.validation.constraints.NotNull;

public record CreateShipperRequest(
    @NotNull Long telegramUserId,
    @NotNull VehicleType vehicleType,
    String licensePlate
) {}
