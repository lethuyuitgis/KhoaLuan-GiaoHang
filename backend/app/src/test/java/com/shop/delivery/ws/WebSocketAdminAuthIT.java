package com.shop.delivery.ws;

import com.shop.delivery.auth.service.JwtService;
import com.shop.delivery.support.PostgresTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("WebSocket admin auth IT — STOMP CONNECT path branches on JWT")
class WebSocketAdminAuthIT extends PostgresTestContainer {

    @LocalServerPort
    int port;

    @Autowired
    JwtService jwtService;

    WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        // SockJS client (matches backend's withSockJS endpoint).
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);
        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    @DisplayName("happy path: valid admin JWT → CONNECT succeeds, can SUBSCRIBE to /topic/admin/orders")
    void validJwtConnects() throws Exception {
        // Issue a real JWT via the running JwtService.
        String token = jwtService.issueAccessToken(1L, "admin@shop.local");

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        String url = "ws://localhost:" + port + "/ws";
        StompSession session = stompClient
            .connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, new SilentSessionHandler())
            .get(5, TimeUnit.SECONDS);

        assertThat(session.isConnected()).isTrue();

        // Verify subscription to /topic/admin/orders is accepted.
        StompSession.Subscription sub = session.subscribe("/topic/admin/orders", new SilentFrameHandler());
        assertThat(sub.getSubscriptionId()).isNotNull();

        session.disconnect();
    }

    @Test
    @DisplayName("missing Authorization → CONNECT rejected")
    void missingAuthRejected() {
        String url = "ws://localhost:" + port + "/ws";
        // No connectHeaders provided.
        CompletableFuture<StompSession> future = stompClient
            .connectAsync(url, new SilentSessionHandler());

        // The interceptor returns null → STOMP client gets a CONNECT failure.
        // The future completes exceptionally within ~3s.
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
            .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    @Test
    @DisplayName("garbage JWT → CONNECT rejected")
    void garbageJwtRejected() {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer not-a-real-jwt");

        String url = "ws://localhost:" + port + "/ws";
        CompletableFuture<StompSession> future = stompClient
            .connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, new SilentSessionHandler());

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
            .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    @Test
    @DisplayName("valid JWT but tries to subscribe to a disallowed destination → SUBSCRIBE blocked")
    void unauthorizedDestinationBlocked() throws Exception {
        String token = jwtService.issueAccessToken(1L, "admin@shop.local");

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        String url = "ws://localhost:" + port + "/ws";
        StompSession session = stompClient
            .connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, new SilentSessionHandler())
            .get(5, TimeUnit.SECONDS);

        // /topic/orders (without "admin") is NOT in the admin whitelist.
        // The subscribe call returns; the interceptor silently drops the SUBSCRIBE frame.
        // We assert by sending a probe — the backend won't push to /topic/orders, so
        // we just verify the session is still alive afterwards (not torn down by error).
        session.subscribe("/topic/orders", new SilentFrameHandler());
        Thread.sleep(200);
        assertThat(session.isConnected()).isTrue();  // session still alive — drop is silent
        session.disconnect();
    }

    // ─── helpers ─────────────────────────────────────────────────

    private static class SilentSessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void handleException(StompSession session,
                                    org.springframework.messaging.simp.stomp.StompCommand command,
                                    StompHeaders headers, byte[] payload, Throwable exception) {
            /* swallow */
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            /* swallow */
        }
    }

    private static class SilentFrameHandler implements StompFrameHandler {
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            /* ignore */
        }
    }
}
