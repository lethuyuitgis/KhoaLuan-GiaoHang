package com.shop.delivery.order.listener;

import com.shop.delivery.order.service.OrderService;
import com.shop.delivery.shared.event.PaymentSucceededEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges {@code payment} → {@code order} via Spring events.
 *
 * <p>Uses {@code AFTER_COMMIT} so that {@link OrderService#confirmAfterPayment}
 * runs in a NEW transaction once the {@code Payment} row commit is durable.
 * This is the recommended pattern from research §Architecture Pattern 3: it
 * avoids the dirty-write race where the IPN's same-transaction view of the
 * Payment row could roll back if the order transition fails.
 */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final OrderService orderService;

    public PaymentEventListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(PaymentSucceededEvent ev) {
        log.info("Payment succeeded — confirming order: orderId={} paymentId={} txnRef={}",
            ev.orderId(), ev.paymentId(), ev.txnRef());
        try {
            orderService.confirmAfterPayment(ev.orderId());
        } catch (RuntimeException re) {
            // Defensive: IPN ack to VNPay is already 00. If order transition fails
            // (e.g. optimistic-lock collision), log loudly so the user retries.
            log.error("Failed to confirm order {} after payment succeeded: {}",
                ev.orderId(), re.getMessage(), re);
        }
    }
}
