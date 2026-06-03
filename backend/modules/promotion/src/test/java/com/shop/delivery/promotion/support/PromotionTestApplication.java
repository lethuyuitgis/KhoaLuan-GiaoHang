package com.shop.delivery.promotion.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
    scanBasePackages = "com.shop.delivery.promotion",
    exclude = SecurityAutoConfiguration.class
)
@EntityScan(basePackages = "com.shop.delivery.promotion.entity")
@EnableJpaRepositories(basePackages = "com.shop.delivery.promotion.repository")
public class PromotionTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(PromotionTestApplication.class, args);
    }
}
