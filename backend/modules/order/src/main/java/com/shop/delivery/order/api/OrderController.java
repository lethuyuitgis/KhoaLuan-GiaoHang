package com.shop.delivery.order.api;

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
import com.shop.delivery.shared.exception.ValidationException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Customer-facing order API.
 * TODO P3: replace X-Customer-Id header với Telegram initData verification filter.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final String CUSTOMER_ID_HEADER = "X-Customer-Id";

    private final OrderService service;
    private final OrderMapper mapper;

    public OrderController(OrderService service, OrderMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@RequestHeader(CUSTOMER_ID_HEADER) Long customerId,
                                @Valid @RequestBody CreateOrderRequest req) {
        if (customerId == null) {
            throw new ValidationException("MISSING_CUSTOMER_ID", "Thiếu header " + CUSTOMER_ID_HEADER);
        }
        CreateOrderCommand cmd = new CreateOrderCommand(
            customerId,
            req.customerName(), req.customerPhone(),
            req.deliveryAddress(), req.deliveryLat(), req.deliveryLng(),
            req.items().stream().map(i -> new OrderLineCommand(i.productId(), i.quantity())).toList(),
            req.paymentMethod(), req.note()
        );
        Order order = service.create(cmd);
        return mapper.toResponse(order);
    }

    @GetMapping("/mine")
    public Page<OrderSummary> mine(@RequestHeader(CUSTOMER_ID_HEADER) Long customerId,
                                   @PageableDefault(size = 20) Pageable pageable) {
        return service.findMine(customerId, pageable).map(mapper::toSummary);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@RequestHeader(CUSTOMER_ID_HEADER) Long customerId,
                             @PathVariable UUID id) {
        Order order = service.findById(id);
        if (!order.getCustomerId().equals(customerId)) {
            throw new NotFoundException(
                "ORDER_NOT_FOUND", "Đơn không tồn tại hoặc không thuộc về bạn");
        }
        return mapper.toResponse(order);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@RequestHeader(CUSTOMER_ID_HEADER) Long customerId,
                                @PathVariable UUID id,
                                @RequestBody CancelOrderRequest req) {
        Order existing = service.findById(id);
        if (!existing.getCustomerId().equals(customerId)) {
            throw new NotFoundException(
                "ORDER_NOT_FOUND", "Đơn không tồn tại hoặc không thuộc về bạn");
        }
        Order cancelled = service.cancel(id, customerId, req.reason());
        return mapper.toResponse(cancelled);
    }
}
