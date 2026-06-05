package com.shop.delivery.delivery.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketMessagingAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot application for delivery module listener integration tests.
 * Scans delivery + order + auth packages, but excludes any class annotated with
 * {@code @SpringBootConfiguration} other than itself — this prevents the inner
 * {@code TestConfig} classes from @DataJpaTest slice tests (e.g. ReportsRepositoryIT.TestConfig,
 * DeliveryTestConfig) from being picked up and causing duplicate @EnableJpaRepositories conflicts.
 *
 * WebSocket auto-configurations are excluded because the listener test does not need
 * the STOMP broker or SimpMessagingTemplate — those require a full WebSocket setup.
 * Security auto-configuration is kept so that SecurityConfig from auth can get HttpSecurity.
 */
@SpringBootApplication(exclude = {
    WebSocketServletAutoConfiguration.class,
    WebSocketMessagingAutoConfiguration.class
})
@ConfigurationPropertiesScan(basePackages = "com.shop.delivery")
@ComponentScan(
    basePackages = {
        "com.shop.delivery.delivery",
        "com.shop.delivery.order",
        "com.shop.delivery.auth"
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        classes = SpringBootConfiguration.class
    )
)
@EntityScan(basePackages = {
    "com.shop.delivery.delivery.entity",
    "com.shop.delivery.order.entity",
    "com.shop.delivery.auth.entity"
})
@EnableJpaRepositories(basePackages = {
    "com.shop.delivery.delivery.repository",
    "com.shop.delivery.order.repository",
    "com.shop.delivery.auth.repository"
})
public class DeliveryTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeliveryTestApplication.class, args);
    }
}
