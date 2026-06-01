package com.shop.delivery.bot.handler.customer;

import com.shop.delivery.bot.fsm.ConversationState;
import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.service.RatingService;
import com.shop.delivery.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;
import java.util.UUID;

/**
 * FSM-state-gated text handler. canHandle returns true only when the sender is
 * in {@code CUSTOMER_RATING_COMMENT} state. Captures one text message as the
 * rating comment, or accepts {@code /skip} to abort the comment capture.
 *
 * <p>@Order(0): MUST run before command/free-text handlers so a user typing
 * "/help" while in this state doesn't fall through to the help handler.
 * (Confirmed by ConversationStateServiceIT — Spring honors @Order on injected
 * {@code List<UpdateHandler>}.)
 */
@Component
@Order(0)
public class RatingCommentHandler implements UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(RatingCommentHandler.class);
    private static final String STATE = "CUSTOMER_RATING_COMMENT";

    private final ConversationStateService conv;
    private final RatingService ratingService;
    private final BotSender sender;

    public RatingCommentHandler(ConversationStateService conv,
                                RatingService ratingService,
                                BotSender sender) {
        this.conv = conv;
        this.ratingService = ratingService;
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update u) {
        if (u == null || u.getMessage() == null || u.getMessage().getText() == null) return false;
        if (u.getMessage().getFrom() == null) return false;
        Long userId = u.getMessage().getFrom().getId();
        return conv.get(userId)
            .map(s -> STATE.equals(s.getState()))
            .orElse(false);
    }

    @Override
    public void handle(Update u) {
        Long userId = u.getMessage().getFrom().getId();
        Long chatId = u.getMessage().getChatId();
        String raw = u.getMessage().getText();
        String text = raw == null ? "" : raw.trim();

        if ("/skip".equalsIgnoreCase(text)) {
            conv.clear(userId);
            sender.sendText(chatId, "Đã ghi nhận đánh giá. Cảm ơn bạn!");
            return;
        }

        if (text.isEmpty()) {
            // No comment provided — close FSM, don't bother service.
            conv.clear(userId);
            return;
        }

        Optional<ConversationState> stateOpt = conv.get(userId);
        if (stateOpt.isEmpty()) {
            // Defensive: canHandle said yes but the state vanished — bail.
            return;
        }
        Object orderIdRaw = stateOpt.get().getData() == null ? null : stateOpt.get().getData().get("orderId");
        if (!(orderIdRaw instanceof String s)) {
            log.warn("FSM state missing orderId for user {}", userId);
            conv.clear(userId);
            return;
        }

        try {
            UUID orderId = UUID.fromString(s);
            ratingService.updateComment(orderId, userId, text);
            conv.clear(userId);
            sender.sendText(chatId, "Cảm ơn nhận xét của bạn!");
        } catch (IllegalArgumentException ex) {
            log.warn("FSM orderId not a UUID for user {}: {}", userId, s);
            conv.clear(userId);
        } catch (DomainException ex) {
            log.warn("updateComment rejected for user {}: {}", userId, ex.getMessage());
            sender.sendText(chatId, "Không thể cập nhật nhận xét: " + ex.getMessage());
            conv.clear(userId);
        }
    }
}
