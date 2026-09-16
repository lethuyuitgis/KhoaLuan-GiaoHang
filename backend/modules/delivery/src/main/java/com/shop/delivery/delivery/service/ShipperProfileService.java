package com.shop.delivery.delivery.service;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.auth.repository.UserRoleRepository;
import com.shop.delivery.auth.service.RoleResolver;
import com.shop.delivery.delivery.domain.ShipperState;
import com.shop.delivery.delivery.domain.VehicleType;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.repository.ShipperProfileRepository;
import com.shop.delivery.delivery.service.command.CreateShipperCommand;
import com.shop.delivery.shared.event.ShipperApprovedEvent;
import com.shop.delivery.shared.event.ShipperRegisteredEvent;
import com.shop.delivery.shared.event.ShipperRejectedEvent;
import com.shop.delivery.shared.exception.NotFoundException;
import com.shop.delivery.shared.exception.ValidationException;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;

    public ShipperProfileService(TelegramUserRepository userRepo,
                                 UserRoleRepository roleRepo,
                                 ShipperProfileRepository profileRepo,
                                 RoleResolver roleResolver,
                                 ApplicationEventPublisher events) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.profileRepo = profileRepo;
        this.roleResolver = roleResolver;
        this.events = events;
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

    /**
     * Shipper tự bật/tắt nhận đơn: online → AVAILABLE, offline → OFFLINE.
     * Chặn khi đang BUSY (đang giao) để không bỏ dở đơn.
     */
    @Transactional
    public ShipperProfile setAvailability(Long userId, boolean online) {
        ShipperProfile p = findById(userId);
        if (p.getCurrentState() == ShipperState.BUSY) {
            throw new ValidationException("SHIPPER_BUSY",
                "Bạn đang giao đơn — hoàn tất đơn hiện tại trước khi đổi trạng thái");
        }
        p.setCurrentState(online ? ShipperState.AVAILABLE : ShipperState.OFFLINE);
        return profileRepo.save(p);
    }

    /**
     * Bot-driven shipper registration: the user finished the FSM in Telegram,
     * so we insert {@code user_role(SHIPPER, PENDING)} + a {@code shipper_profile}
     * row, then publish {@link ShipperRegisteredEvent} for the notification
     * module to alert admins.
     *
     * <p>Idempotent: if the user already has any {@code user_role(SHIPPER)} (in
     * any status), this is a no-op returning the existing profile if present.
     * The {@code DUPLICATE} sentinel return value lets the caller distinguish
     * the duplicate path so it can tell the user "you're already registered".
     */
    @Transactional
    public ShipperProfile registerPending(Long telegramUserId,
                                          VehicleType vehicleType,
                                          String licensePlate,
                                          String fullName) {
        userRepo.findById(telegramUserId)
            .orElseThrow(() -> new NotFoundException(
                "USER_NOT_FOUND",
                "Telegram user " + telegramUserId + " chưa tồn tại"));

        boolean hasAnyShipperRole = roleRepo.findByTelegramUserIdAndRole(
            telegramUserId, Role.SHIPPER).isPresent();
        if (hasAnyShipperRole) {
            throw new ValidationException("SHIPPER_EXISTS",
                "Bạn đã đăng ký rồi, đang chờ duyệt");
        }

        UserRole role = new UserRole();
        role.setTelegramUserId(telegramUserId);
        role.setRole(Role.SHIPPER);
        role.setStatus(UserRoleStatus.PENDING);
        role.setAssignedAt(Instant.now());
        roleRepo.save(role);

        ShipperProfile profile = profileRepo.findById(telegramUserId).orElseGet(ShipperProfile::new);
        profile.setUserId(telegramUserId);
        profile.setVehicleType(vehicleType);
        profile.setLicensePlate(licensePlate);
        profile.setCurrentState(ShipperState.OFFLINE);
        ShipperProfile saved = profileRepo.save(profile);

        events.publishEvent(new ShipperRegisteredEvent(
            telegramUserId, fullName, vehicleType.name(), licensePlate));

        return saved;
    }

    /**
     * Admin approves a pending shipper: flip the role to ACTIVE, evict the
     * RoleResolver cache so future requests see the new role, and publish
     * {@link ShipperApprovedEvent} so the bot can DM the shipper.
     *
     * @throws NotFoundException if no PENDING SHIPPER role exists for this user.
     */
    @Transactional
    public ShipperProfile approve(Long telegramUserId) {
        UserRole role = roleRepo.findByTelegramUserIdAndRole(telegramUserId, Role.SHIPPER)
            .orElseThrow(() -> new NotFoundException(
                "SHIPPER_NOT_FOUND",
                "Không tìm thấy đơn đăng ký shipper cho user " + telegramUserId));

        if (role.getStatus() == UserRoleStatus.ACTIVE) {
            // Idempotent: already approved.
            return findById(telegramUserId);
        }
        role.setStatus(UserRoleStatus.ACTIVE);
        roleRepo.save(role);
        roleResolver.evict(telegramUserId);

        events.publishEvent(new ShipperApprovedEvent(telegramUserId));

        return findById(telegramUserId);
    }

    /**
     * Admin rejects a pending shipper registration: delete the
     * {@code user_role(SHIPPER)} + {@code shipper_profile} so the user can
     * {@code /start} and register again from scratch, evict the RoleResolver
     * cache, and publish {@link ShipperRejectedEvent} so the bot can DM them.
     *
     * <p>Only a {@code PENDING} registration may be rejected — any other status
     * (ACTIVE, or a future BLOCKED) is refused with {@code SHIPPER_NOT_PENDING}.
     * This guard protects a working shipper (who may hold
     * {@code delivery_assignment} / {@code shipper_ledger} rows) from being
     * deleted through the reject button.
     *
     * @throws NotFoundException   if no SHIPPER role exists for this user.
     * @throws ValidationException if the SHIPPER role is not PENDING.
     */
    @Transactional
    public void reject(Long telegramUserId) {
        UserRole role = roleRepo.findByTelegramUserIdAndRole(telegramUserId, Role.SHIPPER)
            .orElseThrow(() -> new NotFoundException(
                "SHIPPER_NOT_FOUND",
                "Không tìm thấy đơn đăng ký shipper cho user " + telegramUserId));

        if (role.getStatus() != UserRoleStatus.PENDING) {
            throw new ValidationException("SHIPPER_NOT_PENDING",
                "Chỉ có thể từ chối đơn shipper đang chờ duyệt");
        }

        profileRepo.findById(telegramUserId).ifPresent(profileRepo::delete);
        roleRepo.delete(role);
        roleResolver.evict(telegramUserId);

        events.publishEvent(new ShipperRejectedEvent(telegramUserId));
    }
}
