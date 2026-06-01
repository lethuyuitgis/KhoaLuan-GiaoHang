package com.shop.delivery.delivery.testconfig;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.delivery.entity.Rating;
import com.shop.delivery.delivery.repository.RatingRepository;
import com.shop.delivery.order.entity.Order;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Boot config to bootstrap delivery module integration tests.
 * Mirrors {@code OrderTestConfig} in the order module.
 * Includes auth.TelegramUser + order.Order so any cross-module entity references resolve at @DataJpaTest.
 *
 * <p>Placed in a sub-package ({@code testconfig}) so that {@link org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest}
 * tests in the {@code api} package tree don't auto-discover this as their default Spring Boot configuration
 * (which would pull in JPA/datasource autoconfiguration that WebMvcTest can't satisfy).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = {Rating.class, TelegramUser.class, Order.class})
@EnableJpaRepositories(basePackageClasses = RatingRepository.class)
public class DeliveryTestConfig {
}
