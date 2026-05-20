package com.shop.delivery.order.api.dto;

import com.shop.delivery.order.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
    String customerName,
    String customerPhone,
    @NotBlank String deliveryAddress,
    @NotNull BigDecimal deliveryLat,
    @NotNull BigDecimal deliveryLng,
    @NotEmpty @Valid List<OrderItemRequest> items,
    @NotNull PaymentMethod paymentMethod,
    String note
) {
}
