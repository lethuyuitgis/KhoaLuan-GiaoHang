package com.shop.delivery.bot.handler.shipper;

import com.shop.delivery.bot.fsm.ConversationState;
import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.fsm.ShipperRegistrationStates;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.domain.VehicleType;
import com.shop.delivery.delivery.service.ShipperProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

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
class ShipperRegistrationTextHandlerTest {

    @Mock ConversationStateService conv;
    @Mock ShipperProfileService shipperService;
    @Mock BotSender sender;

    @InjectMocks ShipperRegistrationTextHandler handler;

    private final Long userId = 5555L;
    private final Long chatId = 5555L;

    @Test
    void canHandle_falseWhenNoFsmState() {
        when(conv.get(userId)).thenReturn(Optional.empty());
        assertThat(handler.canHandle(textUpdate("hello"))).isFalse();
    }

    @Test
    void canHandle_trueWhenInShipperRegState() {
        when(conv.get(userId)).thenReturn(Optional.of(state(ShipperRegistrationStates.AWAITING_NAME, Map.of())));
        assertThat(handler.canHandle(textUpdate("Nguyễn Văn A"))).isTrue();
    }

    @Test
    void awaitingName_validInput_stashesNameAndTransitionsToPhone_withReplyKeyboard() {
        when(conv.get(userId)).thenReturn(Optional.of(state(ShipperRegistrationStates.AWAITING_NAME, new HashMap<>())));

        handler.handle(textUpdate("Nguyễn Văn A"));

        ArgumentCaptor<Map<String, Object>> dataCap = ArgumentCaptor.forClass(Map.class);
        verify(conv).put(eq(userId), eq(ShipperRegistrationStates.AWAITING_PHONE), dataCap.capture());
        assertThat(dataCap.getValue()).containsEntry(ShipperRegistrationStates.KEY_FULL_NAME, "Nguyễn Văn A");

        ArgumentCaptor<SendMessage> msgCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(sender).execute(msgCap.capture());
        SendMessage msg = msgCap.getValue();
        assertThat(msg.getText()).contains("số điện thoại");
        assertThat(msg.getReplyMarkup()).isInstanceOf(ReplyKeyboardMarkup.class);
        ReplyKeyboardMarkup kb = (ReplyKeyboardMarkup) msg.getReplyMarkup();
        assertThat(kb.getKeyboard()).isNotEmpty();
        assertThat(kb.getKeyboard().get(0).get(0).getRequestContact()).isTrue();
    }

    @Test
    void awaitingPhone_textInputIgnored_justNudges() {
        when(conv.get(userId)).thenReturn(Optional.of(state(ShipperRegistrationStates.AWAITING_PHONE, Map.of())));

        handler.handle(textUpdate("0901234567"));

        verify(conv, never()).put(anyLong(), anyString(), any());
        verify(shipperService, never()).registerPending(anyLong(), any(), any(), any());
        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(eq(chatId), textCap.capture());
        assertThat(textCap.getValue()).contains("Chia sẻ");
    }

    @Test
    void awaitingPlate_validInput_callsRegisterPending_andClearsState() {
        Map<String, Object> data = new HashMap<>();
        data.put(ShipperRegistrationStates.KEY_FULL_NAME, "Nguyễn Văn A");
        data.put(ShipperRegistrationStates.KEY_PHONE, "+84901234567");
        data.put(ShipperRegistrationStates.KEY_VEHICLE_TYPE, "MOTORBIKE");
        when(conv.get(userId)).thenReturn(Optional.of(state(ShipperRegistrationStates.AWAITING_PLATE, data)));

        handler.handle(textUpdate("29A-12345"));

        verify(shipperService).registerPending(
            eq(userId), eq(VehicleType.MOTORBIKE), eq("29A-12345"), eq("Nguyễn Văn A"));
        verify(conv).clear(userId);
        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(eq(chatId), textCap.capture());
        assertThat(textCap.getValue()).contains("chờ shop duyệt");
    }

    @Test
    void awaitingPlate_invalidInput_rejects_noServiceCall() {
        when(conv.get(userId)).thenReturn(Optional.of(state(ShipperRegistrationStates.AWAITING_PLATE,
            Map.of(ShipperRegistrationStates.KEY_VEHICLE_TYPE, "MOTORBIKE"))));

        handler.handle(textUpdate("???"));

        verify(shipperService, never()).registerPending(anyLong(), any(), any(), any());
        verify(conv, never()).clear(userId);
        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(eq(chatId), textCap.capture());
        assertThat(textCap.getValue()).contains("không hợp lệ");
    }

    @Test
    void awaitingName_slashCommandInput_isRejected_notStoredAsName() {
        // Regression: "/start" is 6 chars — it passed the 2–80 length check and
        // got saved as the shipper's full name. It must be nudged instead.
        when(conv.get(userId)).thenReturn(Optional.of(state(ShipperRegistrationStates.AWAITING_NAME, new HashMap<>())));

        handler.handle(textUpdate("/start"));

        verify(conv, never()).put(anyLong(), anyString(), any());
        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(eq(chatId), textCap.capture());
        assertThat(textCap.getValue()).contains("họ tên");
    }

    @Test
    void cancelCommand_clearsState() {
        // /cancel exits before reading state, so conv.get is unused — no stubbing needed.
        handler.handle(textUpdate("/cancel"));

        verify(conv).clear(userId);
        verify(shipperService, never()).registerPending(anyLong(), any(), any(), any());
    }

    // ---- helpers -----------------------------------------------------------

    private Update textUpdate(String text) {
        Update u = new Update();
        Message msg = new Message();
        msg.setText(text);
        User from = new User();
        from.setId(userId);
        msg.setFrom(from);
        Chat chat = new Chat();
        chat.setId(chatId);
        msg.setChat(chat);
        u.setMessage(msg);
        return u;
    }

    private ConversationState state(String name, Map<String, Object> data) {
        ConversationState s = new ConversationState();
        s.setTelegramUserId(userId);
        s.setState(name);
        s.setData(data);
        return s;
    }
}
