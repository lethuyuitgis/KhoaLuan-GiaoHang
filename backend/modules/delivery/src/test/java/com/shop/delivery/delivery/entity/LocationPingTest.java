package com.shop.delivery.delivery.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocationPingTest {

    @Test
    void shouldExposeFields() {
        LocationPing p = new LocationPing();
        UUID assignmentId = UUID.randomUUID();
        p.setAssignmentId(assignmentId);
        p.setLat(new BigDecimal("21.0193"));
        p.setLng(new BigDecimal("105.8503"));
        p.setAccuracy(new BigDecimal("12.50"));
        p.setHeading(new BigDecimal("180.5"));
        Instant now = Instant.now();
        p.setRecordedAt(now);

        assertThat(p.getAssignmentId()).isEqualTo(assignmentId);
        assertThat(p.getLat()).isEqualByComparingTo("21.0193");
        assertThat(p.getLng()).isEqualByComparingTo("105.8503");
        assertThat(p.getAccuracy()).isEqualByComparingTo("12.50");
        assertThat(p.getHeading()).isEqualByComparingTo("180.5");
        assertThat(p.getRecordedAt()).isEqualTo(now);
    }
}
