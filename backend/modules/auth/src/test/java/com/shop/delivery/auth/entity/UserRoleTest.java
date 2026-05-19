package com.shop.delivery.auth.entity;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserRoleTest {

    @Test
    void shouldExposeFieldsAndDefaults() {
        UserRole ur = new UserRole();
        ur.setTelegramUserId(123L);
        ur.setRole(Role.CUSTOMER);
        ur.setStatus(UserRoleStatus.ACTIVE);
        Instant now = Instant.now();
        ur.setAssignedAt(now);

        assertThat(ur.getTelegramUserId()).isEqualTo(123L);
        assertThat(ur.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(ur.getStatus()).isEqualTo(UserRoleStatus.ACTIVE);
        assertThat(ur.getAssignedAt()).isEqualTo(now);
    }

    @Test
    void newInstanceShouldHaveActiveStatusByDefault() {
        UserRole ur = new UserRole();
        assertThat(ur.getStatus()).isEqualTo(UserRoleStatus.ACTIVE);
    }
}
