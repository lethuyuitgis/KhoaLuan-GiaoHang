package com.shop.delivery.auth.service;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.auth.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramUserServiceTest {

    @Mock TelegramUserRepository userRepo;
    @Mock UserRoleRepository roleRepo;

    @InjectMocks TelegramUserService service;

    TelegramUserUpsertCommand command;

    @BeforeEach
    void setup() {
        command = new TelegramUserUpsertCommand(
            555L,
            "alice",
            "Alice",
            "Nguyen",
            "vi"
        );
    }

    @Test
    void registerNewUserShouldPersistUserAndAssignCustomerRole() {
        when(userRepo.findById(555L)).thenReturn(Optional.empty());
        when(userRepo.save(any(TelegramUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepo.findAllByTelegramUserId(555L)).thenReturn(List.of());

        TelegramUser result = service.registerOrUpdate(command);

        assertThat(result.getId()).isEqualTo(555L);
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getFirstName()).isEqualTo("Alice");

        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(roleRepo).save(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(roleCaptor.getValue().getStatus()).isEqualTo(UserRoleStatus.ACTIVE);
        assertThat(roleCaptor.getValue().getTelegramUserId()).isEqualTo(555L);
    }

    @Test
    void existingUserShouldHaveFieldsUpdated() {
        TelegramUser existing = new TelegramUser();
        existing.setId(555L);
        existing.setFirstName("Old Name");
        existing.setUsername("old_username");

        when(userRepo.findById(555L)).thenReturn(Optional.of(existing));
        when(userRepo.save(any(TelegramUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepo.findAllByTelegramUserId(555L)).thenReturn(
            List.of(makeRole(555L, Role.CUSTOMER, UserRoleStatus.ACTIVE)));

        service.registerOrUpdate(command);

        ArgumentCaptor<TelegramUser> userCaptor = ArgumentCaptor.forClass(TelegramUser.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getFirstName()).isEqualTo("Alice");
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("alice");
    }

    @Test
    void existingUserWithRoleShouldNotGetDuplicateRole() {
        TelegramUser existing = new TelegramUser();
        existing.setId(555L);

        when(userRepo.findById(555L)).thenReturn(Optional.of(existing));
        when(userRepo.save(any(TelegramUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepo.findAllByTelegramUserId(555L)).thenReturn(
            List.of(makeRole(555L, Role.CUSTOMER, UserRoleStatus.ACTIVE)));

        service.registerOrUpdate(command);

        verify(roleRepo, org.mockito.Mockito.never()).save(any(UserRole.class));
    }

    private UserRole makeRole(Long userId, Role role, UserRoleStatus status) {
        UserRole r = new UserRole();
        r.setTelegramUserId(userId);
        r.setRole(role);
        r.setStatus(status);
        return r;
    }
}
