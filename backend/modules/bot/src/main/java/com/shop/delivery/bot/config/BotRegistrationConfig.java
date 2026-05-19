package com.shop.delivery.bot.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.LongPollingBot;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.List;

/**
 * Config để enable @ConfigurationProperties cho bot và đăng ký bot polling lên TelegramBotsApi.
 *
 * Lý do tự đăng ký thay vì dùng telegrambots-spring-boot-starter: starter 6.9.7.1 vẫn dùng
 * cơ chế META-INF/spring.factories cũ — không tương thích Spring Boot 3.x (cần
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
 * Hệ quả: bean TelegramBotsApi không được tạo và DeliveryBot không được register vào polling.
 */
@Configuration
@EnableConfigurationProperties(BotProperties.class)
public class BotRegistrationConfig {

    private static final Logger log = LoggerFactory.getLogger(BotRegistrationConfig.class);

    @Bean
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        return new TelegramBotsApi(DefaultBotSession.class);
    }

    /**
     * Đăng ký tất cả LongPollingBot bean (DeliveryBot) vào TelegramBotsApi sau khi context khởi tạo xong.
     * Chỉ active khi mode=polling — webhook mode không cần long-polling session.
     */
    @Bean
    @ConditionalOnProperty(prefix = "bot", name = "mode", havingValue = "polling", matchIfMissing = true)
    public BotRegistrar botRegistrar(TelegramBotsApi api, ObjectProvider<List<LongPollingBot>> botsProvider) {
        return new BotRegistrar(api, botsProvider);
    }

    public static class BotRegistrar {
        private final TelegramBotsApi api;
        private final ObjectProvider<List<LongPollingBot>> botsProvider;

        public BotRegistrar(TelegramBotsApi api, ObjectProvider<List<LongPollingBot>> botsProvider) {
            this.api = api;
            this.botsProvider = botsProvider;
        }

        @PostConstruct
        public void registerBots() {
            List<LongPollingBot> bots = botsProvider.getIfAvailable(List::of);
            for (LongPollingBot bot : bots) {
                try {
                    api.registerBot(bot);
                    log.info("Registered long-polling bot: {}", bot.getBotUsername());
                } catch (TelegramApiException e) {
                    log.error("Failed to register bot {}: {}", bot.getBotUsername(), e.getMessage());
                }
            }
        }
    }
}
