package com.shop.delivery.delivery.api.admin;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.auth.repository.UserRoleRepository;
import com.shop.delivery.delivery.api.admin.dto.CreateShipperRequest;
import com.shop.delivery.delivery.api.admin.dto.ShipperResponse;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.service.ShipperProfileService;
import com.shop.delivery.delivery.service.command.CreateShipperCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/shippers")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminShipperController {

    private final ShipperProfileService service;
    private final TelegramUserRepository userRepo;
    private final UserRoleRepository roleRepo;

    public AdminShipperController(ShipperProfileService service,
                                  TelegramUserRepository userRepo,
                                  UserRoleRepository roleRepo) {
        this.service = service;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
    }

    @GetMapping
    public List<ShipperResponse> list() {
        return service.listAll().stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShipperResponse create(@Valid @RequestBody CreateShipperRequest req) {
        ShipperProfile p = service.createShipper(new CreateShipperCommand(
            req.telegramUserId(), req.vehicleType(), req.licensePlate()
        ));
        return toResponse(p);
    }

    /**
     * Duyệt shipper PENDING → ACTIVE. Idempotent: gọi lại trên shipper đã ACTIVE
     * vẫn trả 200 với profile hiện tại (không lỗi).
     */
    @PostMapping("/{id}/approve")
    public ShipperResponse approve(@PathVariable Long id) {
        return toResponse(service.approve(id));
    }

    /**
     * Từ chối shipper PENDING → xóa hẳn role + hồ sơ, cho phép user /start đăng ký lại.
     * Trả 204. Shipper đã ACTIVE bị từ chối (SHIPPER_ACTIVE) → 400 do guard ở service.
     */
    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable Long id) {
        service.reject(id);
    }

    private ShipperResponse toResponse(ShipperProfile p) {
        TelegramUser u = userRepo.findById(p.getUserId()).orElse(null);
        UserRoleStatus approvalStatus = roleRepo
            .findByTelegramUserIdAndRole(p.getUserId(), Role.SHIPPER)
            .map(r -> r.getStatus())
            .orElse(null);
        return new ShipperResponse(
            p.getUserId(),
            u != null ? u.getFirstName() : null,
            u != null ? u.getLastName() : null,
            u != null ? u.getUsername() : null,
            p.getVehicleType(),
            p.getLicensePlate(),
            p.getCurrentState(),
            approvalStatus,
            p.getRatingAvg(),
            p.getRatingCount(),
            p.getTotalDeliveries()
        );
    }
}
