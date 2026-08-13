package com.shop.delivery.notification;

import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.shared.event.CustomerChatSentEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomerChatNotifierTest {

    @Mock BotSender bot;
    @InjectMocks CustomerChatNotifier notifier;

    @Test
    void relaysCustomerMessageToShipperWithReplyButton() {
        UUID assignmentId = UUID.randomUUID();
        notifier.onCustomerChat(new CustomerChatSentEvent(assignmentId, 2001L, "shipper ơi tới chưa"));

        ArgumentCaptor<SendMessage> cap = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(cap.capture());
        SendMessage msg = cap.getValue();
        assertThat(msg.getChatId()).isEqualTo("2001");
        assertThat(msg.getText()).isEqualTo("🧑 Khách: shipper ơi tới chưa");
        assertThat(msg.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);
        InlineKeyboardMarkup kb = (InlineKeyboardMarkup) msg.getReplyMarkup();
        assertThat(kb.getKeyboard().get(0).get(0).getCallbackData()).isEqualTo("CHAT_OPEN:" + assignmentId);
    }
}
