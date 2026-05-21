package com.shop.delivery.delivery.entity;

import com.shop.delivery.delivery.domain.AssignmentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAssignmentTest {

    @Test
    void shouldExposeFieldsAndDefaults() {
        DeliveryAssignment a = new DeliveryAssignment();
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        a.setId(id);
        a.setOrderId(orderId);
        a.setShipperId(8888L);
        a.setStatus(AssignmentStatus.OFFERED);
        Instant now = Instant.now();
        a.setAcceptedAt(now);

        assertThat(a.getId()).isEqualTo(id);
        assertThat(a.getOrderId()).isEqualTo(orderId);
        assertThat(a.getShipperId()).isEqualTo(8888L);
        assertThat(a.getStatus()).isEqualTo(AssignmentStatus.OFFERED);
        assertThat(a.getAcceptedAt()).isEqualTo(now);
    }
}
