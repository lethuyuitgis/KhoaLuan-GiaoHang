package com.shop.delivery.order.api;

import com.shop.delivery.auth.api.CurrentUser;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.order.api.dto.CancelOrderRequest;
import com.shop.delivery.order.api.dto.CreateOrderRequest;
import com.shop.delivery.order.api.dto.OrderResponse;
import com.shop.delivery.order.api.dto.OrderSummary;
import com.shop.delivery.order.api.mapper.OrderMapper;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.service.OrderService;
import com.shop.delivery.order.service.command.CreateOrderCommand;
import com.shop.delivery.order.service.command.OrderLineCommand;
import com.shop.delivery.shared.exception.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Customer-facing order API. Authentication via TelegramAuthFilter
 * (header X-Telegram-Init-Data → @CurrentUser).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;
    private final OrderMapper mapper;

    public OrderController(OrderService service, OrderMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@CurrentUser TelegramUser user,
                                @Valid @RequestBody CreateOrderRequest req) {
        String voucherProducts = req.voucherCodes() != null ? req.voucherCodes().products() : null;
        String voucherShipping = req.voucherCodes() != null ? req.voucherCodes().shipping() : null;
        CreateOrderCommand cmd = new CreateOrderCommand(
            user.getId(),
            firstNonNull(req.customerName(), user.getFirstName()),
            req.customerPhone(),
            req.deliveryAddress(), req.deliveryLat(), req.deliveryLng(),
            req.items().stream().map(i -> new OrderLineCommand(i.productId(), i.quantity())).toList(),
            req.paymentMethod(), req.note(),
            voucherProducts, voucherShipping
        );
        Order order = service.create(cmd);
        return mapper.toResponse(order);
    }

    @GetMapping("/mine")
    public Page<OrderSummary> mine(@CurrentUser TelegramUser user,
                                   @PageableDefault(size = 20) Pageable pageable) {
        return service.findMine(user.getId(), pageable).map(mapper::toSummary);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@CurrentUser TelegramUser user, @PathVariable UUID id) {
        Order order = service.findById(id);
        if (!order.getCustomerId().equals(user.getId())) {
            throw new NotFoundException(
                "ORDER_NOT_FOUND", "Đơn không tồn tại hoặc không thuộc về bạn");
        }
        return mapper.toResponse(order);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@CurrentUser TelegramUser user,
                                @PathVariable UUID id,
                                @RequestBody CancelOrderRequest req) {
        Order existing = service.findById(id);
        if (!existing.getCustomerId().equals(user.getId())) {
            throw new NotFoundException(
                "ORDER_NOT_FOUND", "Đơn không tồn tại hoặc không thuộc về bạn");
        }
        Order cancelled = service.cancel(id, user.getId(), req.reason());
        return mapper.toResponse(cancelled);
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
