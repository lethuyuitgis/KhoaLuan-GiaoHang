package com.shop.delivery.bot.handler.shipper;

import com.shop.delivery.bot.fsm.ConversationState;
import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.fsm.ShipperRegistrationStates;
import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.domain.VehicleType;
import com.shop.delivery.delivery.service.ShipperProfileService;
import com.shop.delivery.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * FSM-state-gated text handler for shipper registration. {@code @Order(0)} so
 * it runs before generic command handlers — a user typing "/help" mid-FSM
 * won't fall through to {@code HelpHandler}.
 *
 * <p>Per-state behaviour:
 * <ul>
 *   <li>{@code AWAITING_NAME}: stash full name, advance to PHONE, render
 *       request_contact reply keyboard.</li>
 *   <li>{@code AWAITING_PHONE}: text path is a no-op nudge (user must use the
 *       contact-share button). Phone capture itself lives in
 *       {@code ShipperRegistrationContactHandler}.</li>
 *   <li>{@code AWAITING_VEHICLE}: text path is a no-op nudge (vehicle picked
 *       via inline keyboard).</li>
 *   <li>{@code AWAITING_PLATE}: validate plate regex, finish registration via
 *       {@link ShipperProfileService#registerPending}, clear FSM state.</li>
 * </ul>
 *
 * <p>Universal: {@code /cancel} from any state clears the FSM and exits.
 */
@Component
@Order(0)
public class ShipperRegistrationTextHandler implements UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(ShipperRegistrationTextHandler.class);

    /** 6–16 chars, only digits/letters/dash/space — covers VN plate variants. */
    private static final Pattern PLATE_RE = Pattern.compile("^[0-9A-Z\\-\\s]{6,16}$");

    private final ConversationStateService conv;
    private final ShipperProfileService shipperService;
    private final BotSender sender;

    public ShipperRegistrationTextHandler(ConversationStateService conv,
                                          ShipperProfileService shipperService,
                                          BotSender sender) {
        this.conv = conv;
        this.shipperService = shipperService;
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update u) {
        if (u == null || u.getMessage() == null) return false;
        if (!u.getMessage().hasText()) return false;
        if (u.getMessage().getFrom() == null) return false;
        Long userId = u.getMessage().getFrom().getId();
        return conv.get(userId)
            .map(s -> ShipperRegistrationStates.isShipperRegistration(s.getState()))
            .orElse(false);
    }

    @Override
    public void handle(Update u) {
        Long userId = u.getMessage().getFrom().getId();
        Long chatId = u.getMessage().getChatId();
        String text = u.getMessage().getText() == null ? "" : u.getMessage().getText().trim();

        if ("/cancel".equalsIgnoreCase(text)) {
            conv.clear(userId);
            sender.sendText(chatId, "Đã huỷ đăng ký shipper.");
            return;
        }

        ConversationState state = conv.get(userId).orElse(null);
        if (state == null) return; // defensive — canHandle already checked

        switch (state.getState()) {
            case ShipperRegistrationStates.AWAITING_NAME    -> handleName(userId, chatId, state, text);
            case ShipperRegistrationStates.AWAITING_PHONE   -> nudgeShareContact(chatId);
            case ShipperRegistrationStates.AWAITING_VEHICLE -> nudgePickVehicle(chatId);
            case ShipperRegistrationStates.AWAITING_PLATE   -> handlePlate(userId, chatId, state, text);
            default -> { /* not ours */ }
        }
    }

    // ---- per-state handlers ------------------------------------------------

    private void handleName(Long userId, Long chatId, ConversationState state, String text) {
        if (text.length() < 2 || text.length() > 80) {
            sender.sendText(chatId, "Tên không hợp lệ. Vui lòng gõ lại (2–80 ký tự).");
            return;
        }
        Map<String, Object> data = state.getData() != null ? new HashMap<>(state.getData()) : new HashMap<>();
        data.put(ShipperRegistrationStates.KEY_FULL_NAME, text);
        conv.put(userId, ShipperRegistrationStates.AWAITING_PHONE, data);

        KeyboardButton shareBtn = KeyboardButton.builder()
            .text("📞 Chia sẻ số")
            .requestContact(true)
            .build();
        KeyboardRow row = new KeyboardRow();
        row.add(shareBtn);
        ReplyKeyboardMarkup kb = ReplyKeyboardMarkup.builder()
            .keyboardRow(row)
            .resizeKeyboard(true)
            .oneTimeKeyboard(true)
            .selective(true)
            .build();

        SendMessage msg = SendMessage.builder()
            .chatId(chatId)
            .text("Cảm ơn " + text + "!\nBấm nút bên dưới để chia sẻ số điện thoại của bạn.")
            .replyMarkup(kb)
            .build();
        sender.execute(msg);
    }

    private void nudgeShareContact(Long chatId) {
        sender.sendText(chatId,
            "Vui lòng bấm nút \"📞 Chia sẻ số\" để gửi số điện thoại, không gõ trực tiếp.");
    }

    private void nudgePickVehicle(Long chatId) {
        sender.sendText(chatId,
            "Vui lòng chọn loại phương tiện từ các nút bên dưới.");
    }

    private void handlePlate(Long userId, Long chatId, ConversationState state, String text) {
        String normalised = text.toUpperCase().trim();
        if (!PLATE_RE.matcher(normalised).matches()) {
            sender.sendText(chatId,
                "Biển số không hợp lệ. Mẫu hợp lệ: 6–16 ký tự, chỉ chữ/số/dấu gạch.\n"
                    + "Ví dụ: 29A-12345");
            return;
        }
        Map<String, Object> data = state.getData() != null ? state.getData() : Map.of();
        String fullName     = String.valueOf(data.getOrDefault(ShipperRegistrationStates.KEY_FULL_NAME, ""));
        String vehicleStr   = String.valueOf(data.getOrDefault(ShipperRegistrationStates.KEY_VEHICLE_TYPE, "MOTORBIKE"));

        VehicleType vehicle;
        try {
            vehicle = VehicleType.valueOf(vehicleStr);
        } catch (IllegalArgumentException ex) {
            log.warn("FSM has invalid vehicleType {} for user {}", vehicleStr, userId);
            conv.clear(userId);
            sender.sendText(chatId, "Lỗi nội bộ, vui lòng /start lại.");
            return;
        }

        try {
            shipperService.registerPending(userId, vehicle, normalised, fullName);
        } catch (DomainException ex) {
            log.warn("Shipper register failed for user {}: {}", userId, ex.getMessage());
            sender.sendText(chatId, "Không thể đăng ký: " + ex.getMessage());
            conv.clear(userId);
            return;
        }

        conv.clear(userId);
        sender.sendText(chatId,
            "✅ Đăng ký xong, đang chờ shop duyệt. Bạn sẽ nhận được thông báo khi được duyệt.");
        log.info("Shipper registration finished userId={} plate={}", userId, normalised);
    }

    // ---- inline-keyboard helper for vehicle (used by sibling handler) ------

    static InlineKeyboardMarkup vehicleKeyboard() {
        InlineKeyboardButton moto = InlineKeyboardButton.builder()
            .text("🏍️ Xe máy").callbackData("VEHICLE:MOTORBIKE").build();
        InlineKeyboardButton car = InlineKeyboardButton.builder()
            .text("🚗 Ô tô").callbackData("VEHICLE:CAR").build();
        InlineKeyboardButton bike = InlineKeyboardButton.builder()
            .text("🚲 Xe đạp").callbackData("VEHICLE:BICYCLE").build();
        return InlineKeyboardMarkup.builder()
            .keyboard(List.of(List.of(moto), List.of(car), List.of(bike)))
            .build();
    }
}
