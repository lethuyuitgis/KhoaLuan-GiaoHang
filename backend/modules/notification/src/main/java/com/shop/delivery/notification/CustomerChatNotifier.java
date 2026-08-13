package com.shop.delivery.notification;

import com.shop.delivery.bot.fsm.ChatStates;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.shared.event.CustomerChatSentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

/**
 * Relays a chat message a customer sent from the Mini App (Zalo, which has no
 * bot DM) to the shipper's Telegram bot. Runs {@code AFTER_COMMIT} so the relay
 * never fires on rollback. The shipper sees prefixed text (not the customer's
 * account) and a "Trả lời" button that opens chat mode in the bot to reply.
 */
@Component
public class CustomerChatNotifier {

    private static final Logger log = LoggerFactory.getLogger(CustomerChatNotifier.class);

    private final BotSender bot;

    public CustomerChatNotifier(BotSender bot) {
        this.bot = bot;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCustomerChat(CustomerChatSentEvent e) {
        InlineKeyboardButton reply = InlineKeyboardButton.builder()
            .text("💬 Trả lời")
            .callbackData(ChatStates.CALLBACK_CHAT_OPEN + e.assignmentId())
            .build();
        SendMessage msg = SendMessage.builder()
            .chatId(e.shipperId())
            .text("🧑 Khách: " + e.body())
            .replyMarkup(InlineKeyboardMarkup.builder().keyboard(List.of(List.of(reply))).build())
            .build();
        bot.execute(msg);
        log.info("Relayed customer chat (assignment {}) to shipper {}", e.assignmentId(), e.shipperId());
    }
}
