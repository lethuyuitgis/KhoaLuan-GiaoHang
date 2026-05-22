package com.shop.delivery.delivery.api.admin;

import com.shop.delivery.auth.api.admin.AdminPrincipal;
import com.shop.delivery.delivery.api.admin.dto.AssignShipperRequest;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.service.DeliveryAssignmentService;
import com.shop.delivery.delivery.service.command.AssignShipperCommand;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminAssignController {

    private final DeliveryAssignmentService service;

    public AdminAssignController(DeliveryAssignmentService service) {
        this.service = service;
    }

    public record AssignmentResponse(
        UUID id,
        UUID orderId,
        Long shipperId,
        String status
    ) {}

    @PostMapping("/{orderId}/assign")
    public AssignmentResponse assign(@AuthenticationPrincipal AdminPrincipal admin,
                                     @PathVariable UUID orderId,
                                     @Valid @RequestBody AssignShipperRequest req) {
        DeliveryAssignment a = service.assign(new AssignShipperCommand(orderId, req.shipperId(), admin.adminUserId()));
        return new AssignmentResponse(a.getId(), a.getOrderId(), a.getShipperId(), a.getStatus().name());
    }
}
