package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.domain.AssignmentStatus;
import com.shop.delivery.delivery.domain.ChatRole;
import com.shop.delivery.delivery.entity.ChatMessage;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.repository.ChatMessageRepository;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock DeliveryAssignmentRepository assignmentRepo;
    @Mock ChatMessageRepository chatRepo;
    @Mock OrderService orderService;
    @InjectMocks ChatService service;

    private final UUID assignmentId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final Long customerId = 100L;
    private final Long shipperId = 200L;

    private DeliveryAssignment assignment(AssignmentStatus status) {
        DeliveryAssignment a = new DeliveryAssignment();
        a.setId(assignmentId);
        a.setOrderId(orderId);
        a.setShipperId(shipperId);
        a.setStatus(status);
        return a;
    }

    private void stubOrder() {
        Order o = new Order();
        o.setId(orderId);
        o.setCustomerId(customerId);
        when(orderService.findById(orderId)).thenReturn(o);
    }

    @Test
    void participantsResolvesBothPartiesAndActiveForAcceptedDelivery() {
        when(assignmentRepo.findById(assignmentId)).thenReturn(Optional.of(assignment(AssignmentStatus.ACCEPTED)));
        stubOrder();

        ChatParticipants p = service.participants(assignmentId).orElseThrow();

        assertThat(p.customerId()).isEqualTo(customerId);
        assertThat(p.shipperId()).isEqualTo(shipperId);
        assertThat(p.active()).isTrue();
        assertThat(p.peerOf(customerId)).isEqualTo(shipperId);
        assertThat(p.peerOf(shipperId)).isEqualTo(customerId);
        assertThat(p.includes(999L)).isFalse();
        assertThat(p.peerOf(999L)).isNull();
    }

    @Test
    void participantsInactiveOnceDeliveryCompleted() {
        when(assignmentRepo.findById(assignmentId)).thenReturn(Optional.of(assignment(AssignmentStatus.COMPLETED)));
        stubOrder();

        assertThat(service.participants(assignmentId).orElseThrow().active()).isFalse();
    }

    @Test
    void participantsEmptyWhenAssignmentMissing() {
        when(assignmentRepo.findById(assignmentId)).thenReturn(Optional.empty());
        assertThat(service.participants(assignmentId)).isEmpty();
    }

    @Test
    void recordPersistsMessage() {
        when(chatRepo.save(org.mockito.ArgumentMatchers.any(ChatMessage.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.record(assignmentId, ChatRole.CUSTOMER, customerId, "chào shipper");

        ArgumentCaptor<ChatMessage> cap = ArgumentCaptor.forClass(ChatMessage.class);
        org.mockito.Mockito.verify(chatRepo).save(cap.capture());
        assertThat(cap.getValue().getSenderRole()).isEqualTo(ChatRole.CUSTOMER);
        assertThat(cap.getValue().getSenderUserId()).isEqualTo(customerId);
        assertThat(cap.getValue().getBody()).isEqualTo("chào shipper");
        assertThat(cap.getValue().getAssignmentId()).isEqualTo(assignmentId);
    }

    @Test
    void historyForOrderEmptyWhenNeverAssigned() {
        when(assignmentRepo.findByOrderId(orderId)).thenReturn(Optional.empty());
        assertThat(service.historyForOrder(orderId)).isEmpty();
    }
}
