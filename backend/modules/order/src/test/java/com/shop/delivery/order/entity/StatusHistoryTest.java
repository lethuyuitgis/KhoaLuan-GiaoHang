package com.shop.delivery.order.entity;

import com.shop.delivery.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StatusHistoryTest {

    @Test
    void shouldStoreTransition() {
        StatusHistory h = new StatusHistory();
        UUID orderId = UUID.randomUUID();
        h.setOrderId(orderId);
        h.setFromStatus(OrderStatus.PENDING);
        h.setToStatus(OrderStatus.CONFIRMED);
        h.setChangedByUserId(999L);
        h.setChangedAt(Instant.now());
        h.setNote("Admin confirmed");

        assertThat(h.getOrderId()).isEqualTo(orderId);
        assertThat(h.getFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(h.getToStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(h.getChangedByUserId()).isEqualTo(999L);
    }
}
