package com.shop.delivery.order.service.command;

public record OrderLineCommand(
    Long productId,
    Integer quantity
) {
}
