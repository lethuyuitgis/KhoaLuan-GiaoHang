package com.shop.delivery.delivery.api.customer;

import com.shop.delivery.auth.api.CurrentUser;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.delivery.api.admin.dto.ChatMessageResponse;
import com.shop.delivery.delivery.api.customer.dto.SendChatRequest;
import com.shop.delivery.delivery.entity.ChatMessage;
import com.shop.delivery.delivery.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Customer-facing anonymous chat for the Mini App. Unlike the Telegram flow
 * (where the customer chats in the bot DM), the Zalo Mini App has no bot DM, so
 * it reads/sends messages over REST here. Auth via {@code @CurrentUser}; every
 * call is scoped to the customer's own order. Responses never expose the other
 * party's id (only role + text), preserving anonymity.
 */
@RestController
@RequestMapping("/api/orders")
public class CustomerChatController {

    private final ChatService chatService;

    public CustomerChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/{orderId}/chat")
    public List<ChatMessageResponse> history(@CurrentUser TelegramUser user, @PathVariable UUID orderId) {
        return chatService.historyForCustomer(orderId, user.getId()).stream()
            .map(this::toResponse).toList();
    }

    @PostMapping("/{orderId}/chat")
    public ChatMessageResponse send(@CurrentUser TelegramUser user,
                                    @PathVariable UUID orderId,
                                    @Valid @RequestBody SendChatRequest req) {
        return toResponse(chatService.sendFromCustomer(orderId, user.getId(), req.body().trim()));
    }

    private ChatMessageResponse toResponse(ChatMessage m) {
        return new ChatMessageResponse(m.getId(), m.getSenderRole(), m.getBody(), m.getCreatedAt());
    }
}
