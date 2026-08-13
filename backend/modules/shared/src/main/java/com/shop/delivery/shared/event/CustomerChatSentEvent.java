package com.shop.delivery.shared.event;

import java.util.UUID;

/**
 * Published when a customer sends a chat message from the Mini App
 * (POST {@code /api/orders/{id}/chat}) — used by the Zalo Mini App, which has no
 * Telegram bot DM of its own. The notification module relays the text to the
 * shipper's Telegram bot so they can reply. Body is the raw message; the
 * shipper never sees the customer's account/id (relayed as plain prefixed text).
 */
public record CustomerChatSentEvent(
    UUID assignmentId,
    Long shipperId,
    String body
) {}
