package com.shop.delivery.auth.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    @Test
    void shouldExposeFieldsAndDefaults() {
        RefreshToken t = new RefreshToken();
        UUID id = UUID.randomUUID();
        Instant exp = Instant.now().plusSeconds(7 * 24 * 3600);
        t.setId(id);
        t.setAdminUserId(1L);
        t.setTokenHash("$2a$10$hashed");
        t.setExpiresAt(exp);

        assertThat(t.getId()).isEqualTo(id);
        assertThat(t.getAdminUserId()).isEqualTo(1L);
        assertThat(t.getTokenHash()).isEqualTo("$2a$10$hashed");
        assertThat(t.getExpiresAt()).isEqualTo(exp);
        assertThat(t.isRevoked()).isFalse();
    }

    @Test
    void revokeSetsRevokedTrue() {
        RefreshToken t = new RefreshToken();
        t.setRevoked(true);
        assertThat(t.isRevoked()).isTrue();
    }
}
