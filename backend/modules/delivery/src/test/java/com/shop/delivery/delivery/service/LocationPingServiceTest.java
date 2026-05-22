package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.domain.AssignmentStatus;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.entity.LocationPing;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.delivery.repository.LocationPingRepository;
import com.shop.delivery.delivery.service.event.LocationPingReceivedEvent;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationPingServiceTest {

    @Mock LocationPingRepository pingRepo;
    @Mock DeliveryAssignmentRepository assignmentRepo;
    @Mock OrderService orderService;
    @Mock ApplicationEventPublisher events;

    private LocationPingService newService() {
        return new LocationPingService(pingRepo, assignmentRepo, orderService, events);
    }

    @Test
    void savePingForActiveShipperShouldPersistAndPublishEvent() {
        LocationPingService service = newService();
        UUID assignmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        DeliveryAssignment a = new DeliveryAssignment();
        a.setId(assignmentId);
        a.setShipperId(8888L);
        a.setOrderId(orderId);
        a.setStatus(AssignmentStatus.STARTED);

        Order o = new Order();
        o.setId(orderId);
        o.setCustomerId(5555L);

        when(assignmentRepo.findAllByShipperIdAndStatusInOrderByAssignedAtDesc(eq(8888L), any())).thenReturn(List.of(a));
        when(orderService.findById(orderId)).thenReturn(o);
        when(pingRepo.save(any(LocationPing.class))).thenAnswer(inv -> inv.getArgument(0));

        service.savePingForShipper(8888L,
            new BigDecimal("21.0193"), new BigDecimal("105.8503"),
            new BigDecimal("12.50"), new BigDecimal("180.5"));

        ArgumentCaptor<LocationPing> pingCaptor = ArgumentCaptor.forClass(LocationPing.class);
        verify(pingRepo).save(pingCaptor.capture());
        assertThat(pingCaptor.getValue().getAssignmentId()).isEqualTo(assignmentId);
        assertThat(pingCaptor.getValue().getLat()).isEqualByComparingTo("21.0193");

        verify(events).publishEvent(any(LocationPingReceivedEvent.class));
    }

    @Test
    void savePingForShipperWithNoActiveAssignmentShouldIgnore() {
        LocationPingService service = newService();
        when(assignmentRepo.findAllByShipperIdAndStatusInOrderByAssignedAtDesc(eq(8888L), any())).thenReturn(List.of());

        service.savePingForShipper(8888L,
            new BigDecimal("21.0"), new BigDecimal("105.8"), null, null);

        verify(pingRepo, never()).save(any());
        verify(events, never()).publishEvent(any());
    }
}
