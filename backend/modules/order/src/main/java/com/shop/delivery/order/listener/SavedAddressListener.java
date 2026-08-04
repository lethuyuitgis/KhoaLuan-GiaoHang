package com.shop.delivery.order.listener;

import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.order.service.SavedAddressService;
import com.shop.delivery.shared.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Auto-captures the delivery address of every placed order into the customer's
 * saved-address list for Mini App autocomplete.
 *
 * <p>Runs {@code AFTER_COMMIT} so a saved-address failure can never roll back a
 * successfully-placed order — the address is a convenience, not part of the
 * order transaction. Loads the order fresh (the event carries only ids) to read
 * its resolved delivery address + coordinates.
 */
@Component
public class SavedAddressListener {

    private static final Logger log = LoggerFactory.getLogger(SavedAddressListener.class);

    private final OrderRepository orderRepo;
    private final SavedAddressService savedAddressService;

    public SavedAddressListener(OrderRepository orderRepo, SavedAddressService savedAddressService) {
        this.orderRepo = orderRepo;
        this.savedAddressService = savedAddressService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent e) {
        try {
            Order order = orderRepo.findById(e.orderId()).orElse(null);
            if (order == null) return;
            savedAddressService.recordUse(
                order.getCustomerId(),
                order.getDeliveryAddress(),
                order.getDeliveryLat(),
                order.getDeliveryLng());
        } catch (RuntimeException ex) {
            // Never surface to the customer — the order already succeeded.
            log.warn("Auto-save address failed for order {}: {}", e.orderId(), ex.getMessage());
        }
    }
}
