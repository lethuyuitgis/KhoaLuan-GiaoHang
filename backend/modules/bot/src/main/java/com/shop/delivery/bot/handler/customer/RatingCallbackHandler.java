package com.shop.delivery.bot.handler.customer;

import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.service.RatingService;
import com.shop.delivery.shared.exception.ConflictException;
import com.shop.delivery.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the 5-star + skip inline keyboard sent after order DELIVERED.
 *
 * <p>Callback data format:
 *   - {@code RATE:<orderId>:<stars>}  → persist rating, open FSM for optional comment
 *   - {@code RATE_SKIP:<orderId>}     → just remove the keyboard, no DB write
 *
 * <p>@Order(50): runs before generic command handlers but after FSM-bound text handlers.
 * Callback-data prefix is unique so order doesn't strictly matter, but explicit is better.
 */
@Component
@Order(50)
public class RatingCallbackHandler implements UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(RatingCallbackHandler.class);
    private static final String STATE_COMMENT = "CUSTOMER_RATING_COMMENT";

    private final RatingService ratingService;
    private final ConversationStateService conv;
    private final BotSender sender;

    public RatingCallbackHandler(RatingService ratingService,
                                 ConversationStateService conv,
                                 BotSender sender) {
        this.ratingService = ratingService;
        this.conv = conv;
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update u) {
        if (!u.hasCallbackQuery()) return false;
        String data = u.getCallbackQuery().getData();
        return data != null
            && (data.startsWith("RATE:") || data.startsWith("RATE_SKIP:"));
    }

    @Override
    public void handle(Update u) {
        CallbackQuery cb = u.getCallbackQuery();
        String data = cb.getData();
        Long customerId = cb.getFrom().getId();
        Long chatId = cb.getMessage().getChatId();
        Integer messageId = cb.getMessage().getMessageId();

        if (data.startsWith("RATE_SKIP:")) {
            removeKeyboard(chatId, messageId);
            sender.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(cb.getId())
                .text("Đã bỏ qua")
                .build());
            return;
        }

        // RATE:<orderId>:<stars>
        String[] parts = data.split(":");
        if (parts.length != 3) {
            answerError(cb.getId(), "Dữ liệu không hợp lệ");
            return;
        }

        UUID orderId;
        int stars;
        try {
            orderId = UUID.fromString(parts[1]);
            stars = Integer.parseInt(parts[2]);
            if (stars < 1 || stars > 5) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            log.warn("Bad RATE callback data: {}", data);
            answerError(cb.getId(), "Dữ liệu không hợp lệ");
            return;
        }

        try {
            ratingService.rate(orderId, customerId, stars, null);
        } catch (ConflictException ex) {
            sender.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(cb.getId())
                .text("Đơn đã được đánh giá rồi")
                .showAlert(true)
                .build());
            removeKeyboard(chatId, messageId);
            return;
        } catch (DomainException ex) {
            log.warn("Rating rejected for order {} customer {}: {}", orderId, customerId, ex.getMessage());
            sender.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(cb.getId())
                .text(ex.getMessage())
                .showAlert(true)
                .build());
            return;
        }

        // SUCCESS PATH
        removeKeyboard(chatId, messageId);
        sender.execute(AnswerCallbackQuery.builder()
            .callbackQueryId(cb.getId())
            .text("Cảm ơn bạn!")
            .build());

        conv.put(customerId, STATE_COMMENT, Map.of("orderId", orderId.toString()));
        sender.sendText(chatId, "Bạn có muốn nhập nhận xét? Gõ tin nhắn hoặc /skip để bỏ qua.");
    }

    private void removeKeyboard(Long chatId, Integer messageId) {
        InlineKeyboardMarkup empty = new InlineKeyboardMarkup();
        empty.setKeyboard(List.of());
        EditMessageReplyMarkup edit = EditMessageReplyMarkup.builder()
            .chatId(chatId.toString())
            .messageId(messageId)
            .replyMarkup(empty)
            .build();
        sender.execute(edit);
    }

    private void answerError(String cbId, String msg) {
        sender.execute(AnswerCallbackQuery.builder()
            .callbackQueryId(cbId)
            .text(msg)
            .showAlert(true)
            .build());
    }
}
