package com.shop.delivery.notification;

import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.service.event.OrderAcceptedEvent;
import com.shop.delivery.delivery.service.event.OrderAssignedEvent;
import com.shop.delivery.delivery.service.event.OrderDeliveredEvent;
import com.shop.delivery.delivery.service.event.OrderRejectedEvent;
import com.shop.delivery.delivery.service.event.OrderStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

/**
 * Listener that pushes Telegram notifications when assignments change state.
 * @TransactionalEventListener(AFTER_COMMIT) ensures we don't notify if DB rolled back.
 */
@Component
public class OrderAssignedNotifier {

    private static final Logger log = LoggerFactory.getLogger(OrderAssignedNotifier.class);

    private final BotSender bot;

    public OrderAssignedNotifier(BotSender bot) {
        this.bot = bot;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderAssigned(OrderAssignedEvent e) {
        String text = String.format(
            "📦 Đơn mới %s\n📍 Giao: %s\n📏 Khoảng cách: %s km\n💰 Phí ship: %s đ",
            e.orderCode(), e.deliveryAddress(), e.distanceKm(), e.deliveryFee()
        );

        InlineKeyboardButton acceptBtn = InlineKeyboardButton.builder()
            .text("✅ Nhận")
            .callbackData("ACCEPT_ORDER:" + e.assignmentId())
            .build();
        InlineKeyboardButton rejectBtn = InlineKeyboardButton.builder()
            .text("❌ Từ chối")
            .callbackData("REJECT_ORDER:" + e.assignmentId())
            .build();

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(List.of(List.of(acceptBtn, rejectBtn)));

        SendMessage msg = SendMessage.builder()
            .chatId(e.shipperId())
            .text(text)
            .replyMarkup(keyboard)
            .build();

        bot.execute(msg);
        log.info("Pushed offer to shipper {} for order {}", e.shipperId(), e.orderCode());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderAccepted(OrderAcceptedEvent e) {
        bot.sendText(e.shipperId(), "✅ Bạn đã nhận đơn " + e.orderCode() + ". Mở Mini App để bắt đầu giao.");
        bot.sendText(e.customerId(), "🚴 Shipper đã nhận đơn " + e.orderCode() + ". Đơn sắp được giao.");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderRejected(OrderRejectedEvent e) {
        bot.sendText(e.shipperId(), "❌ Bạn đã từ chối đơn " + e.orderCode() + ".");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStarted(OrderStartedEvent e) {
        bot.sendText(e.customerId(), "🚀 Đơn " + e.orderCode() + " đang được giao. Shipper đang trên đường tới bạn.");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderDelivered(OrderDeliveredEvent e) {
        bot.sendText(e.customerId(), "✅ Đơn " + e.orderCode() + " đã giao xong. Cảm ơn bạn đã mua hàng!");
        bot.sendText(e.shipperId(), "✅ Hoàn thành đơn " + e.orderCode() + ". Chúc bạn ngày làm việc tốt lành!");
    }
}
