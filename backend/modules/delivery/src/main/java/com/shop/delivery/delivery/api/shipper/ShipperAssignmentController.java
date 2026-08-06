package com.shop.delivery.delivery.api.shipper;

import com.shop.delivery.auth.api.CurrentUser;
import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.service.RoleResolver;
import com.shop.delivery.delivery.api.shipper.dto.AssignmentResponse;
import com.shop.delivery.delivery.domain.AssignmentStatus;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.service.DeliveryAssignmentService;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.entity.OrderItem;
import com.shop.delivery.order.entity.Product;
import com.shop.delivery.order.repository.OrderItemRepository;
import com.shop.delivery.order.repository.ProductRepository;
import com.shop.delivery.order.service.OrderService;
import com.shop.delivery.shared.exception.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/shipper/assignments")
public class ShipperAssignmentController {

    private final DeliveryAssignmentService service;
    private final OrderService orderService;
    private final RoleResolver roleResolver;
    private final OrderItemRepository itemRepo;
    private final ProductRepository productRepo;

    public ShipperAssignmentController(DeliveryAssignmentService service,
                                       OrderService orderService,
                                       RoleResolver roleResolver,
                                       OrderItemRepository itemRepo,
                                       ProductRepository productRepo) {
        this.service = service;
        this.orderService = orderService;
        this.roleResolver = roleResolver;
        this.itemRepo = itemRepo;
        this.productRepo = productRepo;
    }

    private void requireShipper(TelegramUser user) {
        if (!roleResolver.hasRole(user.getId(), Role.SHIPPER)) {
            throw new AuthenticationException("NOT_SHIPPER", "Bạn không phải shipper");
        }
    }

    @GetMapping
    public List<AssignmentResponse> listActive(@CurrentUser TelegramUser user) {
        requireShipper(user);
        List<DeliveryAssignment> active = service.findMine(user.getId(),
            List.of(AssignmentStatus.OFFERED, AssignmentStatus.ACCEPTED, AssignmentStatus.STARTED));
        return active.stream().map(this::toResponse).toList();
    }

    @GetMapping("/history")
    public List<AssignmentResponse> listHistory(@CurrentUser TelegramUser user) {
        requireShipper(user);
        List<DeliveryAssignment> done = service.findMine(user.getId(),
            List.of(AssignmentStatus.COMPLETED, AssignmentStatus.CANCELLED));
        return done.stream().map(this::toResponse).toList();
    }

    @PostMapping("/{id}/accept")
    public AssignmentResponse accept(@CurrentUser TelegramUser user, @PathVariable UUID id) {
        requireShipper(user);
        return toResponse(service.accept(id, user.getId()));
    }

    @PostMapping("/{id}/reject")
    public void reject(@CurrentUser TelegramUser user, @PathVariable UUID id) {
        requireShipper(user);
        service.reject(id, user.getId());
    }

    @PostMapping("/{id}/start")
    public AssignmentResponse start(@CurrentUser TelegramUser user, @PathVariable UUID id) {
        requireShipper(user);
        return toResponse(service.start(id, user.getId()));
    }

    @PostMapping("/{id}/complete")
    public AssignmentResponse complete(@CurrentUser TelegramUser user, @PathVariable UUID id) {
        requireShipper(user);
        return toResponse(service.complete(id, user.getId()));
    }

    private AssignmentResponse toResponse(DeliveryAssignment a) {
        Order o = orderService.findById(a.getOrderId());
        return new AssignmentResponse(
            a.getId(), o.getId(), o.getCode(),
            o.getCustomerId(), o.getCustomerName(), o.getCustomerPhone(),
            o.getDeliveryAddress(), o.getDeliveryLat(), o.getDeliveryLng(),
            o.getDistanceKm(), o.getDeliveryFee(), o.getTotal(),
            o.getPaymentMethod().name(), o.getPaymentStatus().name(), o.getNote(),
            loadItems(o.getId()), o.getShipperCommission(),
            a.getStatus(), o.getStatus().name(),
            a.getAssignedAt(), a.getAcceptedAt(), a.getStartedAt(), a.getDeliveredAt()
        );
    }

    /** Món ×SL để shipper biết lấy gì ở shop; tên tra từ bảng product. */
    private List<AssignmentResponse.AssignmentItem> loadItems(UUID orderId) {
        List<OrderItem> items = itemRepo.findAllByOrderId(orderId);
        Map<Long, String> names = productRepo
            .findAllById(items.stream().map(OrderItem::getProductId).toList())
            .stream().collect(Collectors.toMap(Product::getId, Product::getName));
        return items.stream()
            .map(i -> new AssignmentResponse.AssignmentItem(
                names.getOrDefault(i.getProductId(), "SP#" + i.getProductId()), i.getQuantity()))
            .toList();
    }
}
