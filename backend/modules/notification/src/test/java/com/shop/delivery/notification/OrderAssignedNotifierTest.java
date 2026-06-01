package com.shop.delivery.notification;

import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.repository.RatingRepository;
import com.shop.delivery.delivery.service.event.OrderDeliveredEvent;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAssignedNotifierTest {

    @Mock BotSender bot;
    @Mock RatingPromptBuilder ratingPromptBuilder;
    @Mock RatingRepository ratingRepo;

    @InjectMocks OrderAssignedNotifier notifier;

    @Test
    void onOrderDelivered_notYetRated_sendsKeyboardToCustomer() {
        UUID orderId = UUID.randomUUID();
        OrderDeliveredEvent e = new OrderDeliveredEvent(
            UUID.randomUUID(), orderId, "DH001", 2001L, 1001L);

        when(ratingRepo.existsByOrderId(orderId)).thenReturn(false);
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        when(ratingPromptBuilder.build(orderId)).thenReturn(kb);

        notifier.onOrderDelivered(e);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(captor.capture());
        assertThat(captor.getValue().getChatId()).isEqualTo("1001");
        assertThat(captor.getValue().getText()).contains("DH001").contains("đánh giá");
        assertThat(captor.getValue().getReplyMarkup()).isSameAs(kb);

        // Shipper side: existing thank-you must still fire
        verify(bot).sendText(2001L, "✅ Hoàn thành đơn DH001. Chúc bạn ngày làm việc tốt lành!");
    }

    @Test
    void onOrderDelivered_alreadyRated_skipsKeyboard() {
        UUID orderId = UUID.randomUUID();
        OrderDeliveredEvent e = new OrderDeliveredEvent(
            UUID.randomUUID(), orderId, "DH002", 2001L, 1001L);

        when(ratingRepo.existsByOrderId(orderId)).thenReturn(true);

        notifier.onOrderDelivered(e);

        // No SendMessage to customer
        verify(bot, never()).execute(any(SendMessage.class));
        // But shipper thank-you still happens
        verify(bot).sendText(2001L, "✅ Hoàn thành đơn DH002. Chúc bạn ngày làm việc tốt lành!");
    }
}
