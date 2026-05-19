package com.shop.delivery.auth.repository;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findAllByTelegramUserId(Long telegramUserId);

    List<UserRole> findAllByTelegramUserIdAndStatus(Long telegramUserId, UserRoleStatus status);

    Optional<UserRole> findByTelegramUserIdAndRole(Long telegramUserId, Role role);

    boolean existsByTelegramUserIdAndRoleAndStatus(Long telegramUserId, Role role, UserRoleStatus status);
}
