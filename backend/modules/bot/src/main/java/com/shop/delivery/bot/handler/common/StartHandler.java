package com.shop.delivery.bot.handler.common;

import com.shop.delivery.auth.service.TelegramUserService;
import com.shop.delivery.auth.service.TelegramUserUpsertCommand;
import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

@Component
public class StartHandler implements UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(StartHandler.class);
    private static final String WELCOME = """
        Chào %s! 👋

        Đây là bot quản lý giao hàng. Bấm nút bên dưới để bắt đầu đặt hàng.

        Bạn cũng có thể gõ:
        /myorders — Xem đơn hàng của tôi
        /help — Trợ giúp
        """;

    private final TelegramUserService userService;
    private final BotSender sender;

    public StartHandler(TelegramUserService userService, BotSender sender) {
        this.userService = userService;
        this.sender = sender;
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

        userService.registerOrUpdate(new TelegramUserUpsertCommand(
            userId,
            from.getUserName(),
            from.getFirstName(),
            from.getLastName(),
            from.getLanguageCode()
        ));

        String displayName = from.getFirstName() != null ? from.getFirstName() : "bạn";
        sender.sendText(chatId, String.format(WELCOME, displayName));

        log.info("Handled /start for userId={} username={}", userId, from.getUserName());
    }
}
