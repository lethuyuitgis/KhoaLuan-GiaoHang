package com.shop.delivery.delivery.api.admin;

import com.shop.delivery.auth.api.admin.AdminPrincipal;
import com.shop.delivery.delivery.api.admin.dto.AssignShipperRequest;
import com.shop.delivery.delivery.api.admin.dto.ShipperCandidateResponse;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.service.DeliveryAssignmentService;
import com.shop.delivery.delivery.service.ShipperCandidateService;
import com.shop.delivery.delivery.service.command.AssignShipperCommand;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminAssignController {

    private final DeliveryAssignmentService service;
    private final ShipperCandidateService candidateService;

    public AdminAssignController(DeliveryAssignmentService service,
                                 ShipperCandidateService candidateService) {
        this.service = service;
        this.candidateService = candidateService;
    }

    public record AssignmentResponse(
        UUID id,
        UUID orderId,
        Long shipperId,
        String status
    ) {}

    /** AVAILABLE shippers ranked for this order (nearest to pickup first). */
    @GetMapping("/{orderId}/candidate-shippers")
    public List<ShipperCandidateResponse> candidateShippers(@PathVariable UUID orderId) {
        return candidateService.listCandidates(orderId);
    }

    @PostMapping("/{orderId}/assign")
    public AssignmentResponse assign(@AuthenticationPrincipal AdminPrincipal admin,
                                     @PathVariable UUID orderId,
                                     @Valid @RequestBody AssignShipperRequest req) {
        DeliveryAssignment a = service.assign(new AssignShipperCommand(orderId, req.shipperId(), admin.adminUserId()));
        return new AssignmentResponse(a.getId(), a.getOrderId(), a.getShipperId(), a.getStatus().name());
    }
}
