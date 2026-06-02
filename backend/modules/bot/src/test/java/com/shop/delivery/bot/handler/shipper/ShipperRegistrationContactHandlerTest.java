package com.shop.delivery.bot.handler.shipper;

import com.shop.delivery.auth.service.TelegramUserService;
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
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;

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
class ShipperRegistrationContactHandlerTest {

    @Mock ConversationStateService conv;
    @Mock TelegramUserService userService;
    @Mock BotSender sender;

    @InjectMocks ShipperRegistrationContactHandler handler;

    private final Long userId = 5555L;
    private final Long chatId = 5555L;

    @Test
    void canHandle_trueWhenContactAndAwaitingPhone() {
        when(conv.get(userId)).thenReturn(Optional.of(state(ShipperRegistrationStates.AWAITING_PHONE)));
        assertThat(handler.canHandle(contactUpdate("+84901234567"))).isTrue();
    }

    @Test
    void canHandle_falseWhenStateMismatch() {
        when(conv.get(userId)).thenReturn(Optional.of(state(ShipperRegistrationStates.AWAITING_NAME)));
        assertThat(handler.canHandle(contactUpdate("+84901234567"))).isFalse();
    }

    @Test
    void canHandle_falseWhenNoContactAttached() {
        Update u = new Update();
        Message msg = new Message();
        User from = new User(); from.setId(userId); msg.setFrom(from);
        u.setMessage(msg);
        assertThat(handler.canHandle(u)).isFalse();
    }

    @Test
    void handle_happyPath_savesPhone_transitionsState_hidesAndShowsKeyboards() {
        Map<String, Object> data = new HashMap<>();
        data.put(ShipperRegistrationStates.KEY_FULL_NAME, "Nguyễn Văn A");
        when(conv.get(userId)).thenReturn(Optional.of(state(ShipperRegistrationStates.AWAITING_PHONE, data)));

        handler.handle(contactUpdate("+84901234567"));

        verify(userService).updatePhone(userId, "+84901234567");

        ArgumentCaptor<Map<String, Object>> dataCap = ArgumentCaptor.forClass(Map.class);
        verify(conv).put(eq(userId), eq(ShipperRegistrationStates.AWAITING_VEHICLE), dataCap.capture());
        assertThat(dataCap.getValue()).containsEntry(ShipperRegistrationStates.KEY_PHONE, "+84901234567");
        assertThat(dataCap.getValue()).containsEntry(ShipperRegistrationStates.KEY_FULL_NAME, "Nguyễn Văn A");

        // Two execute() calls: one with ReplyKeyboardRemove ack, one with vehicle inline keyboard
        ArgumentCaptor<SendMessage> msgCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(sender, org.mockito.Mockito.times(2)).execute(msgCap.capture());
        SendMessage first = msgCap.getAllValues().get(0);
        assertThat(first.getReplyMarkup()).isInstanceOf(ReplyKeyboardRemove.class);
        assertThat(first.getText()).contains("số điện thoại");
        SendMessage second = msgCap.getAllValues().get(1);
        assertThat(second.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);
        assertThat(second.getText()).contains("phương tiện");
    }

    @Test
    void handle_blankPhone_notifiesUser_noStateChange() {
        // Blank phone exits before conv.get is read — no stubbing needed.
        handler.handle(contactUpdate(""));

        verify(userService, never()).updatePhone(anyLong(), anyString());
        verify(conv, never()).put(anyLong(), anyString(), any());
        verify(sender).sendText(eq(chatId), anyString());
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

    private Update contactUpdate(String phone) {
        Update u = new Update();
        Message msg = new Message();
        User from = new User(); from.setId(userId); msg.setFrom(from);
        Chat chat = new Chat(); chat.setId(chatId); msg.setChat(chat);
        Contact contact = new Contact();
        contact.setPhoneNumber(phone);
        contact.setUserId(userId);
        msg.setContact(contact);
        u.setMessage(msg);
        return u;
    }
}
