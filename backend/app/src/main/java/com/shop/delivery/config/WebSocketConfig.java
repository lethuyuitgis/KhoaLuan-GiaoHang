package com.shop.delivery.config;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.service.TelegramInitDataVerifier;
import com.shop.delivery.auth.service.TelegramUserService;
import com.shop.delivery.auth.service.TelegramUserUpsertCommand;
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

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
    private static final String HEADER_INIT_DATA = "X-Telegram-Init-Data";

    private final TelegramInitDataVerifier verifier;
    private final TelegramUserService userService;

    public WebSocketConfig(TelegramInitDataVerifier verifier, TelegramUserService userService) {
        this.verifier = verifier;
        this.userService = userService;
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
                    String initData = accessor.getFirstNativeHeader(HEADER_INIT_DATA);
                    if (initData == null || initData.isBlank()) {
                        log.debug("WS CONNECT rejected — missing X-Telegram-Init-Data header");
                        return null;
                    }
                    var verified = verifier.tryVerify(initData);
                    if (verified.isEmpty()) {
                        log.debug("WS CONNECT rejected — invalid initData");
                        return null;
                    }
                    var v = verified.get();
                    TelegramUser user = userService.registerOrUpdate(new TelegramUserUpsertCommand(
                        v.userId(), v.username(), v.firstName(), v.lastName(), v.languageCode()));
                    accessor.setUser(new TelegramUserPrincipal(user));
                    log.debug("WS CONNECT OK for userId={}", user.getId());
                } else if (StompCommand.SUBSCRIBE.equals(cmd)) {
                    // Defense-in-depth: only allow per-user `/user/queue/...` destinations.
                    // Public `/topic/...` destinations from clients are dropped because we never
                    // broadcast to them — keeps the broker from being used as a free-for-all bus.
                    String dest = accessor.getDestination();
                    if (dest == null || (!dest.startsWith("/user/") && !dest.startsWith("/queue/"))) {
                        log.debug("WS SUBSCRIBE rejected — disallowed destination {}", dest);
                        return null;
                    }
                }
                return message;
            }
        });
    }

    public record TelegramUserPrincipal(TelegramUser user) implements java.security.Principal {
        @Override public String getName() { return String.valueOf(user.getId()); }
    }
}
