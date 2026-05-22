package com.shop.delivery.shared.event;

import java.util.UUID;

/**
 * Published by {@code OrderService} when an order transitions to {@code CONFIRMED}.
 *
 * <p>P7 emits this from {@code confirmAfterPayment}. The existing
 * {@code OrderService.confirm} (used by Admin manual confirm of COD orders)
 * also gets this added in TASK 10. P8 wires bot listeners.
 *
 * @param paymentMethod string form of {@code PaymentMethod} (COD / VNPAY) — kept
 *                      as String to avoid {@code order.domain} cross-import.
 */
public record OrderConfirmedEvent(
    UUID orderId,
    String orderCode,
    String paymentMethod
) {}
