package com.shop.delivery.bot.handler.common;

import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class HelpHandler implements UpdateHandler {

    private static final String HELP_TEXT = """
        📖 Hướng dẫn

        Lệnh thường dùng:
        /start — Bắt đầu / xem menu chính
        /myorders — Xem đơn hàng của tôi
        /help — Trợ giúp

        Cần hỗ trợ? Liên hệ shop qua @yourshop
        """;

    private final BotSender sender;

    public HelpHandler(BotSender sender) {
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return false;
        return update.getMessage().getText().trim().startsWith("/help");
    }

    @Override
    public void handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        sender.sendText(chatId, HELP_TEXT);
    }
}
