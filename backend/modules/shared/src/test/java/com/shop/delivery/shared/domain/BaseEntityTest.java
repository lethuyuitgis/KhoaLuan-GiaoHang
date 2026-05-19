package com.shop.delivery.shared.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    static class TestEntity extends BaseEntity {
    }

    @Test
    void shouldExposeCreatedAtAndUpdatedAtFields() {
        TestEntity e = new TestEntity();
        Instant now = Instant.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);

        assertThat(e.getCreatedAt()).isEqualTo(now);
        assertThat(e.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void twoEntitiesWithNullIdsShouldNotBeEqual() {
        TestEntity a = new TestEntity();
        TestEntity b = new TestEntity();
        assertThat(a).isNotEqualTo(b);
    }
}
