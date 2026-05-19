package com.shop.delivery.bot.controller;

import com.shop.delivery.bot.config.BotProperties;
import com.shop.delivery.bot.router.UpdateRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/bot")
@ConditionalOnProperty(prefix = "bot", name = "mode", havingValue = "webhook")
public class BotWebhookController {

    private static final Logger log = LoggerFactory.getLogger(BotWebhookController.class);
    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final UpdateRouter router;
    private final BotProperties props;

    public BotWebhookController(UpdateRouter router, BotProperties props) {
        this.router = router;
        this.props = props;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader(value = SECRET_HEADER, required = false) String secret,
                                        @RequestBody Update update) {
        if (!constantTimeEquals(props.getWebhookSecret(), secret)) {
            log.warn("Webhook called with invalid secret header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        router.route(update);
        return ResponseEntity.ok().build();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
