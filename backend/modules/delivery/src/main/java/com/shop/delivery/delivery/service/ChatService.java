package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.domain.AssignmentStatus;
import com.shop.delivery.delivery.domain.ChatRole;
import com.shop.delivery.delivery.entity.ChatMessage;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.repository.ChatMessageRepository;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Domain side of anonymous customer↔shipper chat. Resolves who the two parties
 * of a delivery are (and whether messaging is still open), and persists relayed
 * messages for audit. The actual Telegram send lives in the bot module — this
 * service never touches the bot, keeping delivery free of a bot dependency.
 */
@Service
public class ChatService {

    /** Chat is open once the shipper accepts, until the delivery finishes. */
    private static final Set<AssignmentStatus> ACTIVE =
        Set.of(AssignmentStatus.ACCEPTED, AssignmentStatus.STARTED);

    private final DeliveryAssignmentRepository assignmentRepo;
    private final ChatMessageRepository chatRepo;
    private final OrderService orderService;

    public ChatService(DeliveryAssignmentRepository assignmentRepo,
                       ChatMessageRepository chatRepo,
                       OrderService orderService) {
        this.assignmentRepo = assignmentRepo;
        this.chatRepo = chatRepo;
        this.orderService = orderService;
    }

    /** Resolve the customer/shipper of an assignment and whether chat is open. */
    @Transactional(readOnly = true)
    public Optional<ChatParticipants> participants(UUID assignmentId) {
        return assignmentRepo.findById(assignmentId).map(a -> {
            Order order = orderService.findById(a.getOrderId());
            return new ChatParticipants(
                a.getId(), a.getOrderId(), order.getCustomerId(), a.getShipperId(),
                ACTIVE.contains(a.getStatus()));
        });
    }

    @Transactional
    public ChatMessage record(UUID assignmentId, ChatRole role, Long senderUserId, String body) {
        ChatMessage m = new ChatMessage();
        m.setAssignmentId(assignmentId);
        m.setSenderRole(role);
        m.setSenderUserId(senderUserId);
        m.setBody(body);
        return chatRepo.save(m);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> history(UUID assignmentId) {
        return chatRepo.findByAssignmentIdOrderByCreatedAtAsc(assignmentId);
    }

    /** Chat history for an order (via its assignment); empty if never assigned. */
    @Transactional(readOnly = true)
    public List<ChatMessage> historyForOrder(UUID orderId) {
        return assignmentRepo.findByOrderId(orderId)
            .map(DeliveryAssignment::getId)
            .map(this::history)
            .orElseGet(List::of);
    }
}
