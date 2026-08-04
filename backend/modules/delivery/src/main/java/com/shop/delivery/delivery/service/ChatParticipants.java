package com.shop.delivery.delivery.service;

import java.util.UUID;

/**
 * The two ends of an order's anonymous chat, plus whether the delivery is still
 * in a state where messaging is allowed (shipper accepted, not yet finished).
 */
public record ChatParticipants(
    UUID assignmentId,
    UUID orderId,
    Long customerId,
    Long shipperId,
    boolean active
) {
    /** True if {@code userId} is a party to this chat. */
    public boolean includes(Long userId) {
        return userId != null && (userId.equals(customerId) || userId.equals(shipperId));
    }

    /** The other party's telegram id for a given sender, or null if not a party. */
    public Long peerOf(Long userId) {
        if (userId == null) return null;
        if (userId.equals(customerId)) return shipperId;
        if (userId.equals(shipperId)) return customerId;
        return null;
    }
}
