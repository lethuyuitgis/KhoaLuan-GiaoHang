package com.shop.delivery.auth.service;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.auth.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TelegramUserService {

    private final TelegramUserRepository userRepo;
    private final UserRoleRepository roleRepo;

    public TelegramUserService(TelegramUserRepository userRepo, UserRoleRepository roleRepo) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
    }

    /**
     * Đăng ký user mới hoặc cập nhật thông tin user hiện tại từ Telegram.
     * User mới được cấp role CUSTOMER với status ACTIVE.
     * Idempotent: gọi nhiều lần với cùng command không gây side effect ngoài update timestamp.
     */
    @Transactional
    public TelegramUser registerOrUpdate(TelegramUserUpsertCommand cmd) {
        TelegramUser user = userRepo.findById(cmd.id())
            .orElseGet(() -> {
                TelegramUser fresh = new TelegramUser();
                fresh.setId(cmd.id());
                return fresh;
            });

        // Cập nhật field từ Telegram (có thể đã đổi tên, đổi username)
        user.setUsername(cmd.username());
        user.setFirstName(cmd.firstName());
        user.setLastName(cmd.lastName());
        user.setLanguageCode(cmd.languageCode());

        TelegramUser saved = userRepo.save(user);

        // Gán role CUSTOMER nếu chưa có
        boolean hasCustomer = roleRepo.findAllByTelegramUserId(cmd.id()).stream()
            .anyMatch(r -> r.getRole() == Role.CUSTOMER);
        if (!hasCustomer) {
            UserRole role = new UserRole();
            role.setTelegramUserId(cmd.id());
            role.setRole(Role.CUSTOMER);
            role.setStatus(UserRoleStatus.ACTIVE);
            role.setAssignedAt(Instant.now());
            roleRepo.save(role);
        }

        return saved;
    }

    /**
     * Update the {@code phone} field for an existing user. Used by the bot
     * shipper-registration FSM when the user shares a contact via Telegram's
     * {@code request_contact} reply keyboard.
     *
     * @return the updated user, or empty if the user doesn't exist (no autocreate).
     */
    @Transactional
    public java.util.Optional<TelegramUser> updatePhone(Long telegramUserId, String phone) {
        return userRepo.findById(telegramUserId).map(u -> {
            u.setPhone(phone);
            return userRepo.save(u);
        });
    }
}
