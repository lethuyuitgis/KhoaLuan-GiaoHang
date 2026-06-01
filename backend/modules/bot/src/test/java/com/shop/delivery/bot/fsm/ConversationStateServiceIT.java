package com.shop.delivery.bot.fsm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ConversationStateServiceIT.TestConfig.class)
@Testcontainers
class ConversationStateServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("bot_fsm_test")
        .withUsername("test").withPassword("test").withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.flyway.enabled", () -> "false");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = ConversationState.class)
    @EnableJpaRepositories(basePackageClasses = ConversationStateRepository.class)
    @ComponentScan(basePackageClasses = ConversationStateService.class)
    static class TestConfig {}

    @Autowired ConversationStateService service;
    @Autowired ConversationStateRepository repo;

    @Test
    void roundTrip_jsonbMapPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", "11111111-2222-3333-4444-555555555555");
        payload.put("retries", 0);
        payload.put("flag", true);

        service.put(42L, "CUSTOMER_RATING_COMMENT", payload);

        Optional<ConversationState> loaded = service.get(42L);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getState()).isEqualTo("CUSTOMER_RATING_COMMENT");
        assertThat(loaded.get().getData())
            .containsEntry("orderId", "11111111-2222-3333-4444-555555555555")
            .containsEntry("retries", 0)
            .containsEntry("flag", true);
        assertThat(loaded.get().getUpdatedAt()).isNotNull();

        service.clear(42L);
    }

    @Test
    void overwrite_replacesPayload() {
        service.put(42L, "S1", Map.of("k", "v1"));
        service.put(42L, "S2", Map.of("k", "v2"));

        ConversationState got = service.get(42L).orElseThrow();
        assertThat(got.getState()).isEqualTo("S2");
        assertThat(got.getData()).containsEntry("k", "v2");

        service.clear(42L);
    }

    @Test
    void clear_deletesRow() {
        service.put(42L, "S1", Map.of("k", "v"));
        assertThat(service.get(42L)).isPresent();
        service.clear(42L);
        assertThat(service.get(42L)).isEmpty();
    }

    @Test
    void deleteStaleSince_removesOldRows() {
        service.put(42L, "S1", Map.of("k", "v"));
        // Sleep just enough for Instant.now() in deleteStaleSince to be after the row's updatedAt
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        int deleted = repo.deleteStaleSince(java.time.Instant.now());
        assertThat(deleted).isEqualTo(1);
        assertThat(service.get(42L)).isEmpty();
    }
}
