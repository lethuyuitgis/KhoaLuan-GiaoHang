package com.shop.delivery.ws;

import com.shop.delivery.shared.event.OrderConfirmedEvent;
import com.shop.delivery.shared.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Listens for order-lifecycle events and pushes a compact JSON payload to
 * {@code /topic/admin/orders}. The Web Admin subscribes there and triggers
 * a TanStack Query invalidation on every message.
 *
 * <p>Lives in {@code app} module so it can reach {@link SimpMessagingTemplate}
 * (which Spring auto-configures only in the application that defines the
 * {@code WebSocketConfig}).
 *
 * <p>Listens with {@code phase = AFTER_COMMIT} so broadcasts don't fire when
 * the publishing transaction rolled back.
 */
@Component
public class AdminOrderBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderBroadcaster.class);
    private static final String TOPIC = "/topic/admin/orders";

    private final SimpMessagingTemplate broker;

    public AdminOrderBroadcaster(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent e) {
        broadcast("ORDER_CREATED", e.orderId(), e.orderCode(), "PENDING", e.paymentMethod(), e.customerId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderConfirmed(OrderConfirmedEvent e) {
        // OrderConfirmedEvent doesn't carry customerId; pass null (the FE only uses
        // it for optional display — the polling fallback covers detail anyway).
        broadcast("ORDER_CONFIRMED", e.orderId(), e.orderCode(), "CONFIRMED", e.paymentMethod(), null);
    }

    private void broadcast(String type, UUID orderId, String orderCode, String status,
                           String paymentMethod, Long customerId) {
        try {
            Map<String, Object> payload = Map.of(
                "type",          type,
                "orderId",       orderId.toString(),
                "orderCode",     orderCode,
                "status",        status,
                "paymentMethod", paymentMethod == null ? "" : paymentMethod,
                "customerId",    customerId == null ? 0L : customerId,
                "timestamp",     Instant.now().toString()
            );
            broker.convertAndSend(TOPIC, payload);
            log.debug("WS broadcast {} order {} → {}", type, orderCode, TOPIC);
        } catch (Exception ex) {
            // Never let a broker failure (e.g. broker not yet started during boot)
            // poison the calling transaction's AFTER_COMMIT path.
            log.warn("WS broadcast failed for {} {}: {}", type, orderCode, ex.getMessage());
        }
    }
}
