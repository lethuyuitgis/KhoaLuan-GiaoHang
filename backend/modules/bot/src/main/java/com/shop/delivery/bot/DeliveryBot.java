package com.shop.delivery.bot;

import com.shop.delivery.bot.config.BotProperties;
import com.shop.delivery.bot.router.UpdateRouter;
import com.shop.delivery.bot.sender.BotSender;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Long-polling bot — chỉ activate khi bot.mode=polling (default cho dev).
 * Production dùng webhook controller (TASK 13) thay cho class này.
 */
@Component
@ConditionalOnProperty(prefix = "bot", name = "mode", havingValue = "polling", matchIfMissing = true)
public class DeliveryBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(DeliveryBot.class);

    private final BotProperties props;
    private final UpdateRouter router;
    private final BotSender sender;

    public DeliveryBot(BotProperties props, UpdateRouter router, BotSender sender) {
        super(props.getToken());
        this.props = props;
        this.router = router;
        this.sender = sender;
    }

    @PostConstruct
    void wireUpSender() {
        sender.register(this);
        log.info("DeliveryBot initialized: username={}, mode=polling", props.getUsername());
    }

    @Override
    public String getBotUsername() {
        return props.getUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        log.debug("Received update: id={}", update.getUpdateId());
        router.route(update);
    }
}
