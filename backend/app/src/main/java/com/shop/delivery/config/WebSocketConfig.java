package com.shop.delivery.config;

import com.shop.delivery.auth.api.admin.AdminPrincipal;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.service.JwtService;
import com.shop.delivery.auth.service.TelegramInitDataVerifier;
import com.shop.delivery.auth.service.TelegramUserService;
import com.shop.delivery.auth.service.TelegramUserUpsertCommand;
import com.shop.delivery.ws.AdminPrincipalWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
    private static final String HEADER_INIT_DATA = "X-Telegram-Init-Data";
    private static final String HEADER_AUTH      = "Authorization";
    private static final String BEARER_PREFIX    = "Bearer ";
    private static final String ROLE_SHOP_OWNER  = "SHOP_OWNER";

    private final TelegramInitDataVerifier verifier;
    private final TelegramUserService userService;
    private final JwtService jwtService;

    public WebSocketConfig(TelegramInitDataVerifier verifier,
                           TelegramUserService userService,
                           JwtService jwtService) {
        this.verifier = verifier;
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS handles native WebSocket as its first transport, so a single endpoint suffices.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;
                StompCommand cmd = accessor.getCommand();

                if (StompCommand.CONNECT.equals(cmd)) {
                    Principal principal = authenticate(accessor);
                    if (principal == null) {
                        log.debug("WS CONNECT rejected — no valid credentials");
                        return null;
                    }
                    accessor.setUser(principal);
                    log.debug("WS CONNECT OK for principal {}", principal.getName());

                } else if (StompCommand.SUBSCRIBE.equals(cmd)) {
                    Principal principal = accessor.getUser();
                    String dest = accessor.getDestination();
                    if (!isSubscribeAllowed(principal, dest)) {
                        log.debug("WS SUBSCRIBE rejected — principal={} dest={}",
                            principal == null ? "<none>" : principal.getName(), dest);
                        return null;
                    }
                }
                return message;
            }
        });
    }

    /**
     * Inspect headers and return a {@link Principal} on success, or null on failure.
     * Two paths:
     *   1. X-Telegram-Init-Data → TelegramUserPrincipal (existing Mini App path)
     *   2. Authorization: Bearer <jwt> → AdminPrincipalWrapper (new admin path)
     * Header precedence: init-data wins if both are present (Mini App is the more
     * common case; admin clients won't bother setting init-data).
     */
    private Principal authenticate(StompHeaderAccessor accessor) {
        String initData = accessor.getFirstNativeHeader(HEADER_INIT_DATA);
        if (initData != null && !initData.isBlank()) {
            var verified = verifier.tryVerify(initData);
            if (verified.isEmpty()) return null;
            var v = verified.get();
            TelegramUser user = userService.registerOrUpdate(new TelegramUserUpsertCommand(
                v.userId(), v.username(), v.firstName(), v.lastName(), v.languageCode()));
            return new TelegramUserPrincipal(user);
        }

        String authHeader = accessor.getFirstNativeHeader(HEADER_AUTH);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            var claimsOpt = jwtService.tryParse(token);
            if (claimsOpt.isEmpty()) return null;
            var claims = claimsOpt.get();
            if (!ROLE_SHOP_OWNER.equals(claims.role())) {
                log.debug("WS CONNECT JWT role rejected: {}", claims.role());
                return null;
            }
            return new AdminPrincipalWrapper(new AdminPrincipal(claims.adminUserId(), claims.email()));
        }

        return null;
    }

    /**
     * Per-principal subscription whitelist. Defence in depth on top of business-level
     * authorization in controllers.
     */
    private boolean isSubscribeAllowed(Principal principal, String dest) {
        if (dest == null || principal == null) return false;
        if (principal instanceof TelegramUserPrincipal) {
            return dest.startsWith("/user/") || dest.startsWith("/queue/");
        }
        if (principal instanceof AdminPrincipalWrapper) {
            return dest.startsWith("/topic/admin/")
                || dest.startsWith("/user/")
                || dest.startsWith("/queue/");
        }
        return false;
    }

    public record TelegramUserPrincipal(TelegramUser user) implements Principal {
        @Override public String getName() { return String.valueOf(user.getId()); }
    }
}
