package com.shop.delivery.order.api;

import com.shop.delivery.order.api.dto.CancelOrderRequest;
import com.shop.delivery.order.api.dto.ConfirmOrderRequest;
import com.shop.delivery.order.api.dto.OrderResponse;
import com.shop.delivery.order.api.dto.OrderSummary;
import com.shop.delivery.order.api.mapper.OrderMapper;
import com.shop.delivery.order.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin-facing order management.
 * TODO P4: Add @PreAuthorize("hasRole('SHOP_OWNER')") + extract admin actorId from JWT.
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    /** Placeholder admin actor id used in StatusHistory until JWT auth in P4. */
    private static final Long ADMIN_ACTOR_ID = 0L;

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

    @PostMapping("/{id}/confirm")
    public OrderResponse confirm(@PathVariable UUID id, @RequestBody(required = false) ConfirmOrderRequest req) {
        String note = req != null ? req.note() : null;
        return mapper.toResponse(service.confirm(id, ADMIN_ACTOR_ID, note));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id, @RequestBody(required = false) CancelOrderRequest req) {
        String reason = req != null ? req.reason() : null;
        return mapper.toResponse(service.cancel(id, ADMIN_ACTOR_ID, reason));
    }
}
