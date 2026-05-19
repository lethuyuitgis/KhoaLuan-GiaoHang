package com.shop.delivery.auth.entity;

import com.shop.delivery.shared.domain.BaseEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramUserTest {

    @Test
    void shouldUseTelegramIdAsPrimaryKey() {
        TelegramUser u = new TelegramUser();
        u.setId(987654321L);
        assertThat(u.getId()).isEqualTo(987654321L);
    }

    @Test
    void shouldExposeStandardFields() {
        TelegramUser u = new TelegramUser();
        u.setUsername("nguyen_a");
        u.setFirstName("Nguyễn");
        u.setLastName("A");
        u.setPhone("+84901234567");
        u.setPhotoUrl("https://t.me/photo.jpg");
        u.setLanguageCode("vi");

        assertThat(u.getUsername()).isEqualTo("nguyen_a");
        assertThat(u.getFirstName()).isEqualTo("Nguyễn");
        assertThat(u.getLastName()).isEqualTo("A");
        assertThat(u.getPhone()).isEqualTo("+84901234567");
        assertThat(u.getPhotoUrl()).isEqualTo("https://t.me/photo.jpg");
        assertThat(u.getLanguageCode()).isEqualTo("vi");
        assertThat(u.isBlocked()).isFalse();
    }

    @Test
    void shouldExtendBaseEntity() {
        assertThat(BaseEntity.class.isAssignableFrom(TelegramUser.class)).isTrue();
    }
}
