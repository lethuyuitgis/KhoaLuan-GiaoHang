package com.shop.delivery.bot.handler.common;

import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.auth.service.TelegramUserService;
import com.shop.delivery.auth.service.TelegramUserUpsertCommand;
import com.shop.delivery.bot.config.BotProperties;
import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import java.util.List;

@Component
public class StartHandler implements UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(StartHandler.class);

    /** First-time greeting shown together with the role-picker inline keyboard. */
    private static final String WELCOME_NEW = """
        Chào %s! 👋

        Đây là bot quản lý giao hàng nội thành.
        Bạn muốn dùng bot với vai trò nào?
        """;

    /** Returning-user greeting — short, points back at /help. */
    private static final String WELCOME_BACK = """
        Chào mừng trở lại %s! 👋
        Dùng menu để đặt hàng, hoặc gõ /help để xem hướng dẫn.
        """;

    public static final String CALLBACK_ROLE_CUSTOMER = "ROLE:CUSTOMER";
    public static final String CALLBACK_ROLE_SHIPPER  = "ROLE:SHIPPER";

    private final TelegramUserService userService;
    private final TelegramUserRepository userRepo;
    private final BotSender sender;
    private final BotProperties botProps;

    public StartHandler(TelegramUserService userService,
                        TelegramUserRepository userRepo,
                        BotSender sender,
                        BotProperties botProps) {
        this.userService = userService;
        this.userRepo = userRepo;
        this.sender = sender;
        this.botProps = botProps;
    }

    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return false;
        String text = update.getMessage().getText();
        return text != null && text.trim().startsWith("/start");
    }

    @Override
    public void handle(Update update) {
        User from = update.getMessage().getFrom();
        Long userId = from.getId();
        Long chatId = update.getMessage().getChatId();

        boolean isReturning = userRepo.existsById(userId);

        userService.registerOrUpdate(new TelegramUserUpsertCommand(
            userId,
            from.getUserName(),
            from.getFirstName(),
            from.getLastName(),
            from.getLanguageCode()
        ));

        String displayName = from.getFirstName() != null ? from.getFirstName() : "bạn";

        if (isReturning) {
            sender.sendText(chatId, String.format(WELCOME_BACK, displayName));
        } else {
            sendRolePicker(chatId, displayName);
        }

        log.info("Handled /start for userId={} username={} returning={}",
            userId, from.getUserName(), isReturning);
    }

    private void sendRolePicker(Long chatId, String displayName) {
        InlineKeyboardButton customerBtn;
        // Use a Mini App button for customers if miniapp URL is configured;
        // fall back to a plain callback otherwise (dev/test environments).
        String miniappUrl = botProps.getMiniappUrl();
        if (miniappUrl != null && !miniappUrl.isBlank()) {
            customerBtn = InlineKeyboardButton.builder()
                .text("🛍️ Đặt hàng")
                .webApp(new WebAppInfo(miniappUrl))
                .build();
        } else {
            customerBtn = InlineKeyboardButton.builder()
                .text("🛍️ Đặt hàng")
                .callbackData(CALLBACK_ROLE_CUSTOMER)
                .build();
        }

        InlineKeyboardButton shipperBtn = InlineKeyboardButton.builder()
            .text("🚴 Làm shipper")
            .callbackData(CALLBACK_ROLE_SHIPPER)
            .build();

        InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
            .keyboard(List.of(List.of(customerBtn), List.of(shipperBtn)))
            .build();

        SendMessage msg = SendMessage.builder()
            .chatId(chatId)
            .text(String.format(WELCOME_NEW, displayName))
            .replyMarkup(kb)
            .build();
        sender.execute(msg);
    }
}
