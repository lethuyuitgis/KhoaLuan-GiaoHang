package com.shop.delivery.bot.handler.shipper;

import com.shop.delivery.auth.service.TelegramUserService;
import com.shop.delivery.bot.fsm.ConversationState;
import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.fsm.ShipperRegistrationStates;
import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles the {@code contact} payload Telegram sends when a user taps a
 * {@code request_contact} reply button. Only active during
 * {@link ShipperRegistrationStates#AWAITING_PHONE}.
 *
 * <p>Action:
 * <ol>
 *   <li>Persist phone to {@code telegram_user.phone} (via
 *       {@link TelegramUserService#updatePhone}).</li>
 *   <li>Stash phone in FSM payload for downstream use.</li>
 *   <li>Transition state to {@code AWAITING_VEHICLE}.</li>
 *   <li>Send inline keyboard for vehicle selection AND remove the reply
 *       keyboard ({@link ReplyKeyboardRemove}) — Telegram requires this so the
 *       user doesn't see both keyboards at once.</li>
 * </ol>
 */
@Component
@Order(1)
public class ShipperRegistrationContactHandler implements UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(ShipperRegistrationContactHandler.class);

    private final ConversationStateService conv;
    private final TelegramUserService userService;
    private final BotSender sender;

    public ShipperRegistrationContactHandler(ConversationStateService conv,
                                             TelegramUserService userService,
                                             BotSender sender) {
        this.conv = conv;
        this.userService = userService;
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update u) {
        if (u == null || u.getMessage() == null) return false;
        if (u.getMessage().getContact() == null) return false;
        if (u.getMessage().getFrom() == null) return false;
        Long userId = u.getMessage().getFrom().getId();
        return conv.get(userId)
            .map(s -> ShipperRegistrationStates.AWAITING_PHONE.equals(s.getState()))
            .orElse(false);
    }

    @Override
    public void handle(Update u) {
        Long userId = u.getMessage().getFrom().getId();
        Long chatId = u.getMessage().getChatId();
        Contact contact = u.getMessage().getContact();
        String phone = contact.getPhoneNumber();
        if (phone == null || phone.isBlank()) {
            sender.sendText(chatId, "Không nhận được số điện thoại. Vui lòng thử lại.");
            return;
        }

        userService.updatePhone(userId, phone);

        ConversationState state = conv.get(userId).orElse(null);
        if (state == null) {
            // Race: state vanished between canHandle and handle. Just exit.
            return;
        }
        Map<String, Object> data = state.getData() != null ? new HashMap<>(state.getData()) : new HashMap<>();
        data.put(ShipperRegistrationStates.KEY_PHONE, phone);
        conv.put(userId, ShipperRegistrationStates.AWAITING_VEHICLE, data);

        // Hide the reply keyboard (one-time, but explicit removal is reliable).
        SendMessage ackHide = SendMessage.builder()
            .chatId(chatId)
            .text("Đã nhận số điện thoại: " + phone)
            .replyMarkup(ReplyKeyboardRemove.builder().removeKeyboard(true).build())
            .build();
        sender.execute(ackHide);

        // Then send the inline keyboard for vehicle pick.
        SendMessage vehiclePick = SendMessage.builder()
            .chatId(chatId)
            .text("Bạn dùng loại phương tiện nào?")
            .replyMarkup(ShipperRegistrationTextHandler.vehicleKeyboard())
            .build();
        sender.execute(vehiclePick);

        log.info("Shipper FSM phone captured userId={} → AWAITING_VEHICLE", userId);
    }
}
