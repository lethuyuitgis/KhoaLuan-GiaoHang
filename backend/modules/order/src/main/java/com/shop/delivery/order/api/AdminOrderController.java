package com.shop.delivery.order.api;

import com.shop.delivery.auth.api.admin.AdminPrincipal;
import com.shop.delivery.order.api.dto.CancelOrderRequest;
import com.shop.delivery.order.api.dto.ConfirmOrderRequest;
import com.shop.delivery.order.api.dto.OrderResponse;
import com.shop.delivery.order.api.dto.OrderSummary;
import com.shop.delivery.order.api.dto.StatusHistoryResponse;
import com.shop.delivery.order.api.mapper.OrderMapper;
import com.shop.delivery.order.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
public class AdminOrderController {

    private final OrderService service;
    private final OrderMapper mapper;

    public AdminOrderController(OrderService service, OrderMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public Page<OrderSummary> listAll(@PageableDefault(size = 50) Pageable pageable) {
        return service.findAll(pageable).map(mapper::toSummary);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return mapper.toResponse(service.findById(id));
    }

    @GetMapping("/{id}/history")
    public List<StatusHistoryResponse> history(@PathVariable UUID id) {
        return service.listHistory(id).stream().map(mapper::toHistoryResponse).toList();
    }

    @PostMapping("/{id}/confirm")
    public OrderResponse confirm(@AuthenticationPrincipal AdminPrincipal admin,
                                 @PathVariable UUID id,
                                 @RequestBody(required = false) ConfirmOrderRequest req) {
        String note = req != null ? req.note() : null;
        return mapper.toResponse(service.confirm(id, admin.adminUserId(), note));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal AdminPrincipal admin,
                                @PathVariable UUID id,
                                @RequestBody(required = false) CancelOrderRequest req) {
        String reason = req != null ? req.reason() : null;
        return mapper.toResponse(service.cancel(id, admin.adminUserId(), reason));
    }
}
