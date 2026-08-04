package com.shop.delivery.delivery.api.admin.dto;

import com.shop.delivery.delivery.domain.ChatRole;

import java.time.Instant;

/** One chat message in an order's conversation, for Web Admin audit. */
public record ChatMessageResponse(
    Long id,
    ChatRole senderRole,
    String body,
    Instant createdAt
) {
}
