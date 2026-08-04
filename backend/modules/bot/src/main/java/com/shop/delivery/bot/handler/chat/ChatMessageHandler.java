package com.shop.delivery.bot.handler.chat;

import com.shop.delivery.bot.fsm.ChatStates;
import com.shop.delivery.bot.fsm.ConversationState;
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
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Relays a plain-text message from a user in {@link ChatStates#CHAT_ACTIVE} to
 * the other party of the delivery. {@code @Order(0)} so it runs before generic
 * command handlers while chatting.
 *
 * <p>Privacy: the message is re-sent as new text prefixed with the sender's
 * role (never {@code forwardMessage}), so neither party sees the other's
 * Telegram account. Every message is persisted for audit.
 *
 * <p>Command escape: only {@code /thoat} is consumed here; other slash-commands
 * fall through so {@code /start}, {@code /help} etc. still work mid-chat.
 */
@Component
@Order(0)
public class ChatMessageHandler implements UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageHandler.class);

    private final ConversationStateService conv;
    private final ChatService chatService;
    private final BotSender sender;

    public ChatMessageHandler(ConversationStateService conv, ChatService chatService, BotSender sender) {
        this.conv = conv;
        this.chatService = chatService;
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update u) {
        if (u.getMessage() == null || !u.getMessage().hasText() || u.getMessage().getFrom() == null) {
            return false;
        }
        Long userId = u.getMessage().getFrom().getId();
        boolean inChat = conv.get(userId)
            .map(s -> ChatStates.CHAT_ACTIVE.equals(s.getState()))
            .orElse(false);
        if (!inChat) return false;
        String text = u.getMessage().getText().trim();
        // Consume plain text and the exit command; let other /commands escape.
        return ChatStates.EXIT_COMMAND.equalsIgnoreCase(text) || !text.startsWith("/");
    }

    @Override
    public void handle(Update u) {
        Long userId = u.getMessage().getFrom().getId();
        Long chatId = u.getMessage().getChatId();
        String text = u.getMessage().getText().trim();

        if (ChatStates.EXIT_COMMAND.equalsIgnoreCase(text)) {
            conv.clear(userId);
            sender.sendText(chatId, "Đã kết thúc trò chuyện ẩn danh.");
            return;
        }
        if (text.isBlank()) return; // nothing to relay

        ConversationState state = conv.get(userId).orElse(null);
        if (state == null || state.getData() == null) return;
        Map<String, Object> data = state.getData();

        UUID assignmentId;
        ChatRole role;
        try {
            assignmentId = UUID.fromString(String.valueOf(data.get(ChatStates.KEY_ASSIGNMENT_ID)));
            role = ChatRole.valueOf(String.valueOf(data.get(ChatStates.KEY_ROLE)));
        } catch (IllegalArgumentException ex) {
            conv.clear(userId); // corrupt FSM payload — drop out of chat cleanly
            return;
        }

        Optional<ChatParticipants> maybe = chatService.participants(assignmentId);
        if (maybe.isEmpty() || !maybe.get().active()) {
            conv.clear(userId);
            sender.sendText(chatId, "Đơn đã hoàn tất — trò chuyện đã đóng.");
            return;
        }
        Long peerId = maybe.get().peerOf(userId);
        if (peerId == null) {
            conv.clear(userId);
            return;
        }

        chatService.record(assignmentId, role, userId, text);

        String prefix = role == ChatRole.CUSTOMER ? "🧑 Khách: " : "🛵 Shipper: ";
        relayToPeer(peerId, prefix + text, assignmentId);
        log.info("Relayed chat assignment={} from {} to {}", assignmentId, role, peerId);
    }

    /** Send the relayed message; attach a "Trả lời" button if the peer isn't already chatting. */
    private void relayToPeer(Long peerId, String body, UUID assignmentId) {
        if (peerAlreadyChatting(peerId, assignmentId)) {
            sender.sendText(peerId, body);
            return;
        }
        InlineKeyboardButton reply = InlineKeyboardButton.builder()
            .text("💬 Trả lời")
            .callbackData(ChatStates.CALLBACK_CHAT_OPEN + assignmentId)
            .build();
        SendMessage msg = SendMessage.builder()
            .chatId(peerId)
            .text(body)
            .replyMarkup(InlineKeyboardMarkup.builder().keyboard(List.of(List.of(reply))).build())
            .build();
        sender.execute(msg);
    }

    private boolean peerAlreadyChatting(Long peerId, UUID assignmentId) {
        return conv.get(peerId)
            .filter(s -> ChatStates.CHAT_ACTIVE.equals(s.getState()))
            .map(ConversationState::getData)
            .map(d -> assignmentId.toString().equals(String.valueOf(d.get(ChatStates.KEY_ASSIGNMENT_ID))))
            .orElse(false);
    }
}
