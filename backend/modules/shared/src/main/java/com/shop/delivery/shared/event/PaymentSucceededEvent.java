package com.shop.delivery.shared.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published by {@code payment} module when an IPN flips a {@code Payment} row
 * from PENDING to SUCCESS. Consumed by {@code order} module's
 * {@code PaymentEventListener} (which calls {@code OrderService.confirmAfterPayment}).
 *
 * <p>Use {@code @TransactionalEventListener(AFTER_COMMIT)} on the consumer side
 * so the order transition runs in a new TX <em>after</em> the payment row is
 * committed — avoids dirty-write races between the IPN handler and the order
 * status update.
 */
public record PaymentSucceededEvent(
    UUID orderId,
    UUID paymentId,
    BigDecimal amount,
    String txnRef
) {}
