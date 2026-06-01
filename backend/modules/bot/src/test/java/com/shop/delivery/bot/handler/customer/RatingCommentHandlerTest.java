package com.shop.delivery.bot.handler.customer;

import com.shop.delivery.bot.fsm.ConversationState;
import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.service.RatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RatingCommentHandlerTest {

    @Mock ConversationStateService conv;
    @Mock RatingService ratingService;
    @Mock BotSender sender;

    @InjectMocks RatingCommentHandler handler;

    private final Long customerId = 1001L;
    private final UUID orderId = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void canHandle_returnsFalse_whenNoMessage() {
        Update u = new Update();
        assertThat(handler.canHandle(u)).isFalse();
    }

    @Test
    void canHandle_returnsFalse_whenNoFsmState() {
        when(conv.get(customerId)).thenReturn(Optional.empty());
        assertThat(handler.canHandle(textUpdate("hello"))).isFalse();
    }

    @Test
    void canHandle_returnsFalse_whenWrongFsmState() {
        when(conv.get(customerId)).thenReturn(Optional.of(stateOf("OTHER_STATE")));
        assertThat(handler.canHandle(textUpdate("hello"))).isFalse();
    }

    @Test
    void canHandle_returnsTrue_whenInRatingCommentState() {
        when(conv.get(customerId)).thenReturn(Optional.of(stateOf("CUSTOMER_RATING_COMMENT")));
        assertThat(handler.canHandle(textUpdate("Giao nhanh"))).isTrue();
    }

    @Test
    void handle_skipCommand_clearsFsmAndThanks() {
        when(conv.get(customerId)).thenReturn(Optional.of(stateOf("CUSTOMER_RATING_COMMENT")));

        handler.handle(textUpdate("/skip"));

        verify(conv).clear(customerId);
        verify(ratingService, never()).updateComment(any(), anyLong(), anyString());
        verify(sender).sendText(customerId, "Đã ghi nhận đánh giá. Cảm ơn bạn!");
    }

    @Test
    void handle_skipCommand_isCaseInsensitive() {
        when(conv.get(customerId)).thenReturn(Optional.of(stateOf("CUSTOMER_RATING_COMMENT")));
        handler.handle(textUpdate("/SKIP"));
        verify(conv).clear(customerId);
        verify(ratingService, never()).updateComment(any(), anyLong(), anyString());
    }

    @Test
    void handle_freeText_persistsCommentClearsFsm() {
        when(conv.get(customerId)).thenReturn(Optional.of(stateOf("CUSTOMER_RATING_COMMENT")));

        handler.handle(textUpdate("Giao nhanh, thái độ tốt"));

        verify(ratingService).updateComment(orderId, customerId, "Giao nhanh, thái độ tốt");
        verify(conv).clear(customerId);
        verify(sender).sendText(customerId, "Cảm ơn nhận xét của bạn!");
    }

    @Test
    void handle_trimsWhitespace() {
        when(conv.get(customerId)).thenReturn(Optional.of(stateOf("CUSTOMER_RATING_COMMENT")));

        handler.handle(textUpdate("   ok   "));

        verify(ratingService).updateComment(orderId, customerId, "ok");
    }

    @Test
    void handle_emptyTextAfterTrim_doesNotCallService() {
        when(conv.get(customerId)).thenReturn(Optional.of(stateOf("CUSTOMER_RATING_COMMENT")));

        handler.handle(textUpdate("    "));

        verify(ratingService, never()).updateComment(any(), anyLong(), anyString());
        // FSM should still close — nothing to capture
        verify(conv).clear(customerId);
    }

    // ---- helpers ----

    private Update textUpdate(String text) {
        Update u = new Update();
        Message msg = new Message();
        msg.setText(text);
        User from = new User(); from.setId(customerId);
        msg.setFrom(from);
        Chat chat = new Chat(); chat.setId(customerId);
        msg.setChat(chat);
        u.setMessage(msg);
        return u;
    }

    private ConversationState stateOf(String name) {
        ConversationState s = new ConversationState();
        s.setTelegramUserId(customerId);
        s.setState(name);
        s.setData(Map.of("orderId", orderId.toString()));
        return s;
    }
}
