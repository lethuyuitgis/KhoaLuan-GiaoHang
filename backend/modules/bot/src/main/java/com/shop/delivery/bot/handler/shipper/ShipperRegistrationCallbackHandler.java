package com.shop.delivery.bot.handler.shipper;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.repository.UserRoleRepository;
import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.fsm.ShipperRegistrationStates;
import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.handler.common.StartHandler;
import com.shop.delivery.bot.sender.BotSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;

/**
 * Handles the {@code ROLE:SHIPPER} callback (sent from {@link StartHandler}'s
 * role-picker keyboard). Opens the shipper-registration FSM, asking for the
 * user's full name.
 *
 * <p>Idempotency: if the user already has a {@code user_role(SHIPPER)} in any
 * status, we answer with "Bạn đã đăng ký rồi" instead of restarting the FSM.
 */
@Component
@Order(40)
public class ShipperRegistrationCallbackHandler implements UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(ShipperRegistrationCallbackHandler.class);

    private final ConversationStateService conv;
    private final UserRoleRepository roleRepo;
    private final BotSender sender;

    public ShipperRegistrationCallbackHandler(ConversationStateService conv,
                                              UserRoleRepository roleRepo,
                                              BotSender sender) {
        this.conv = conv;
        this.roleRepo = roleRepo;
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update u) {
        if (!u.hasCallbackQuery()) return false;
        String data = u.getCallbackQuery().getData();
        return StartHandler.CALLBACK_ROLE_SHIPPER.equals(data);
    }

    @Override
    public void handle(Update u) {
        CallbackQuery cb = u.getCallbackQuery();
        Long userId = cb.getFrom().getId();
        Long chatId = cb.getMessage().getChatId();

        // Acknowledge the callback so Telegram clears the loading state.
        sender.execute(AnswerCallbackQuery.builder()
            .callbackQueryId(cb.getId())
            .build());

        boolean alreadyRegistered = roleRepo.findByTelegramUserIdAndRole(userId, Role.SHIPPER).isPresent();
        if (alreadyRegistered) {
            sender.sendText(chatId, "Bạn đã đăng ký làm shipper rồi, đang chờ duyệt hoặc đã được duyệt.");
            log.info("Shipper registration ignored (duplicate) userId={}", userId);
            return;
        }

        conv.put(userId, ShipperRegistrationStates.AWAITING_NAME, new HashMap<>());
        sender.sendText(chatId,
            "Bắt đầu đăng ký shipper. Bạn tên là gì? (vd: Nguyễn Văn A)\n"
                + "Gõ /cancel bất kỳ lúc nào để huỷ.");
        log.info("Shipper registration FSM opened userId={}", userId);
    }
}
