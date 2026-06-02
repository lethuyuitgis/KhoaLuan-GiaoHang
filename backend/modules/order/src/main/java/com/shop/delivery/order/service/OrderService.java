package com.shop.delivery.order.service;

import com.shop.delivery.order.config.ShopConfigProperties;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.entity.OrderItem;
import com.shop.delivery.order.entity.Product;
import com.shop.delivery.order.entity.StatusHistory;
import com.shop.delivery.order.repository.OrderItemRepository;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.order.repository.StatusHistoryRepository;
import com.shop.delivery.order.service.command.CreateOrderCommand;
import com.shop.delivery.order.service.command.OrderLineCommand;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.shared.event.OrderConfirmedEvent;
import com.shop.delivery.shared.event.OrderCreatedEvent;
import com.shop.delivery.shared.exception.NotFoundException;
import com.shop.delivery.shared.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final StatusHistoryRepository statusHistoryRepo;
    private final ProductService productService;
    private final ShopConfigProperties shopProps;
    private final DistanceCalculator distance;
    private final FeeCalculator fee;
    private final OrderStateMachine stateMachine;
    private final OrderCodeGenerator codeGen;
    private final ApplicationEventPublisher events;

    public OrderService(OrderRepository orderRepo, OrderItemRepository orderItemRepo,
                        StatusHistoryRepository statusHistoryRepo,
                        ProductService productService,
                        ShopConfigProperties shopProps,
                        DistanceCalculator distance, FeeCalculator fee,
                        OrderStateMachine stateMachine, OrderCodeGenerator codeGen,
                        ApplicationEventPublisher events) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.statusHistoryRepo = statusHistoryRepo;
        this.productService = productService;
        this.shopProps = shopProps;
        this.distance = distance;
        this.fee = fee;
        this.stateMachine = stateMachine;
        this.codeGen = codeGen;
        this.events = events;
    }

    @Transactional
    public Order create(CreateOrderCommand cmd) {
        if (cmd.items() == null || cmd.items().isEmpty()) {
            throw new ValidationException("EMPTY_ORDER", "Đơn không có sản phẩm");
        }

        // Resolve product → snapshot price → build OrderItem
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        for (OrderLineCommand line : cmd.items()) {
            Product p = productService.findById(line.productId());
            if (!p.isActive()) {
                throw new ValidationException("PRODUCT_INACTIVE", "Sản phẩm " + p.getName() + " không còn bán");
            }
            BigDecimal lineSub = p.getPrice().multiply(BigDecimal.valueOf(line.quantity()));
            subtotal = subtotal.add(lineSub);

            OrderItem item = new OrderItem();
            item.setProductId(p.getId());
            item.setQuantity(line.quantity());
            item.setUnitPrice(p.getPrice());
            item.setSubtotal(lineSub);
            items.add(item);
        }

        BigDecimal pickupLat = shopProps.getPickup().getLat();
        BigDecimal pickupLng = shopProps.getPickup().getLng();
        BigDecimal distKm = distance.haversineKm(pickupLat, pickupLng, cmd.deliveryLat(), cmd.deliveryLng());
        BigDecimal deliveryFee = fee.calculate(distKm);
        BigDecimal total = subtotal.add(deliveryFee);

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCode(generateUniqueCode());
        order.setCustomerId(cmd.customerId());
        order.setCustomerName(cmd.customerName());
        order.setCustomerPhone(cmd.customerPhone());
        order.setPickupLat(pickupLat);
        order.setPickupLng(pickupLng);
        order.setDeliveryAddress(cmd.deliveryAddress());
        order.setDeliveryLat(cmd.deliveryLat());
        order.setDeliveryLng(cmd.deliveryLng());
        order.setDistanceKm(distKm);
        order.setSubtotal(subtotal);
        order.setDeliveryFee(deliveryFee);
        order.setTotal(total);
        order.setPaymentMethod(cmd.paymentMethod());
        order.setNote(cmd.note());

        Order saved = orderRepo.save(order);

        for (OrderItem item : items) {
            item.setOrderId(saved.getId());
        }
        orderItemRepo.saveAll(items);

        recordTransition(saved.getId(), null, OrderStatus.PENDING, cmd.customerId(), "Đơn được tạo");

        // P9 — broadcast to admin dashboard via WebSocket (AdminOrderBroadcaster listens AFTER_COMMIT).
        events.publishEvent(new OrderCreatedEvent(
            saved.getId(),
            saved.getCode(),
            saved.getCustomerId(),
            saved.getPaymentMethod().name()
        ));

        return saved;
    }

    @Transactional(readOnly = true)
    public Order findById(UUID id) {
        return orderRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Đơn " + id + " không tồn tại"));
    }

    @Transactional(readOnly = true)
    public Page<Order> findMine(Long customerId, Pageable pageable) {
        return orderRepo.findAllByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Order> findAll(Pageable pageable) {
        return orderRepo.findAll(pageable);
    }

    @Transactional
    public Order confirm(UUID id, Long actorUserId, String note) {
        return transitionStatus(id, OrderStatus.CONFIRMED, actorUserId, note);
    }

    /**
     * Called by {@link com.shop.delivery.order.listener.PaymentEventListener} after a
     * VNPay payment succeeds. Idempotent — repeated calls are safe.
     *
     * <p>Always sets {@code paymentStatus = SUCCESS}. If current {@code status == PENDING},
     * also transitions to {@code CONFIRMED} via the state machine and publishes
     * {@link OrderConfirmedEvent}. If the order is already CONFIRMED, only the
     * payment status update is persisted (no duplicate event). If the order is in
     * a terminal/cancelled state, only the payment_status flag is updated so
     * audit/reporting stays accurate.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirmAfterPayment(UUID orderId) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND",
                "Đơn " + orderId + " không tồn tại khi xử lý xác nhận thanh toán"));

        boolean shouldTransition = order.getStatus() == OrderStatus.PENDING;
        boolean isCancelled = order.getStatus() == OrderStatus.CANCELLED
                           || order.getStatus() == OrderStatus.RETURNED;

        // Always mark payment as SUCCESS (denormalised from payment table)
        order.setPaymentStatus(PaymentStatus.SUCCESS);

        if (shouldTransition) {
            stateMachine.requireAllowed(order.getStatus(), OrderStatus.CONFIRMED);
            OrderStatus from = order.getStatus();
            order.setStatus(OrderStatus.CONFIRMED);
            Order saved = orderRepo.save(order);
            recordTransition(saved.getId(), from, OrderStatus.CONFIRMED, null,
                "Đơn được xác nhận sau khi thanh toán VNPay");
            events.publishEvent(new OrderConfirmedEvent(
                saved.getId(), saved.getCode(), saved.getPaymentMethod().name()));
            log.info("confirmAfterPayment: order {} transitioned PENDING→CONFIRMED", saved.getCode());
        } else if (!isCancelled) {
            // Already CONFIRMED/ASSIGNED/DELIVERING — just persist the payment_status flip
            orderRepo.save(order);
        } else {
            // CANCELLED/RETURNED — payment came late; flip flag for audit but no event/state change
            orderRepo.save(order);
        }
    }

    @Transactional
    public Order cancel(UUID id, Long actorUserId, String reason) {
        return transitionStatus(id, OrderStatus.CANCELLED, actorUserId, reason);
    }

    @Transactional
    public Order transitionStatus(UUID id, OrderStatus to, Long actorUserId, String note) {
        Order order = findById(id);
        stateMachine.requireAllowed(order.getStatus(), to);
        OrderStatus from = order.getStatus();
        order.setStatus(to);
        Order saved = orderRepo.save(order);
        recordTransition(saved.getId(), from, to, actorUserId, note);
        return saved;
    }

    private void recordTransition(UUID orderId, OrderStatus from, OrderStatus to,
                                  Long actorUserId, String note) {
        StatusHistory h = new StatusHistory();
        h.setOrderId(orderId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedByUserId(actorUserId);
        h.setChangedAt(Instant.now());
        h.setNote(note);
        statusHistoryRepo.save(h);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = codeGen.generate();
            if (!orderRepo.existsByCode(candidate)) return candidate;
        }
        throw new IllegalStateException("Không thể sinh mã đơn unique sau 10 lần thử");
    }
}
