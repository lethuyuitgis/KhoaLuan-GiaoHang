package com.shop.delivery.auth.api;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.auth.service.TelegramInitDataVerifier;
import com.shop.delivery.auth.service.TelegramUserService;
import com.shop.delivery.auth.service.TelegramUserUpsertCommand;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Verify Telegram initData header on every request. On success, set
 * the resolved TelegramUser as request attribute "currentUser" so
 * controllers can access via @CurrentUser.
 *
 * <p>In the {@code dev} profile only, an alternative header
 * {@code X-Dev-User-Id: <telegram_user.id>} is accepted as a shortcut
 * so the Mini App can be tested outside Telegram (browser → localhost).
 * This header is silently ignored under any non-dev profile.
 */
@Component
public class TelegramAuthFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE_CURRENT_USER = "currentUser";
    public static final String HEADER_INIT_DATA = "X-Telegram-Init-Data";
    public static final String HEADER_DEV_USER_ID = "X-Dev-User-Id";

    private static final Logger log = LoggerFactory.getLogger(TelegramAuthFilter.class);

    private final TelegramInitDataVerifier verifier;
    private final TelegramUserService userService;
    private final TelegramUserRepository userRepo;
    /** Chấp nhận header X-Dev-User-Id (impersonate user ĐÃ TỒN TẠI) — bật khi dev profile
     *  HOẶC app.demo-mode=true (cho demo web trong trình duyệt trên server công khai). */
    private final boolean bypassEnabled;

    public TelegramAuthFilter(TelegramInitDataVerifier verifier,
                              TelegramUserService userService,
                              TelegramUserRepository userRepo,
                              Environment env,
                              @org.springframework.beans.factory.annotation.Value("${app.demo-mode:false}") boolean demoMode) {
        this.verifier = verifier;
        this.userService = userService;
        this.userRepo = userRepo;
        boolean devProfile = List.of(env.getActiveProfiles()).contains("dev");
        this.bypassEnabled = devProfile || demoMode;
        if (this.bypassEnabled) {
            log.warn("⚠️  TelegramAuthFilter: X-Dev-User-Id BYPASS ENABLED (devProfile={}, demoMode={}) — "
                + "chỉ dùng cho demo/dev, KHÔNG bật ở prod thật.", devProfile, demoMode);
        }
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
                    log.warn("Auth upsert failed for userId={}: {}", verified.userId(), ex.getMessage());
                }
            });
        } else if (bypassEnabled) {
            String devUserId = request.getHeader(HEADER_DEV_USER_ID);
            if (devUserId != null && !devUserId.isBlank()) {
                try {
                    long id = Long.parseLong(devUserId);
                    userRepo.findById(id).ifPresent(u -> {
                        request.setAttribute(ATTRIBUTE_CURRENT_USER, u);
                        log.debug("DEV bypass: currentUser={} ({})", u.getId(), u.getFirstName());
                    });
                } catch (NumberFormatException ignored) {
                    // bad header value — treat as no-auth, downstream returns 401
                }
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false;
    }
}
