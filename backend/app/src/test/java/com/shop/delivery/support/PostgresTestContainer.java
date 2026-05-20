package com.shop.delivery.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Test base class providing a single shared Postgres container for all tests.
 * Uses a JVM-wide singleton pattern (manual start, no @Testcontainers lifecycle)
 * so the container survives across test classes and Spring's context cache.
 */
public abstract class PostgresTestContainer {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("shop_delivery_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
