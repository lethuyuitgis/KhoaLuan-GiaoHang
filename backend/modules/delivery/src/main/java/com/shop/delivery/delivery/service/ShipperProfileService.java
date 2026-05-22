package com.shop.delivery.delivery.service;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.auth.repository.UserRoleRepository;
import com.shop.delivery.auth.service.RoleResolver;
import com.shop.delivery.delivery.domain.ShipperState;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.repository.ShipperProfileRepository;
import com.shop.delivery.delivery.service.command.CreateShipperCommand;
import com.shop.delivery.shared.exception.NotFoundException;
import com.shop.delivery.shared.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ShipperProfileService {

    private final TelegramUserRepository userRepo;
    private final UserRoleRepository roleRepo;
    private final ShipperProfileRepository profileRepo;
    private final RoleResolver roleResolver;

    public ShipperProfileService(TelegramUserRepository userRepo,
                                 UserRoleRepository roleRepo,
                                 ShipperProfileRepository profileRepo,
                                 RoleResolver roleResolver) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.profileRepo = profileRepo;
        this.roleResolver = roleResolver;
    }

    @Transactional
    public ShipperProfile createShipper(CreateShipperCommand cmd) {
        userRepo.findById(cmd.telegramUserId())
            .orElseThrow(() -> new NotFoundException(
                "USER_NOT_FOUND",
                "Telegram user " + cmd.telegramUserId() + " chưa tồn tại — họ phải /start bot trước"));

        if (!roleRepo.existsByTelegramUserIdAndRoleAndStatus(cmd.telegramUserId(), Role.SHIPPER, UserRoleStatus.ACTIVE)) {
            UserRole role = new UserRole();
            role.setTelegramUserId(cmd.telegramUserId());
            role.setRole(Role.SHIPPER);
            role.setStatus(UserRoleStatus.ACTIVE);
            role.setAssignedAt(Instant.now());
            roleRepo.save(role);
            roleResolver.evict(cmd.telegramUserId());
        }

        if (profileRepo.findById(cmd.telegramUserId()).isPresent()) {
            throw new ValidationException("SHIPPER_EXISTS", "Shipper đã tồn tại");
        }

        ShipperProfile profile = new ShipperProfile();
        profile.setUserId(cmd.telegramUserId());
        profile.setVehicleType(cmd.vehicleType());
        profile.setLicensePlate(cmd.licensePlate());
        profile.setCurrentState(ShipperState.OFFLINE);
        return profileRepo.save(profile);
    }

    @Transactional(readOnly = true)
    public ShipperProfile findById(Long userId) {
        return profileRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("SHIPPER_NOT_FOUND", "Shipper " + userId + " không tồn tại"));
    }

    @Transactional(readOnly = true)
    public List<ShipperProfile> listAll() {
        return profileRepo.findAll();
    }

    @Transactional(readOnly = true)
    public List<ShipperProfile> listAvailable() {
        return profileRepo.findAllByCurrentState(ShipperState.AVAILABLE);
    }

    @Transactional
    public ShipperProfile setState(Long userId, ShipperState state) {
        ShipperProfile p = findById(userId);
        p.setCurrentState(state);
        return profileRepo.save(p);
    }
}
