package com.shop.delivery.auth.api;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.service.TelegramInitDataVerifier;
import com.shop.delivery.auth.service.TelegramUserService;
import com.shop.delivery.auth.service.TelegramUserUpsertCommand;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Verify Telegram initData header on every request. On success, set
 * the resolved TelegramUser as request attribute "currentUser" so
 * controllers can access via @CurrentUser.
 */
@Component
public class TelegramAuthFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE_CURRENT_USER = "currentUser";
    public static final String HEADER_INIT_DATA = "X-Telegram-Init-Data";

    private static final Logger log = LoggerFactory.getLogger(TelegramAuthFilter.class);

    private final TelegramInitDataVerifier verifier;
    private final TelegramUserService userService;

    public TelegramAuthFilter(TelegramInitDataVerifier verifier,
                              TelegramUserService userService) {
        this.verifier = verifier;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String initData = request.getHeader(HEADER_INIT_DATA);
        if (initData != null && !initData.isBlank()) {
            verifier.tryVerify(initData).ifPresent(verified -> {
                try {
                    TelegramUser user = userService.registerOrUpdate(new TelegramUserUpsertCommand(
                        verified.userId(),
                        verified.username(),
                        verified.firstName(),
                        verified.lastName(),
                        verified.languageCode()
                    ));
                    request.setAttribute(ATTRIBUTE_CURRENT_USER, user);
                    log.debug("Auth OK userId={}", user.getId());
                } catch (Exception ex) {
                    // DB upsert failed (transient connection issue, race, etc.).
                    // Treat as no-auth: leave attribute unset → @CurrentUser will return 401.
                    // Better than letting the exception break the filter chain and surface as 500.
                    log.warn("Auth upsert failed for userId={}: {}", verified.userId(), ex.getMessage());
                }
            });
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false;
    }
}
