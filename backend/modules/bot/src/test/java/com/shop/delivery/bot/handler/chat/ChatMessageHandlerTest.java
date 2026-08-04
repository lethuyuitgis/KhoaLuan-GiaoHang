package com.shop.delivery.bot.handler.chat;

import com.shop.delivery.bot.fsm.ChatStates;
import com.shop.delivery.bot.fsm.ConversationState;
import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.domain.ChatRole;
import com.shop.delivery.delivery.service.ChatParticipants;
import com.shop.delivery.delivery.service.ChatService;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageHandlerTest {

    @Mock ConversationStateService conv;
    @Mock ChatService chatService;
    @Mock BotSender sender;
    @InjectMocks ChatMessageHandler handler;

    private final Long customerId = 100L;
    private final Long shipperId = 200L;
    private final UUID assignmentId = UUID.randomUUID();

    private Update textFrom(Long userId, String text) {
        Update u = new Update();
        Message m = new Message();
        m.setText(text);
        User from = new User();
        from.setId(userId);
        m.setFrom(from);
        Chat chat = new Chat();
        chat.setId(userId);
        m.setChat(chat);
        u.setMessage(m);
        return u;
    }

    private ConversationState chatState(ChatRole role) {
        ConversationState s = new ConversationState();
        s.setState(ChatStates.CHAT_ACTIVE);
        Map<String, Object> data = new HashMap<>();
        data.put(ChatStates.KEY_ASSIGNMENT_ID, assignmentId.toString());
        data.put(ChatStates.KEY_ROLE, role.name());
        s.setData(data);
        return s;
    }

    private ChatParticipants participants(boolean active) {
        return new ChatParticipants(assignmentId, UUID.randomUUID(), customerId, shipperId, active);
    }

    @Test
    void canHandle_trueForPlainTextWhileChatting() {
        when(conv.get(customerId)).thenReturn(Optional.of(chatState(ChatRole.CUSTOMER)));
        assertThat(handler.canHandle(textFrom(customerId, "chào bạn"))).isTrue();
    }

    @Test
    void canHandle_trueForExitCommand() {
        when(conv.get(customerId)).thenReturn(Optional.of(chatState(ChatRole.CUSTOMER)));
        assertThat(handler.canHandle(textFrom(customerId, "/thoat"))).isTrue();
    }

    @Test
    void canHandle_falseForOtherCommand_soStartStillWorks() {
        when(conv.get(customerId)).thenReturn(Optional.of(chatState(ChatRole.CUSTOMER)));
        assertThat(handler.canHandle(textFrom(customerId, "/start"))).isFalse();
    }

    @Test
    void canHandle_falseWhenNotChatting() {
        when(conv.get(customerId)).thenReturn(Optional.empty());
        assertThat(handler.canHandle(textFrom(customerId, "hello"))).isFalse();
    }

    @Test
    void relaysCustomerMessageToShipperWithRolePrefix() {
        when(conv.get(customerId)).thenReturn(Optional.of(chatState(ChatRole.CUSTOMER)));
        when(chatService.participants(assignmentId)).thenReturn(Optional.of(participants(true)));
        when(conv.get(shipperId)).thenReturn(Optional.empty()); // peer not chatting → reply button

        handler.handle(textFrom(customerId, "shipper ơi tới chưa"));

        verify(chatService).record(eq(assignmentId), eq(ChatRole.CUSTOMER), eq(customerId), eq("shipper ơi tới chưa"));
        ArgumentCaptor<SendMessage> cap = ArgumentCaptor.forClass(SendMessage.class);
        verify(sender).execute(cap.capture());
        assertThat(cap.getValue().getChatId()).isEqualTo(shipperId.toString());
        assertThat(cap.getValue().getText()).isEqualTo("🧑 Khách: shipper ơi tới chưa");
        assertThat(cap.getValue().getReplyMarkup()).isNotNull(); // "Trả lời" button
    }

    @Test
    void exitCommandClearsStateAndConfirms() {
        // /thoat exits before reading FSM state — no conv.get stub needed.
        handler.handle(textFrom(customerId, "/thoat"));

        verify(conv).clear(customerId);
        verify(chatService, never()).record(any(), any(), anyLong(), anyString());
        verify(sender).sendText(eq(customerId), anyString());
    }

    @Test
    void blankMessageIsNotRelayed() {
        handler.handle(textFrom(customerId, "   "));
        verify(chatService, never()).record(any(), any(), anyLong(), anyString());
        verify(sender, never()).execute(any());
    }

    @Test
    void closedDeliveryClosesChatInsteadOfRelaying() {
        when(conv.get(customerId)).thenReturn(Optional.of(chatState(ChatRole.CUSTOMER)));
        when(chatService.participants(assignmentId)).thenReturn(Optional.of(participants(false)));

        handler.handle(textFrom(customerId, "còn đó không"));

        verify(conv).clear(customerId);
        verify(chatService, never()).record(any(), any(), anyLong(), anyString());
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(eq(customerId), text.capture());
        assertThat(text.getValue()).contains("đã đóng");
    }
}
