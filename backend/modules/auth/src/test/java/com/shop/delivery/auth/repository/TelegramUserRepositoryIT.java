package com.shop.delivery.auth.repository;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.support.AuthTestcontainerBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class TelegramUserRepositoryIT extends AuthTestcontainerBase {

    @Autowired
    TelegramUserRepository userRepo;

    @Autowired
    UserRoleRepository roleRepo;

    @Test
    void shouldPersistAndRetrieveTelegramUser() {
        TelegramUser u = new TelegramUser();
        u.setId(123456L);
        u.setUsername("test_user");
        u.setFirstName("Test");
        u.setLanguageCode("vi");
        userRepo.save(u);

        Optional<TelegramUser> found = userRepo.findById(123456L);
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("test_user");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindRolesByUserId() {
        TelegramUser u = new TelegramUser();
        u.setId(999L);
        u.setFirstName("Multi");
        userRepo.save(u);

        UserRole r1 = new UserRole();
        r1.setTelegramUserId(999L);
        r1.setRole(Role.CUSTOMER);
        r1.setStatus(UserRoleStatus.ACTIVE);
        roleRepo.save(r1);

        UserRole r2 = new UserRole();
        r2.setTelegramUserId(999L);
        r2.setRole(Role.SHIPPER);
        r2.setStatus(UserRoleStatus.PENDING);
        roleRepo.save(r2);

        var roles = roleRepo.findAllByTelegramUserIdAndStatus(999L, UserRoleStatus.ACTIVE);
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).getRole()).isEqualTo(Role.CUSTOMER);
    }
}
