package com.shop.delivery.order.api.dto;

import com.shop.delivery.order.domain.OrderStatus;

import java.time.Instant;

/**
 * One entry of an order's status timeline (a row of {@code status_history}).
 * {@code fromStatus} is null for the very first transition. {@code changedByUserId}
 * is the raw actor id — the Web Admin derives a coarse role label from the
 * transition itself, so no cross-table name lookup happens server-side.
 */
public record StatusHistoryResponse(
    Long id,
    OrderStatus fromStatus,
    OrderStatus toStatus,
    Long changedByUserId,
    Instant changedAt,
    String note
) {
}
