package com.shop.delivery.bot.handler.common;

import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class UnknownCommandHandler implements UpdateHandler {

    private final BotSender sender;

    public UnknownCommandHandler(BotSender sender) {
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update update) {
        return false; // chỉ làm fallback, UpdateRouter gọi trực tiếp
    }

    @Override
    public void handle(Update update) {
        if (!update.hasMessage()) return;
        Long chatId = update.getMessage().getChatId();
        sender.sendText(chatId, "Mình chưa hiểu lệnh này. Gõ /help để xem hướng dẫn.");
    }
}
