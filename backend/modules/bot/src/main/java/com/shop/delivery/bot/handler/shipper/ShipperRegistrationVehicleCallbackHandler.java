package com.shop.delivery.bot.handler.shipper;

import com.shop.delivery.bot.fsm.ConversationState;
import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.fsm.ShipperRegistrationStates;
import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.domain.VehicleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles the {@code VEHICLE:*} callbacks emitted by the inline keyboard built
 * in {@link ShipperRegistrationTextHandler#vehicleKeyboard()}. Only active
 * during {@link ShipperRegistrationStates#AWAITING_VEHICLE}.
 *
 * <p>Edits the original message in-place to replace the keyboard with a
 * confirmation line + prompt for plate, then transitions FSM to
 * {@code AWAITING_PLATE}.
 */
@Component
@Order(40)
public class ShipperRegistrationVehicleCallbackHandler implements UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(ShipperRegistrationVehicleCallbackHandler.class);

    private static final String PREFIX = "VEHICLE:";

    private static final Map<String, String> LABELS = Map.of(
        "MOTORBIKE", "🏍️ Xe máy",
        "CAR",       "🚗 Ô tô",
        "BICYCLE",   "🚲 Xe đạp"
    );

    private final ConversationStateService conv;
    private final BotSender sender;

    public ShipperRegistrationVehicleCallbackHandler(ConversationStateService conv,
                                                     BotSender sender) {
        this.conv = conv;
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update u) {
        if (!u.hasCallbackQuery()) return false;
        String data = u.getCallbackQuery().getData();
        if (data == null || !data.startsWith(PREFIX)) return false;
        Long userId = u.getCallbackQuery().getFrom().getId();
        return conv.get(userId)
            .map(s -> ShipperRegistrationStates.AWAITING_VEHICLE.equals(s.getState()))
            .orElse(false);
    }

    @Override
    public void handle(Update u) {
        CallbackQuery cb = u.getCallbackQuery();
        Long userId = cb.getFrom().getId();
        Long chatId = cb.getMessage().getChatId();
        Integer messageId = cb.getMessage().getMessageId();
        String code = cb.getData().substring(PREFIX.length());

        VehicleType type;
        try {
            type = VehicleType.valueOf(code);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown VEHICLE callback {}", cb.getData());
            sender.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(cb.getId())
                .text("Lựa chọn không hợp lệ")
                .showAlert(true)
                .build());
            return;
        }

        ConversationState state = conv.get(userId).orElse(null);
        if (state == null) {
            // State expired between canHandle and handle; just bail.
            return;
        }
        Map<String, Object> data = state.getData() != null ? new HashMap<>(state.getData()) : new HashMap<>();
        data.put(ShipperRegistrationStates.KEY_VEHICLE_TYPE, type.name());
        conv.put(userId, ShipperRegistrationStates.AWAITING_PLATE, data);

        String label = LABELS.getOrDefault(type.name(), type.name());
        sender.execute(AnswerCallbackQuery.builder()
            .callbackQueryId(cb.getId())
            .text("Đã chọn: " + label)
            .build());

        // Replace the keyboard message with confirmation + plate prompt.
        EditMessageText edit = EditMessageText.builder()
            .chatId(chatId.toString())
            .messageId(messageId)
            .text("Đã chọn: " + label + ".\nNhập biển số xe (vd: 29A-12345):")
            .build();
        sender.execute(edit);
        log.info("Shipper FSM vehicle picked userId={} type={}", userId, type);
    }
}
