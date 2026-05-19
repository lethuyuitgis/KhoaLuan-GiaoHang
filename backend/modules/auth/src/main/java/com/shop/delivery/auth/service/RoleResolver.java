package com.shop.delivery.auth.service;

import com.shop.delivery.auth.config.CacheConfig;
import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.UserRoleRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleResolver {

    private final UserRoleRepository roleRepo;

    public RoleResolver(UserRoleRepository roleRepo) {
        this.roleRepo = roleRepo;
    }

    /**
     * Trả về tất cả role ACTIVE của user. Cache 5 phút (xem CacheConfig).
     * Trả về EnumSet rỗng nếu user không tồn tại hoặc không có role active nào.
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_USER_ROLES, key = "#telegramUserId")
    public Set<Role> activeRolesOf(Long telegramUserId) {
        Set<Role> roles = roleRepo.findAllByTelegramUserIdAndStatus(telegramUserId, UserRoleStatus.ACTIVE)
            .stream()
            .map(UserRole::getRole)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
        return roles;
    }

    public boolean hasRole(Long telegramUserId, Role role) {
        return activeRolesOf(telegramUserId).contains(role);
    }

    /** Gọi khi role của user thay đổi (assign/revoke) để invalidate cache. */
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_ROLES, key = "#telegramUserId")
    public void evict(Long telegramUserId) {
        // body trống — annotation làm hết
    }
}
