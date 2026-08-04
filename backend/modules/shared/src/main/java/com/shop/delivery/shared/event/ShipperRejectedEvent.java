package com.shop.delivery.shared.event;

/**
 * Published after a shop owner rejects a pending shipper in the Web Admin
 * (POST {@code /api/admin/shippers/{id}/reject}). The rejection deletes the
 * shipper's {@code user_role(SHIPPER)} + {@code shipper_profile}, so the
 * notification listener tells the shipper on Telegram they were not approved
 * and may {@code /start} to register again.
 *
 * <p>Mirrors {@link ShipperApprovedEvent} — lives in {@code shared.event} so
 * the notification listener consumes it without importing the delivery module.
 */
public record ShipperRejectedEvent(
    Long telegramUserId
) {}
