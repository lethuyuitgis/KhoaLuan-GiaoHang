package com.shop.delivery.notification;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.UserRoleRepository;
import com.shop.delivery.bot.config.BotProperties;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.shared.event.ShipperApprovedEvent;
import com.shop.delivery.shared.event.ShipperRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import java.util.List;
import java.util.Locale;

/**
 * Listens for shipper-lifecycle events emitted by {@code ShipperProfileService}
 * and pushes Telegram notifications:
 *
 * <ul>
 *   <li>{@link ShipperRegisteredEvent} → broadcast to active SHOP_OWNERs so
 *       they know a new shipper is waiting in {@code /shippers} Web Admin.</li>
 *   <li>{@link ShipperApprovedEvent} → DM the shipper a confirmation with a
 *       Mini App button (or plain text if {@code bot.miniapp-url} not set).</li>
 * </ul>
 *
 * <p>Uses {@code AFTER_COMMIT} so notifications never fire on rollback —
 * same pattern as {@link OrderLifecycleNotifier}.
 */
@Component
public class ShipperRegistrationNotifier {

    private static final Logger log = LoggerFactory.getLogger(ShipperRegistrationNotifier.class);

    private final BotSender bot;
    private final UserRoleRepository roleRepo;
    private final BotProperties botProps;

    public ShipperRegistrationNotifier(BotSender bot,
                                       UserRoleRepository roleRepo,
                                       BotProperties botProps) {
        this.bot = bot;
        this.roleRepo = roleRepo;
        this.botProps = botProps;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShipperRegistered(ShipperRegisteredEvent e) {
        String vehicleLabel = switch (e.vehicleType()) {
            case "MOTORBIKE" -> "Xe máy";
            case "CAR"       -> "Ô tô";
            case "BICYCLE"   -> "Xe đạp";
            default          -> e.vehicleType();
        };
        String text = String.format(Locale.ROOT,
            "🆕 Shipper mới đăng ký%n"
                + "👤 Tên: %s%n"
                + "🆔 Telegram: %d%n"
                + "🚲 Phương tiện: %s%n"
                + "🔢 Biển số: %s%n"
                + "Mở Web Admin → /shippers để duyệt.",
            e.fullName(), e.telegramUserId(), vehicleLabel, e.licensePlate()
        );
        broadcastToAdmins(text, "ShipperRegistered " + e.telegramUserId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShipperApproved(ShipperApprovedEvent e) {
        String miniappUrl = botProps.getMiniappUrl();
        if (miniappUrl != null && !miniappUrl.isBlank()) {
            InlineKeyboardButton open = InlineKeyboardButton.builder()
                .text("🚀 Mở Mini App")
                .webApp(new WebAppInfo(miniappUrl))
                .build();
            InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(open)))
                .build();
            SendMessage msg = SendMessage.builder()
                .chatId(e.telegramUserId())
                .text("✅ Bạn đã được duyệt làm shipper! Bấm nút bên dưới để mở Mini App nhận đơn.")
                .replyMarkup(kb)
                .build();
            bot.execute(msg);
        } else {
            bot.sendText(e.telegramUserId(),
                "✅ Bạn đã được duyệt làm shipper! Mở Mini App để bắt đầu nhận đơn.");
        }
        log.info("Notified shipper {} that they are ACTIVE", e.telegramUserId());
    }

    // ---- helpers -----------------------------------------------------------

    private void broadcastToAdmins(String text, String contextForLog) {
        List<UserRole> admins = roleRepo.findAll().stream()
            .filter(r -> r.getRole() == Role.SHOP_OWNER && r.getStatus() == UserRoleStatus.ACTIVE)
            .toList();
        if (admins.isEmpty()) {
            log.debug("No active SHOP_OWNER to notify for {}", contextForLog);
            return;
        }
        for (UserRole admin : admins) {
            bot.sendText(admin.getTelegramUserId(), text);
        }
        log.info("Notified {} admin(s) about {}", admins.size(), contextForLog);
    }
}
