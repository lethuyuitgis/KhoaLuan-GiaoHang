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
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
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
                }
                return message;
            }
        });
    }

    public record TelegramUserPrincipal(TelegramUser user) implements java.security.Principal {
        @Override public String getName() { return String.valueOf(user.getId()); }
    }
}
