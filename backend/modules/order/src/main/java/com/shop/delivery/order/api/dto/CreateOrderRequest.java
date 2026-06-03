package com.shop.delivery.order.api.dto;

import com.shop.delivery.order.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request payload for creating an order. Geographic bounds are deliberately
 * generous (entire Vietnam landmass) — the miniapp UI restricts further to
 * Hà Nội only, but we keep the API permissive in case of future expansion.
 */
public record CreateOrderRequest(
    String customerName,
    String customerPhone,
    @NotBlank
    @Size(min = 10, max = 500, message = "Địa chỉ giao phải từ 10 đến 500 ký tự")
    String deliveryAddress,
    @NotNull
    @DecimalMin(value = "8.0", message = "deliveryLat phải nằm trong lãnh thổ Việt Nam (>= 8.0)")
    @DecimalMax(value = "24.0", message = "deliveryLat phải nằm trong lãnh thổ Việt Nam (<= 24.0)")
    BigDecimal deliveryLat,
    @NotNull
    @DecimalMin(value = "102.0", message = "deliveryLng phải nằm trong lãnh thổ Việt Nam (>= 102.0)")
    @DecimalMax(value = "110.0", message = "deliveryLng phải nằm trong lãnh thổ Việt Nam (<= 110.0)")
    BigDecimal deliveryLng,
    @NotEmpty @Valid List<OrderItemRequest> items,
    @NotNull PaymentMethod paymentMethod,
    String note
) {
}
