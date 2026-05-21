package com.shop.delivery.auth.api;

import com.shop.delivery.auth.api.dto.MeResponse;
import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.service.RoleResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeControllerTest {

    @Mock RoleResolver roleResolver;

    @InjectMocks MeController controller;

    @Test
    void shouldReturnUserInfoAndRoles() {
        TelegramUser user = new TelegramUser();
        user.setId(12345L);
        user.setFirstName("Thúy");
        user.setLastName("Lê");
        user.setUsername("thuyltt");
        user.setLanguageCode("vi");

        when(roleResolver.activeRolesOf(12345L)).thenReturn(Set.of(Role.CUSTOMER));

        MeResponse resp = controller.me(user);

        assertThat(resp.id()).isEqualTo(12345L);
        assertThat(resp.firstName()).isEqualTo("Thúy");
        assertThat(resp.lastName()).isEqualTo("Lê");
        assertThat(resp.username()).isEqualTo("thuyltt");
        assertThat(resp.languageCode()).isEqualTo("vi");
        assertThat(resp.roles()).containsExactly(Role.CUSTOMER);
    }
}
