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
import com.shop.delivery.delivery.service.event.OrderStartedEvent;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.order.service.OrderService;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryAssignmentServiceTest {

    @Mock DeliveryAssignmentRepository assignmentRepo;
    @Mock ShipperProfileRepository shipperRepo;
    @Mock OrderRepository orderRepo;
    @Mock OrderService orderService;
    @Mock ApplicationEventPublisher events;

    private DeliveryAssignmentService newService() {
        return new DeliveryAssignmentService(assignmentRepo, shipperRepo, orderRepo, orderService, events);
    }

    @Test
    void assignShouldCreateAssignmentTransitionOrderAndPublishEvent() {
        DeliveryAssignmentService service = newService();
        UUID orderId = UUID.randomUUID();
        Order o = new Order();
        o.setId(orderId);
        o.setCode("DH20260521-A");
        o.setStatus(OrderStatus.CONFIRMED);
        o.setDeliveryAddress("45 Bà Triệu");
        o.setDistanceKm(new BigDecimal("1.5"));
        o.setDeliveryFee(new BigDecimal("20000"));
        o.setCustomerId(5555L);

        ShipperProfile sp = new ShipperProfile();
        sp.setUserId(8888L);
        sp.setCurrentState(ShipperState.AVAILABLE);

        when(orderService.findById(orderId)).thenReturn(o);
        when(shipperRepo.findById(8888L)).thenReturn(Optional.of(sp));
        when(assignmentRepo.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(assignmentRepo.save(any(DeliveryAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        DeliveryAssignment result = service.assign(new AssignShipperCommand(orderId, 8888L, 1L));

        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getShipperId()).isEqualTo(8888L);
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.OFFERED);

        verify(orderService).transitionStatus(orderId, OrderStatus.ASSIGNED, 1L, "Gán shipper " + 8888L);
        verify(events).publishEvent(any(OrderAssignedEvent.class));
    }

    @Test
    void assignShouldThrowIfOrderNotConfirmed() {
        DeliveryAssignmentService service = newService();
        UUID orderId = UUID.randomUUID();
        Order o = new Order();
        o.setId(orderId);
        o.setStatus(OrderStatus.ASSIGNED);
        when(orderService.findById(orderId)).thenReturn(o);

        assertThatThrownBy(() ->
            service.assign(new AssignShipperCommand(orderId, 8888L, 1L))
        ).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void assignShouldThrowIfShipperNotAvailable() {
        DeliveryAssignmentService service = newService();
        UUID orderId = UUID.randomUUID();
        Order o = new Order();
        o.setId(orderId);
        o.setStatus(OrderStatus.CONFIRMED);
        when(orderService.findById(orderId)).thenReturn(o);
        when(assignmentRepo.findByOrderId(orderId)).thenReturn(Optional.empty());
        ShipperProfile sp = new ShipperProfile();
        sp.setUserId(8888L);
        sp.setCurrentState(ShipperState.OFFLINE);
        when(shipperRepo.findById(8888L)).thenReturn(Optional.of(sp));

        assertThatThrownBy(() ->
            service.assign(new AssignShipperCommand(orderId, 8888L, 1L))
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("không sẵn sàng");
    }

    @Test
    void acceptShouldSetAssignmentAcceptedAndPublishEvent() {
        DeliveryAssignmentService service = newService();
        UUID assignmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        DeliveryAssignment a = new DeliveryAssignment();
        a.setId(assignmentId);
        a.setOrderId(orderId);
        a.setShipperId(8888L);
        a.setStatus(AssignmentStatus.OFFERED);

        Order o = new Order();
        o.setId(orderId);
        o.setCode("DH20260521-A");
        o.setCustomerId(5555L);

        when(assignmentRepo.findById(assignmentId)).thenReturn(Optional.of(a));
        when(orderService.findById(orderId)).thenReturn(o);
        when(assignmentRepo.save(any(DeliveryAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        DeliveryAssignment result = service.accept(assignmentId, 8888L);

        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.ACCEPTED);
        assertThat(result.getAcceptedAt()).isNotNull();
        verify(events).publishEvent(any(OrderAcceptedEvent.class));
    }

    @Test
    void acceptShouldFailIfShipperIsNotAssignedToOrder() {
        DeliveryAssignmentService service = newService();
        UUID assignmentId = UUID.randomUUID();
        DeliveryAssignment a = new DeliveryAssignment();
        a.setId(assignmentId);
        a.setShipperId(8888L);
        a.setStatus(AssignmentStatus.OFFERED);
        when(assignmentRepo.findById(assignmentId)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.accept(assignmentId, 9999L))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void startShouldTransitionAssignmentAndOrderToDelivering() {
        DeliveryAssignmentService service = newService();
        UUID assignmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        DeliveryAssignment a = new DeliveryAssignment();
        a.setId(assignmentId);
        a.setOrderId(orderId);
        a.setShipperId(8888L);
        a.setStatus(AssignmentStatus.ACCEPTED);

        Order o = new Order();
        o.setId(orderId);
        o.setCode("DH20260521-A");
        o.setCustomerId(5555L);

        when(assignmentRepo.findById(assignmentId)).thenReturn(Optional.of(a));
        when(orderService.findById(orderId)).thenReturn(o);
        when(assignmentRepo.save(any(DeliveryAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.start(assignmentId, 8888L);

        verify(orderService).transitionStatus(orderId, OrderStatus.DELIVERING, 8888L, "Shipper bắt đầu giao");
        verify(events).publishEvent(any(OrderStartedEvent.class));
    }

    @Test
    void startShouldThrowBusinessRuleWhenShipperAlreadyDeliveringAnotherOrder() {
        // Quy tắc "1 shipper chỉ giao 1 đơn tại một thời điểm" phải trả lỗi nghiệp vụ
        // (422) thay vì để vi phạm rơi xuống unique index uq_assignment_shipper_started (500).
        DeliveryAssignmentService service = newService();
        UUID assignmentId = UUID.randomUUID();
        DeliveryAssignment a = new DeliveryAssignment();
        a.setId(assignmentId);
        a.setOrderId(UUID.randomUUID());
        a.setShipperId(8888L);
        a.setStatus(AssignmentStatus.ACCEPTED);

        when(assignmentRepo.findById(assignmentId)).thenReturn(Optional.of(a));
        when(assignmentRepo.existsByShipperIdAndStatus(8888L, AssignmentStatus.STARTED)).thenReturn(true);

        assertThatThrownBy(() -> service.start(assignmentId, 8888L))
            .isInstanceOf(BusinessRuleException.class)
            .hasFieldOrPropertyWithValue("code", "SHIPPER_ALREADY_DELIVERING");

        verify(assignmentRepo, org.mockito.Mockito.never()).save(any(DeliveryAssignment.class));
    }

    @Test
    void completeShouldTransitionAssignmentAndOrderToDelivered() {
        DeliveryAssignmentService service = newService();
        UUID assignmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        DeliveryAssignment a = new DeliveryAssignment();
        a.setId(assignmentId);
        a.setOrderId(orderId);
        a.setShipperId(8888L);
        a.setStatus(AssignmentStatus.STARTED);

        Order o = new Order();
        o.setId(orderId);
        o.setCode("DH20260521-A");
        o.setCustomerId(5555L);

        ShipperProfile sp = new ShipperProfile();
        sp.setUserId(8888L);
        sp.setTotalDeliveries(5);

        when(assignmentRepo.findById(assignmentId)).thenReturn(Optional.of(a));
        when(orderService.findById(orderId)).thenReturn(o);
        when(shipperRepo.findById(8888L)).thenReturn(Optional.of(sp));
        when(assignmentRepo.save(any(DeliveryAssignment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shipperRepo.save(any(ShipperProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        service.complete(assignmentId, 8888L);

        verify(orderService).transitionStatus(orderId, OrderStatus.DELIVERED, 8888L, "Shipper đã giao");

        ArgumentCaptor<ShipperProfile> spCaptor = ArgumentCaptor.forClass(ShipperProfile.class);
        verify(shipperRepo).save(spCaptor.capture());
        assertThat(spCaptor.getValue().getTotalDeliveries()).isEqualTo(6);

        verify(events).publishEvent(any(OrderDeliveredEvent.class));
    }
}
