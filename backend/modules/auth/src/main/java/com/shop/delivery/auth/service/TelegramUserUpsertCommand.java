package com.shop.delivery.auth.service;

/**
 * Dữ liệu để upsert TelegramUser. Field nullable theo Telegram Bot API:
 * - id luôn có
 * - username, firstName, lastName, languageCode có thể null
 */
public record TelegramUserUpsertCommand(
    Long id,
    String username,
    String firstName,
    String lastName,
    String languageCode
) {
}
