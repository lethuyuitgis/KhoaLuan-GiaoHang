package com.shop.delivery.notification;

import com.shop.delivery.bot.fsm.ChatStates;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.repository.RatingRepository;
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
    private final RatingPromptBuilder ratingPromptBuilder;
    private final RatingRepository ratingRepo;

    public OrderAssignedNotifier(BotSender bot,
                                 RatingPromptBuilder ratingPromptBuilder,
                                 RatingRepository ratingRepo) {
        this.bot = bot;
        this.ratingPromptBuilder = ratingPromptBuilder;
        this.ratingRepo = ratingRepo;
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
        // Offer both parties a one-tap entry into anonymous chat for this delivery.
        bot.execute(withChatButton(e.shipperId(), e.assignmentId(),
            "✅ Bạn đã nhận đơn " + e.orderCode() + ". Mở Mini App để bắt đầu giao."));
        bot.execute(withChatButton(e.customerId(), e.assignmentId(),
            "🚴 Shipper đã nhận đơn " + e.orderCode() + ". Đơn sắp được giao."));
    }

    private SendMessage withChatButton(Long chatId, java.util.UUID assignmentId, String text) {
        InlineKeyboardButton chatBtn = InlineKeyboardButton.builder()
            .text("💬 Chat")
            .callbackData(ChatStates.CALLBACK_CHAT_OPEN + assignmentId)
            .build();
        return SendMessage.builder()
            .chatId(chatId)
            .text(text)
            .replyMarkup(InlineKeyboardMarkup.builder().keyboard(List.of(List.of(chatBtn))).build())
            .build();
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
        // 1. Always notify the shipper (unchanged behavior).
        bot.sendText(e.shipperId(), "✅ Hoàn thành đơn " + e.orderCode() + ". Chúc bạn ngày làm việc tốt lành!");

        // 2. Send the customer a rating prompt — but ONLY if not already rated.
        //    Defence-in-depth: AFTER_COMMIT could in theory fire twice (it shouldn't,
        //    but a manual replay or test harness might trigger it). The UNIQUE constraint
        //    on rating.order_id is the ultimate guard; this check just keeps the UX clean.
        if (ratingRepo.existsByOrderId(e.orderId())) {
            log.debug("Order {} already rated — skipping rating prompt", e.orderId());
            return;
        }

        InlineKeyboardMarkup kb = ratingPromptBuilder.build(e.orderId());
        SendMessage msg = SendMessage.builder()
            .chatId(String.valueOf(e.customerId()))
            .text("✅ Đơn " + e.orderCode() + " đã giao xong.\n⭐ Hãy đánh giá shipper:")
            .replyMarkup(kb)
            .build();
        bot.execute(msg);
        log.info("Sent rating prompt to customer {} for order {}", e.customerId(), e.orderCode());
    }
}
