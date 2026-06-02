package com.shop.delivery.shared.event;

/**
 * Published after a shop owner approves a pending shipper in the Web Admin
 * (POST {@code /api/admin/shippers/{id}/approve}). Notification listener
 * tells the shipper on Telegram they can now open the Mini App.
 */
public record ShipperApprovedEvent(
    Long telegramUserId
) {}
