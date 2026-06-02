package com.shop.delivery.shared.event;

/**
 * Published after a user finishes the shipper-registration FSM in the bot.
 * Listeners (notification module) broadcast to every active SHOP_OWNER so the
 * admin knows there's a new pending shipper waiting in the Web Admin.
 *
 * <p>Lives in {@code shared.event} so the notification listener can consume
 * it without importing the bot module directly (notification already depends
 * on bot, but other future listeners can stay decoupled).
 */
public record ShipperRegisteredEvent(
    Long telegramUserId,
    String fullName,
    String vehicleType,
    String licensePlate
) {}
