package com.shop.delivery.delivery.api.customer;

import com.shop.delivery.auth.api.CurrentUser;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.delivery.api.customer.dto.LocationPingResponse;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.delivery.service.LocationPingService;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.service.OrderService;
import com.shop.delivery.shared.exception.NotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderTrackingController {

    private final DeliveryAssignmentRepository assignmentRepo;
    private final LocationPingService pingService;
    private final OrderService orderService;

    public OrderTrackingController(DeliveryAssignmentRepository assignmentRepo,
                                   LocationPingService pingService,
                                   OrderService orderService) {
        this.assignmentRepo = assignmentRepo;
        this.pingService = pingService;
        this.orderService = orderService;
    }

    @GetMapping("/{orderId}/location")
    public LocationPingResponse getLatestLocation(@CurrentUser TelegramUser user,
                                                  @PathVariable UUID orderId) {
        Order order = orderService.findById(orderId);
        if (!order.getCustomerId().equals(user.getId())) {
            throw new NotFoundException("ORDER_NOT_FOUND", "Đơn không tồn tại hoặc không thuộc về bạn");
        }
        DeliveryAssignment a = assignmentRepo.findByOrderId(orderId)
            .orElseThrow(() -> new NotFoundException("NO_ASSIGNMENT", "Đơn chưa được gán shipper"));

        return pingService.findLatestForAssignment(a.getId())
            .map(p -> new LocationPingResponse(p.getLat(), p.getLng(), p.getAccuracy(), p.getHeading(), p.getRecordedAt()))
            .orElseThrow(() -> new NotFoundException("NO_LOCATION_YET", "Shipper chưa chia sẻ vị trí"));
    }
}
