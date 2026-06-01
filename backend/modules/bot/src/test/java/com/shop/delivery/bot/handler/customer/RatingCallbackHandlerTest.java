package com.shop.delivery.bot.handler.customer;

import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.entity.Rating;
import com.shop.delivery.delivery.service.RatingService;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingCallbackHandlerTest {

    @Mock RatingService ratingService;
    @Mock ConversationStateService conv;
    @Mock BotSender sender;

    @InjectMocks RatingCallbackHandler handler;

    private final UUID orderId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private final Long customerId = 1001L;
    private final Long chatId = 1001L;
    private final Integer messageId = 42;

    @Test
    void canHandle_returnsTrue_forRatePrefix() {
        assertThat(handler.canHandle(callback("RATE:" + orderId + ":5"))).isTrue();
        assertThat(handler.canHandle(callback("RATE_SKIP:" + orderId))).isTrue();
    }

    @Test
    void canHandle_returnsFalse_forOtherPrefixes() {
        assertThat(handler.canHandle(callback("ACCEPT_ORDER:abc"))).isFalse();
        assertThat(handler.canHandle(callback("REJECT_ORDER:abc"))).isFalse();
        assertThat(handler.canHandle(callback("RANDOM"))).isFalse();
    }

    @Test
    void canHandle_returnsFalse_forNonCallbackUpdate() {
        Update u = new Update();
        // no callbackQuery
        assertThat(handler.canHandle(u)).isFalse();
    }

    @Test
    void handle_validRate_persistsAndOpensFsm() {
        when(ratingService.rate(orderId, customerId, 5, null))
            .thenReturn(new Rating());

        handler.handle(callback("RATE:" + orderId + ":5"));

        verify(ratingService).rate(orderId, customerId, 5, null);

        // Keyboard removed
        ArgumentCaptor<EditMessageReplyMarkup> editCap = ArgumentCaptor.forClass(EditMessageReplyMarkup.class);
        verify(sender).execute(editCap.capture());
        assertThat(editCap.getValue().getChatId()).isEqualTo(chatId.toString());
        assertThat(editCap.getValue().getMessageId()).isEqualTo(messageId);

        // FSM opened
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(conv).put(eqLong(customerId), eqStr("CUSTOMER_RATING_COMMENT"), payload.capture());
        assertThat(payload.getValue()).containsEntry("orderId", orderId.toString());

        // Thanks sent
        verify(sender).sendText(eqLong(chatId), anyString());
    }

    @Test
    void handle_skip_removesKeyboardAndAnswersCallback_noDbWrite() {
        handler.handle(callback("RATE_SKIP:" + orderId));

        verify(sender).execute(any(EditMessageReplyMarkup.class));
        verify(sender).execute(any(AnswerCallbackQuery.class));
        verify(ratingService, never()).rate(any(), anyLong(), anyInt(), any());
        verify(conv, never()).put(anyLong(), anyString(), any());
    }

    @Test
    void handle_invalidStars_answersCallbackError_noDbWrite() {
        handler.handle(callback("RATE:" + orderId + ":99"));

        verify(ratingService, never()).rate(any(), anyLong(), anyInt(), any());
        verify(sender).execute(any(AnswerCallbackQuery.class));
    }

    @Test
    void handle_malformedUuid_answersCallbackError() {
        handler.handle(callback("RATE:not-a-uuid:5"));

        verify(ratingService, never()).rate(any(), anyLong(), anyInt(), any());
        verify(sender).execute(any(AnswerCallbackQuery.class));
    }

    @Test
    void handle_alreadyRated_answersCallbackAndRemovesKeyboard() {
        when(ratingService.rate(any(), anyLong(), anyInt(), any()))
            .thenThrow(new ConflictException("ALREADY_RATED", "Đơn đã được đánh giá rồi"));

        handler.handle(callback("RATE:" + orderId + ":5"));

        verify(sender).execute(any(AnswerCallbackQuery.class));
        verify(sender).execute(any(EditMessageReplyMarkup.class));
        verify(conv, never()).put(anyLong(), anyString(), any());
    }

    @Test
    void handle_domainException_answersCallback_noFsmOpen() {
        when(ratingService.rate(any(), anyLong(), anyInt(), any()))
            .thenThrow(new BusinessRuleException("ORDER_NOT_RATEABLE", "Chỉ đánh giá được sau khi đơn đã giao"));

        handler.handle(callback("RATE:" + orderId + ":5"));

        verify(sender).execute(any(AnswerCallbackQuery.class));
        verify(conv, never()).put(anyLong(), anyString(), any());
    }

    private Update callback(String data) {
        Update u = new Update();
        CallbackQuery cb = new CallbackQuery();
        cb.setId("cb-id-1");
        cb.setData(data);
        User from = new User(); from.setId(customerId);
        cb.setFrom(from);
        Message msg = new Message();
        msg.setMessageId(messageId);
        org.telegram.telegrambots.meta.api.objects.Chat chat = new org.telegram.telegrambots.meta.api.objects.Chat();
        chat.setId(chatId);
        msg.setChat(chat);
        cb.setMessage(msg);
        u.setCallbackQuery(cb);
        return u;
    }

    private static Long eqLong(Long v) { return org.mockito.ArgumentMatchers.eq(v); }
    private static String eqStr(String v) { return org.mockito.ArgumentMatchers.eq(v); }
}
