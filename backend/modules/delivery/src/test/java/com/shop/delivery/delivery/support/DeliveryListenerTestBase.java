package com.shop.delivery.delivery.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers base for full-context delivery module integration tests (listener ITs).
 * Uses @SpringBootTest so that Spring event infrastructure, @TransactionalEventListener,
 * and all services are registered — required for OrderCompletionListenerIT.
 *
 * SimpMessagingTemplate is mocked because the WebSocket STOMP broker is not set up in
 * test — LocationBroadcaster needs it but is irrelevant to the commission listener tests.
 *
 * Separate from DeliveryTestcontainerBase (which is used by @DataJpaTest slice tests).
 */
@Testcontainers
@SpringBootTest(classes = DeliveryTestApplication.class)
public abstract class DeliveryListenerTestBase {

    @MockBean
    SimpMessagingTemplate simpMessagingTemplate;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("delivery_listener_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.flyway.locations", () -> "classpath:db/migration");
    }
}
