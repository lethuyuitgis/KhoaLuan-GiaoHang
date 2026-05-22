package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.domain.AssignmentStatus;
import com.shop.delivery.delivery.domain.ShipperState;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.delivery.repository.ShipperProfileRepository;
import com.shop.delivery.delivery.service.command.AssignShipperCommand;
import com.shop.delivery.delivery.service.event.OrderAcceptedEvent;
import com.shop.delivery.delivery.service.event.OrderAssignedEvent;
import com.shop.delivery.delivery.service.event.OrderDeliveredEvent;
import com.shop.delivery.delivery.service.event.OrderRejectedEvent;
import com.shop.delivery.delivery.service.event.OrderStartedEvent;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.order.service.OrderService;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeliveryAssignmentService {

    private final DeliveryAssignmentRepository assignmentRepo;
    private final ShipperProfileRepository shipperRepo;
    private final OrderRepository orderRepo;
    private final OrderService orderService;
    private final ApplicationEventPublisher events;

    public DeliveryAssignmentService(DeliveryAssignmentRepository assignmentRepo,
                                     ShipperProfileRepository shipperRepo,
                                     OrderRepository orderRepo,
                                     OrderService orderService,
                                     ApplicationEventPublisher events) {
        this.assignmentRepo = assignmentRepo;
        this.shipperRepo = shipperRepo;
        this.orderRepo = orderRepo;
        this.orderService = orderService;
        this.events = events;
    }

    @Transactional
    public DeliveryAssignment assign(AssignShipperCommand cmd) {
        Order order = orderService.findById(cmd.orderId());
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessRuleException(
                "ORDER_NOT_ASSIGNABLE",
                "Đơn ở trạng thái " + order.getStatus() + " — không thể gán shipper");
        }
        if (assignmentRepo.findByOrderId(cmd.orderId()).isPresent()) {
            throw new BusinessRuleException("ORDER_ALREADY_ASSIGNED", "Đơn đã được gán shipper rồi");
        }

        ShipperProfile shipper = shipperRepo.findById(cmd.shipperId())
            .orElseThrow(() -> new NotFoundException("SHIPPER_NOT_FOUND", "Shipper " + cmd.shipperId() + " không tồn tại"));
        if (shipper.getCurrentState() != ShipperState.AVAILABLE) {
            throw new BusinessRuleException("SHIPPER_NOT_AVAILABLE",
                "Shipper " + cmd.shipperId() + " không sẵn sàng (trạng thái: " + shipper.getCurrentState() + ")");
        }

        DeliveryAssignment a = new DeliveryAssignment();
        a.setId(UUID.randomUUID());
        a.setOrderId(cmd.orderId());
        a.setShipperId(cmd.shipperId());
        a.setStatus(AssignmentStatus.OFFERED);
        a.setAssignedAt(Instant.now());
        DeliveryAssignment saved = assignmentRepo.save(a);

        orderService.transitionStatus(cmd.orderId(), OrderStatus.ASSIGNED, cmd.adminUserId(),
            "Gán shipper " + cmd.shipperId());

        events.publishEvent(new OrderAssignedEvent(
            saved.getId(), order.getId(), order.getCode(), cmd.shipperId(),
            order.getDeliveryAddress(), order.getDistanceKm(), order.getDeliveryFee()
        ));

        return saved;
    }

    @Transactional
    public DeliveryAssignment accept(UUID assignmentId, Long actingShipperId) {
        DeliveryAssignment a = findById(assignmentId);
        if (!a.getShipperId().equals(actingShipperId)) {
            throw new NotFoundException("ASSIGNMENT_NOT_FOUND", "Không tìm thấy đơn được gán cho bạn");
        }
        if (a.getStatus() != AssignmentStatus.OFFERED) {
            throw new BusinessRuleException("INVALID_ASSIGNMENT_STATE",
                "Đơn đã ở trạng thái " + a.getStatus() + " — không thể chấp nhận");
        }

        a.setStatus(AssignmentStatus.ACCEPTED);
        a.setAcceptedAt(Instant.now());
        DeliveryAssignment saved = assignmentRepo.save(a);

        shipperRepo.findById(actingShipperId).ifPresent(sp -> {
            sp.setCurrentState(ShipperState.BUSY);
            shipperRepo.save(sp);
        });

        Order order = orderService.findById(a.getOrderId());
        events.publishEvent(new OrderAcceptedEvent(
            saved.getId(), order.getId(), order.getCode(), actingShipperId, order.getCustomerId()
        ));
        return saved;
    }

    @Transactional
    public void reject(UUID assignmentId, Long actingShipperId) {
        DeliveryAssignment a = findById(assignmentId);
        if (!a.getShipperId().equals(actingShipperId)) {
            throw new NotFoundException("ASSIGNMENT_NOT_FOUND", "Không tìm thấy đơn được gán cho bạn");
        }
        if (a.getStatus() != AssignmentStatus.OFFERED) {
            throw new BusinessRuleException("INVALID_ASSIGNMENT_STATE",
                "Đơn đã ở trạng thái " + a.getStatus() + " — không thể từ chối");
        }

        Order order = orderService.findById(a.getOrderId());

        assignmentRepo.deleteById(assignmentId);
        orderService.transitionStatus(order.getId(), OrderStatus.CONFIRMED, actingShipperId,
            "Shipper từ chối — đơn quay về CONFIRMED");

        events.publishEvent(new OrderRejectedEvent(order.getId(), order.getCode(), actingShipperId));
    }

    @Transactional
    public DeliveryAssignment start(UUID assignmentId, Long actingShipperId) {
        DeliveryAssignment a = findById(assignmentId);
        if (!a.getShipperId().equals(actingShipperId)) {
            throw new NotFoundException("ASSIGNMENT_NOT_FOUND", "Không tìm thấy đơn được gán cho bạn");
        }
        if (a.getStatus() != AssignmentStatus.ACCEPTED) {
            throw new BusinessRuleException("INVALID_ASSIGNMENT_STATE",
                "Phải accept trước khi bắt đầu giao (hiện tại: " + a.getStatus() + ")");
        }

        a.setStatus(AssignmentStatus.STARTED);
        a.setStartedAt(Instant.now());
        DeliveryAssignment saved = assignmentRepo.save(a);

        Order order = orderService.findById(a.getOrderId());
        orderService.transitionStatus(order.getId(), OrderStatus.DELIVERING, actingShipperId, "Shipper bắt đầu giao");

        events.publishEvent(new OrderStartedEvent(
            saved.getId(), order.getId(), order.getCode(), actingShipperId, order.getCustomerId()
        ));
        return saved;
    }

    @Transactional
    public DeliveryAssignment complete(UUID assignmentId, Long actingShipperId) {
        DeliveryAssignment a = findById(assignmentId);
        if (!a.getShipperId().equals(actingShipperId)) {
            throw new NotFoundException("ASSIGNMENT_NOT_FOUND", "Không tìm thấy đơn được gán cho bạn");
        }
        if (a.getStatus() != AssignmentStatus.STARTED) {
            throw new BusinessRuleException("INVALID_ASSIGNMENT_STATE",
                "Phải bắt đầu giao trước (hiện tại: " + a.getStatus() + ")");
        }

        a.setStatus(AssignmentStatus.COMPLETED);
        a.setDeliveredAt(Instant.now());
        DeliveryAssignment saved = assignmentRepo.save(a);

        Order order = orderService.findById(a.getOrderId());
        orderService.transitionStatus(order.getId(), OrderStatus.DELIVERED, actingShipperId, "Shipper đã giao");

        shipperRepo.findById(actingShipperId).ifPresent(sp -> {
            sp.setTotalDeliveries(sp.getTotalDeliveries() + 1);
            sp.setCurrentState(ShipperState.AVAILABLE);
            shipperRepo.save(sp);
        });

        events.publishEvent(new OrderDeliveredEvent(
            saved.getId(), order.getId(), order.getCode(), actingShipperId, order.getCustomerId()
        ));
        return saved;
    }

    @Transactional(readOnly = true)
    public DeliveryAssignment findById(UUID id) {
        return assignmentRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND", "Đơn gán " + id + " không tồn tại"));
    }

    @Transactional(readOnly = true)
    public List<DeliveryAssignment> findMine(Long shipperId, List<AssignmentStatus> statuses) {
        return assignmentRepo.findAllByShipperIdAndStatusIn(shipperId, statuses);
    }
}
