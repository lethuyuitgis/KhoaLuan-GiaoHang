package com.shop.delivery.shared.event;

import java.util.UUID;

/**
 * Published by {@code OrderService.create(...)} after the order row is
 * persisted and the initial PENDING transition recorded.
 *
 * <p>Lives in {@code shared.event} (alongside {@code OrderConfirmedEvent})
 * so any module — without depending on {@code order} — can consume it.
 *
 * @param customerId Telegram user id of the customer who placed the order.
 *                   Kept as Long (NOT a String) since downstream listeners
 *                   (notification, broadcasters) often join against telegram_user.
 * @param paymentMethod string form of {@code PaymentMethod} (COD / VNPAY) —
 *                      kept as String to avoid {@code order.domain} cross-import.
 */
public record OrderCreatedEvent(
    UUID orderId,
    String orderCode,
    Long customerId,
    String paymentMethod
) {}
