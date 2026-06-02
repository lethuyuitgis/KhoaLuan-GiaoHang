package com.shop.delivery.bot.handler.shipper;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.UserRoleRepository;
import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.fsm.ShipperRegistrationStates;
import com.shop.delivery.bot.handler.common.StartHandler;
import com.shop.delivery.bot.sender.BotSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipperRegistrationCallbackHandlerTest {

    @Mock ConversationStateService conv;
    @Mock UserRoleRepository roleRepo;
    @Mock BotSender sender;

    @InjectMocks ShipperRegistrationCallbackHandler handler;

    @Test
    void canHandle_trueForRoleShipperCallback() {
        assertThat(handler.canHandle(callback(StartHandler.CALLBACK_ROLE_SHIPPER))).isTrue();
    }

    @Test
    void canHandle_falseForOtherCallbacks() {
        assertThat(handler.canHandle(callback("ROLE:CUSTOMER"))).isFalse();
        assertThat(handler.canHandle(callback("RATE:abc"))).isFalse();
    }

    @Test
    void handle_happyPath_opensFsmAndPromptsName() {
        when(roleRepo.findByTelegramUserIdAndRole(5555L, Role.SHIPPER)).thenReturn(Optional.empty());

        handler.handle(callback(StartHandler.CALLBACK_ROLE_SHIPPER));

        // FSM state set to AWAITING_NAME
        ArgumentCaptor<Map<String, Object>> dataCap = ArgumentCaptor.forClass(Map.class);
        verify(conv).put(eq(5555L), eq(ShipperRegistrationStates.AWAITING_NAME), dataCap.capture());

        // Acknowledged callback + sent the "what's your name?" prompt
        verify(sender).execute(any(AnswerCallbackQuery.class));
        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(eq(5555L), textCap.capture());
        assertThat(textCap.getValue()).contains("tên");
    }

    @Test
    void handle_duplicateRegistration_doesNotOpenFsm() {
        UserRole existing = new UserRole();
        existing.setRole(Role.SHIPPER);
        when(roleRepo.findByTelegramUserIdAndRole(5555L, Role.SHIPPER)).thenReturn(Optional.of(existing));

        handler.handle(callback(StartHandler.CALLBACK_ROLE_SHIPPER));

        verify(conv, never()).put(anyLong(), anyString(), any());
        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(eq(5555L), textCap.capture());
        assertThat(textCap.getValue()).contains("đã đăng ký");
    }

    private static Long eq(Long v) { return org.mockito.ArgumentMatchers.eq(v); }
    private static String eq(String v) { return org.mockito.ArgumentMatchers.eq(v); }

    private Update callback(String data) {
        Update u = new Update();
        CallbackQuery cb = new CallbackQuery();
        cb.setId("cb-1");
        cb.setData(data);
        User from = new User();
        from.setId(5555L);
        cb.setFrom(from);
        Message msg = new Message();
        msg.setMessageId(7);
        Chat chat = new Chat();
        chat.setId(5555L);
        msg.setChat(chat);
        cb.setMessage(msg);
        u.setCallbackQuery(cb);
        return u;
    }
}
