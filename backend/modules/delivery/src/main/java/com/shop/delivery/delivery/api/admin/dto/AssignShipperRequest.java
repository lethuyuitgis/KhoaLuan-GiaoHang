package com.shop.delivery.delivery.api.admin.dto;

import jakarta.validation.constraints.NotNull;

public record AssignShipperRequest(
    @NotNull Long shipperId
) {}
