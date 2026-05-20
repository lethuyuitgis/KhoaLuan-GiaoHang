package com.shop.delivery.order;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Boot config to bootstrap order module integration tests.
 * Includes auth.TelegramUser so FK from orders.customer_id resolves at @DataJpaTest.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = {Order.class, TelegramUser.class})
@EnableJpaRepositories(basePackageClasses = OrderRepository.class)
public class OrderTestConfig {
}
