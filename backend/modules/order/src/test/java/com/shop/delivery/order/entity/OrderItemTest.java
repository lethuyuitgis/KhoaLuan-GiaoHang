package com.shop.delivery.order.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemTest {

    @Test
    void shouldComputeSubtotalFromQuantityAndUnitPrice() {
        OrderItem item = new OrderItem();
        UUID orderId = UUID.randomUUID();
        item.setOrderId(orderId);
        item.setProductId(7L);
        item.setQuantity(3);
        item.setUnitPrice(new BigDecimal("100000"));
        item.setSubtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));

        assertThat(item.getSubtotal()).isEqualByComparingTo("300000");
    }
}
