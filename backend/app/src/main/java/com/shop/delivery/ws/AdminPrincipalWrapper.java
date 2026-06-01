package com.shop.delivery.ws;

import com.shop.delivery.auth.api.admin.AdminPrincipal;

/**
 * STOMP {@link java.security.Principal} wrapper around {@link AdminPrincipal}
 * so {@code accessor.setUser(...)} accepts it.
 *
 * <p>Mirrors the existing {@code WebSocketConfig.TelegramUserPrincipal} pattern:
 * Spring's per-user destination machinery routes via {@code Principal.getName()},
 * so we return the admin user id (as a String) — distinct from any Telegram user id
 * (which is a Long; their string forms cannot collide because admin ids start at 1
 * and Telegram ids are typically billions, but more importantly they live in
 * different destination spaces — admin uses {@code /topic/admin/**}, Telegram uses
 * {@code /user/queue/**} — so collision is structurally impossible).
 */
public record AdminPrincipalWrapper(AdminPrincipal admin) implements java.security.Principal {

    @Override
    public String getName() {
        return "admin-" + admin.adminUserId();
    }
}
