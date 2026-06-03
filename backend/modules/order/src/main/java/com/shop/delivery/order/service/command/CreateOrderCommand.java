package com.shop.delivery.order.service.command;

import com.shop.delivery.order.domain.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderCommand(
    Long customerId,
    String customerName,
    String customerPhone,
    String deliveryAddress,
    BigDecimal deliveryLat,
    BigDecimal deliveryLng,
    List<OrderLineCommand> items,
    PaymentMethod paymentMethod,
    String note,
    String voucherProductsCode,
    String voucherShippingCode
) {
}
