package com.shop.delivery.bot.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bot")
public class BotProperties {

    @NotBlank
    private String token;

    @NotBlank
    private String username;

    /** "polling" hoặc "webhook" */
    private String mode = "polling";

    /** Chỉ dùng khi mode=webhook */
    private String webhookUrl;

    /** Secret token cho webhook verification (X-Telegram-Bot-Api-Secret-Token header) */
    private String webhookSecret;

    /**
     * Public HTTPS URL of the Mini App (Telegram requires HTTPS for the
     * {@code web_app} button). Used by {@code StartHandler} after a user
     * chooses CUSTOMER and by {@code ShipperApprovedEvent} listener to give
     * an approved shipper a one-tap link into the app.
     */
    private String miniappUrl;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public String getMiniappUrl() { return miniappUrl; }
    public void setMiniappUrl(String miniappUrl) { this.miniappUrl = miniappUrl; }
}
