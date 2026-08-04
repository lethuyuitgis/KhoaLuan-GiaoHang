package com.shop.delivery.bot.handler.chat;

import com.shop.delivery.bot.fsm.ChatStates;
import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.service.ChatParticipants;
import com.shop.delivery.delivery.service.ChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatOpenCallbackHandlerTest {

    @Mock ConversationStateService conv;
    @Mock ChatService chatService;
    @Mock BotSender sender;
    @InjectMocks ChatOpenCallbackHandler handler;

    private final Long customerId = 100L;
    private final Long shipperId = 200L;
    private final UUID assignmentId = UUID.randomUUID();

    private Update callback(Long userId, String data) {
        Update u = new Update();
        CallbackQuery cb = new CallbackQuery();
        cb.setId("cb-1");
        cb.setData(data);
        User from = new User();
        from.setId(userId);
        cb.setFrom(from);
        Message msg = new Message();
        msg.setMessageId(7);
        Chat chat = new Chat();
        chat.setId(userId);
        msg.setChat(chat);
        cb.setMessage(msg);
        u.setCallbackQuery(cb);
        return u;
    }

    private ChatParticipants participants(boolean active) {
        return new ChatParticipants(assignmentId, UUID.randomUUID(), customerId, shipperId, active);
    }

    @Test
    void opensChatForAParticipantOfAnActiveDelivery() {
        when(chatService.participants(assignmentId)).thenReturn(Optional.of(participants(true)));

        handler.handle(callback(customerId, ChatStates.CALLBACK_CHAT_OPEN + assignmentId));

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(conv).put(eq(customerId), eq(ChatStates.CHAT_ACTIVE), cap.capture());
        assertThat(cap.getValue()).containsEntry(ChatStates.KEY_ASSIGNMENT_ID, assignmentId.toString());
        assertThat(cap.getValue()).containsEntry(ChatStates.KEY_ROLE, "CUSTOMER");
    }

    @Test
    void refusesSomeoneNotOnTheOrder() {
        when(chatService.participants(assignmentId)).thenReturn(Optional.of(participants(true)));

        handler.handle(callback(999L, ChatStates.CALLBACK_CHAT_OPEN + assignmentId));

        verify(conv, never()).put(anyLong(), anyString(), any());
    }

    @Test
    void refusesWhenDeliveryFinished() {
        when(chatService.participants(assignmentId)).thenReturn(Optional.of(participants(false)));

        handler.handle(callback(shipperId, ChatStates.CALLBACK_CHAT_OPEN + assignmentId));

        verify(conv, never()).put(anyLong(), anyString(), any());
    }

    @Test
    void handlesInvalidAssignmentIdGracefully() {
        handler.handle(callback(customerId, ChatStates.CALLBACK_CHAT_OPEN + "not-a-uuid"));
        verify(conv, never()).put(anyLong(), anyString(), any());
    }
}
