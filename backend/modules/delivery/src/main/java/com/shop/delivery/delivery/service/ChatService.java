package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.domain.AssignmentStatus;
import com.shop.delivery.delivery.domain.ChatRole;
import com.shop.delivery.delivery.entity.ChatMessage;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.repository.ChatMessageRepository;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.service.OrderService;
import com.shop.delivery.shared.event.CustomerChatSentEvent;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;

    public ChatService(DeliveryAssignmentRepository assignmentRepo,
                       ChatMessageRepository chatRepo,
                       OrderService orderService,
                       ApplicationEventPublisher events) {
        this.assignmentRepo = assignmentRepo;
        this.chatRepo = chatRepo;
        this.orderService = orderService;
        this.events = events;
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

    // ---- Customer-facing chat (Mini App, esp. Zalo which has no bot DM) ------

    /**
     * History for the customer's own order. Empty if not yet assigned. Throws
     * {@link NotFoundException} if the order isn't this customer's (don't leak).
     */
    @Transactional(readOnly = true)
    public List<ChatMessage> historyForCustomer(UUID orderId, Long customerId) {
        Optional<DeliveryAssignment> a = assignmentRepo.findByOrderId(orderId);
        if (a.isEmpty()) {
            requireOwner(orderId, customerId); // still verify ownership before "empty"
            return List.of();
        }
        ChatParticipants p = participants(a.get().getId()).orElseThrow();
        requireCustomer(p, customerId);
        return history(a.get().getId());
    }

    /**
     * Record a message the customer sent from the Mini App and publish
     * {@link CustomerChatSentEvent} so the notification module relays it to the
     * shipper's Telegram bot. Validates ownership + that chat is still open.
     */
    @Transactional
    public ChatMessage sendFromCustomer(UUID orderId, Long customerId, String body) {
        // Verify ownership first so a non-owner can't probe an order's assignment
        // state (uniform NOT_FOUND instead of leaking "exists but unassigned").
        requireOwner(orderId, customerId);
        DeliveryAssignment a = assignmentRepo.findByOrderId(orderId)
            .orElseThrow(() -> new NotFoundException(
                "NO_ASSIGNMENT", "Đơn chưa có shipper — chưa thể nhắn tin"));
        ChatParticipants p = participants(a.getId()).orElseThrow();
        requireCustomer(p, customerId);
        if (!p.active()) {
            throw new BusinessRuleException("CHAT_CLOSED", "Đơn đã hoàn tất — trò chuyện đã đóng");
        }
        ChatMessage saved = record(a.getId(), ChatRole.CUSTOMER, customerId, body);
        events.publishEvent(new CustomerChatSentEvent(a.getId(), p.shipperId(), body));
        return saved;
    }

    private void requireCustomer(ChatParticipants p, Long customerId) {
        if (!customerId.equals(p.customerId())) {
            throw new NotFoundException("ORDER_NOT_FOUND", "Không tìm thấy đơn của bạn");
        }
    }

    private void requireOwner(UUID orderId, Long customerId) {
        Order order = orderService.findById(orderId);
        if (!customerId.equals(order.getCustomerId())) {
            throw new NotFoundException("ORDER_NOT_FOUND", "Không tìm thấy đơn của bạn");
        }
    }
}
