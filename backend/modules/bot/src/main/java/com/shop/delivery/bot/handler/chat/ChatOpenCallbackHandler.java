package com.shop.delivery.bot.handler.chat;

import com.shop.delivery.bot.fsm.ChatStates;
import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.domain.ChatRole;
import com.shop.delivery.delivery.service.ChatParticipants;
import com.shop.delivery.delivery.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles the {@code CHAT_OPEN:<assignmentId>} callback (from the "💬 Chat" /
 * "💬 Trả lời" buttons). Puts the tapping user into {@link ChatStates#CHAT_ACTIVE}
 * for that delivery — after validating they are actually a party to it and the
 * delivery is still open.
 */
@Component
@Order(35)
public class ChatOpenCallbackHandler implements UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatOpenCallbackHandler.class);

    private final ConversationStateService conv;
    private final ChatService chatService;
    private final BotSender sender;

    public ChatOpenCallbackHandler(ConversationStateService conv, ChatService chatService, BotSender sender) {
        this.conv = conv;
        this.chatService = chatService;
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update u) {
        if (!u.hasCallbackQuery()) return false;
        String data = u.getCallbackQuery().getData();
        return data != null && data.startsWith(ChatStates.CALLBACK_CHAT_OPEN);
    }

    @Override
    public void handle(Update u) {
        CallbackQuery cb = u.getCallbackQuery();
        Long userId = cb.getFrom().getId();
        Long chatId = cb.getMessage().getChatId();
        String idPart = cb.getData().substring(ChatStates.CALLBACK_CHAT_OPEN.length());

        UUID assignmentId;
        try {
            assignmentId = UUID.fromString(idPart);
        } catch (IllegalArgumentException ex) {
            ack(cb, "ID không hợp lệ", true);
            return;
        }

        Optional<ChatParticipants> maybe = chatService.participants(assignmentId);
        if (maybe.isEmpty() || !maybe.get().includes(userId)) {
            ack(cb, "Bạn không thuộc đơn này", true);
            return;
        }
        ChatParticipants p = maybe.get();
        if (!p.active()) {
            ack(cb, "Đơn đã hoàn tất", true);
            sender.sendText(chatId, "Đơn đã hoàn tất — không thể trò chuyện.");
            return;
        }

        ChatRole role = userId.equals(p.customerId()) ? ChatRole.CUSTOMER : ChatRole.SHIPPER;
        Map<String, Object> data = new HashMap<>();
        data.put(ChatStates.KEY_ASSIGNMENT_ID, assignmentId.toString());
        data.put(ChatStates.KEY_ROLE, role.name());
        conv.put(userId, ChatStates.CHAT_ACTIVE, data);

        ack(cb, "Đã mở trò chuyện", false);
        String other = role == ChatRole.CUSTOMER ? "shipper" : "khách hàng";
        sender.sendText(chatId,
            "💬 Đang trò chuyện ẩn danh với " + other + ".\n"
                + "Gõ tin nhắn để gửi. Gõ /thoat để kết thúc.");
        log.info("Chat opened userId={} assignment={} role={}", userId, assignmentId, role);
    }

    private void ack(CallbackQuery cb, String text, boolean alert) {
        sender.execute(AnswerCallbackQuery.builder()
            .callbackQueryId(cb.getId())
            .text(text)
            .showAlert(alert)
            .build());
    }
}
