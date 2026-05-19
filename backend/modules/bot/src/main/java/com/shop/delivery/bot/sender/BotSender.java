package com.shop.delivery.bot.sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.io.Serializable;

/**
 * Wrapper xung quanh telegrambots' DefaultAbsSender với log + error handling.
 * DeliveryBot (Task 12) sẽ gọi register() lúc khởi tạo để truyền chính nó vào đây.
 */
@Component
public class BotSender {

    private static final Logger log = LoggerFactory.getLogger(BotSender.class);

    private DefaultAbsSender sender;

    public void register(DefaultAbsSender sender) {
        this.sender = sender;
    }

    public <T extends Serializable, M extends BotApiMethod<T>> T execute(M method) {
        if (sender == null) {
            log.error("BotSender not initialized — DeliveryBot must call register() at startup");
            return null;
        }
        try {
            return sender.execute(method);
        } catch (TelegramApiRequestException e) {
            log.warn("Telegram API error code={} desc='{}' method={}",
                e.getErrorCode(), e.getApiResponse(), method.getMethod());
            return null;
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message: method={}", method.getMethod(), e);
            return null;
        }
    }

    public void sendText(Long chatId, String text) {
        SendMessage msg = SendMessage.builder()
            .chatId(chatId)
            .text(text)
            .build();
        execute(msg);
    }
}
