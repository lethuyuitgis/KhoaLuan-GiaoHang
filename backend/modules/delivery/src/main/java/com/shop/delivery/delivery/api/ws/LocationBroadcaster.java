package com.shop.delivery.delivery.api.ws;

import com.shop.delivery.delivery.service.event.LocationPingReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
public class LocationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LocationBroadcaster.class);

    private final SimpMessagingTemplate ws;

    public LocationBroadcaster(SimpMessagingTemplate ws) {
        this.ws = ws;
    }

    public record LocationMessage(
        UUID orderId,
        BigDecimal lat,
        BigDecimal lng,
        BigDecimal accuracy,
        BigDecimal heading,
        Instant recordedAt
    ) {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPingReceived(LocationPingReceivedEvent e) {
        // Send to the order's customer only — `/user/{customerId}/queue/order/{orderId}/location`.
        // Spring resolves the principal-name from the STOMP session, so subscribers on a different
        // user principal cannot receive this message even if they SUBSCRIBE to the same orderId path.
        String destination = "/queue/order/" + e.orderId() + "/location";
        String userName = String.valueOf(e.customerId());
        LocationMessage payload = new LocationMessage(
            e.orderId(), e.lat(), e.lng(), e.accuracy(), e.heading(), e.recordedAt()
        );
        ws.convertAndSendToUser(userName, destination, payload);
        log.debug("Broadcast location to user={} dest={}: lat={}, lng={}",
            userName, destination, e.lat(), e.lng());
    }
}
