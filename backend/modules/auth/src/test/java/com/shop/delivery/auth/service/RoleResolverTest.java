package com.shop.delivery.auth.service;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleResolverTest {

    @Mock UserRoleRepository roleRepo;

    RoleResolver resolver;

    @BeforeEach
    void setup() {
        // Use uncached resolver (no Spring cache infrastructure in unit test).
        resolver = new RoleResolver(roleRepo);
    }

    @Test
    void shouldReturnEmptySetForUnknownUser() {
        when(roleRepo.findAllByTelegramUserIdAndStatus(999L, UserRoleStatus.ACTIVE))
            .thenReturn(List.of());

        Set<Role> roles = resolver.activeRolesOf(999L);

        assertThat(roles).isEmpty();
    }

    @Test
    void shouldReturnActiveRolesOnly() {
        UserRole r1 = makeRole(123L, Role.CUSTOMER, UserRoleStatus.ACTIVE);
        UserRole r2 = makeRole(123L, Role.SHIPPER, UserRoleStatus.ACTIVE);
        when(roleRepo.findAllByTelegramUserIdAndStatus(123L, UserRoleStatus.ACTIVE))
            .thenReturn(List.of(r1, r2));

        Set<Role> roles = resolver.activeRolesOf(123L);

        assertThat(roles).containsExactlyInAnyOrder(Role.CUSTOMER, Role.SHIPPER);
    }

    @Test
    void hasRoleShouldReturnTrueForAssignedActiveRole() {
        when(roleRepo.findAllByTelegramUserIdAndStatus(123L, UserRoleStatus.ACTIVE))
            .thenReturn(List.of(makeRole(123L, Role.CUSTOMER, UserRoleStatus.ACTIVE)));

        assertThat(resolver.hasRole(123L, Role.CUSTOMER)).isTrue();
        assertThat(resolver.hasRole(123L, Role.SHIPPER)).isFalse();
    }

    private UserRole makeRole(Long userId, Role role, UserRoleStatus status) {
        UserRole r = new UserRole();
        r.setTelegramUserId(userId);
        r.setRole(role);
        r.setStatus(status);
        return r;
    }
}
