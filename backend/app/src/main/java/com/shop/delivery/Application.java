package com.shop.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.shop.delivery")
@EntityScan(basePackages = "com.shop.delivery")
@EnableJpaRepositories(basePackages = "com.shop.delivery")
@ConfigurationPropertiesScan(basePackages = "com.shop.delivery")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
