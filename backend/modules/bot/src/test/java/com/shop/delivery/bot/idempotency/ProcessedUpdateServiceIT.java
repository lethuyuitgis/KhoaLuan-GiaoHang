package com.shop.delivery.bot.idempotency;

import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ProcessedUpdateServiceIT.TestConfig.class)
@Testcontainers
class ProcessedUpdateServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bot_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.flyway.enabled", () -> "false");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = ProcessedUpdate.class)
    @EnableJpaRepositories(basePackageClasses = ProcessedUpdateRepository.class)
    @ComponentScan(basePackageClasses = ProcessedUpdateService.class)
    static class TestConfig {}

    @Autowired ProcessedUpdateService service;
    @Autowired ProcessedUpdateRepository repo;

    @BeforeEach
    void cleanDb() {
        // Use deleteAllInBatch (bulk DELETE), not deleteAll: because
        // Persistable.isNew() returns true for every ProcessedUpdate,
        // EntityManager.remove() would be a no-op on entities loaded
        // via findAll(). Bulk delete bypasses entity state checks.
        repo.deleteAllInBatch();
    }

    @Test
    void firstCallReturnsTrueDuplicateReturnsFalse() {
        long updateId = 42L;
        assertThat(service.markIfNew(updateId)).isTrue();
        assertThat(service.markIfNew(updateId)).isFalse();
        // exactly one row in DB — duplicate did NOT update or insert
        assertThat(repo.count()).isEqualTo(1L);
    }

    @Test
    void differentUpdateIdsAreIndependent() {
        assertThat(service.markIfNew(100L)).isTrue();
        assertThat(service.markIfNew(200L)).isTrue();
        assertThat(service.markIfNew(100L)).isFalse();
        assertThat(service.markIfNew(300L)).isTrue();
        assertThat(repo.count()).isEqualTo(3L);
    }
}
