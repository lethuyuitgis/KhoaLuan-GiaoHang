package com.shop.delivery.bot.handler.shipper;

import com.shop.delivery.bot.fsm.ConversationState;
import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.fsm.ShipperRegistrationStates;
import com.shop.delivery.bot.sender.BotSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipperRegistrationVehicleCallbackHandlerTest {

    @Mock ConversationStateService conv;
    @Mock BotSender sender;

    @InjectMocks ShipperRegistrationVehicleCallbackHandler handler;

    private final Long userId = 5555L;
    private final Long chatId = 5555L;

    @Test
    void canHandle_trueWhenVehicleCallbackAndAwaitingVehicleState() {
        when(conv.get(userId)).thenReturn(Optional.of(state(ShipperRegistrationStates.AWAITING_VEHICLE)));
        assertThat(handler.canHandle(callback("VEHICLE:MOTORBIKE"))).isTrue();
    }

    @Test
    void canHandle_falseWhenStateMismatch() {
        when(conv.get(userId)).thenReturn(Optional.of(state(ShipperRegistrationStates.AWAITING_NAME)));
        assertThat(handler.canHandle(callback("VEHICLE:CAR"))).isFalse();
    }

    @Test
    void canHandle_falseForNonVehiclePrefix() {
        assertThat(handler.canHandle(callback("RATE:abc"))).isFalse();
    }

    @Test
    void handle_happyPath_transitionsToPlateAndEditsMessage() {
        Map<String, Object> data = new HashMap<>();
        data.put(ShipperRegistrationStates.KEY_FULL_NAME, "Nguyễn Văn A");
        when(conv.get(userId)).thenReturn(Optional.of(state(ShipperRegistrationStates.AWAITING_VEHICLE, data)));

        handler.handle(callback("VEHICLE:CAR"));

        ArgumentCaptor<Map<String, Object>> dataCap = ArgumentCaptor.forClass(Map.class);
        verify(conv).put(eq(userId), eq(ShipperRegistrationStates.AWAITING_PLATE), dataCap.capture());
        assertThat(dataCap.getValue()).containsEntry(ShipperRegistrationStates.KEY_VEHICLE_TYPE, "CAR");

        verify(sender).execute(any(AnswerCallbackQuery.class));
        ArgumentCaptor<EditMessageText> editCap = ArgumentCaptor.forClass(EditMessageText.class);
        verify(sender).execute(editCap.capture());
        assertThat(editCap.getValue().getText()).contains("biển số");
    }

    @Test
    void handle_unknownVehicle_errorsCallback_noStateChange() {
        // Unknown vehicle exits before conv.get → no stubbing needed.
        handler.handle(callback("VEHICLE:HELICOPTER"));

        verify(conv, never()).put(anyLong(), anyString(), any());
        verify(sender).execute(any(AnswerCallbackQuery.class));
    }

    // ---- helpers ----

    private ConversationState state(String name) { return state(name, new HashMap<>()); }
    private ConversationState state(String name, Map<String, Object> data) {
        ConversationState s = new ConversationState();
        s.setTelegramUserId(userId);
        s.setState(name);
        s.setData(data);
        return s;
    }

    private Update callback(String data) {
        Update u = new Update();
        CallbackQuery cb = new CallbackQuery();
        cb.setId("cb-vh");
        cb.setData(data);
        User from = new User(); from.setId(userId);
        cb.setFrom(from);
        Message msg = new Message();
        msg.setMessageId(11);
        Chat chat = new Chat(); chat.setId(chatId);
        msg.setChat(chat);
        cb.setMessage(msg);
        u.setCallbackQuery(cb);
        return u;
    }
}
