package com.shop.delivery.auth.entity;

import com.shop.delivery.shared.domain.BaseEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUserTest {

    @Test
    void shouldExtendBaseEntity() {
        assertThat(BaseEntity.class.isAssignableFrom(AdminUser.class)).isTrue();
    }

    @Test
    void shouldExposeFields() {
        AdminUser u = new AdminUser();
        u.setId(1L);
        u.setEmail("owner@shop.local");
        u.setPasswordHash("$2a$10$abc");
        u.setFullName("Shop Owner");
        u.setActive(true);
        u.setTelegramUserId(999L);

        assertThat(u.getId()).isEqualTo(1L);
        assertThat(u.getEmail()).isEqualTo("owner@shop.local");
        assertThat(u.getPasswordHash()).startsWith("$2a$");
        assertThat(u.getFullName()).isEqualTo("Shop Owner");
        assertThat(u.isActive()).isTrue();
        assertThat(u.getTelegramUserId()).isEqualTo(999L);
    }
}
