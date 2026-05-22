package com.shop.delivery.delivery.api.admin.dto;

import com.shop.delivery.delivery.domain.ShipperState;
import com.shop.delivery.delivery.domain.VehicleType;

import java.math.BigDecimal;

public record ShipperResponse(
    Long userId,
    String firstName,
    String lastName,
    String username,
    VehicleType vehicleType,
    String licensePlate,
    ShipperState currentState,
    BigDecimal ratingAvg,
    Integer ratingCount,
    Integer totalDeliveries
) {}
