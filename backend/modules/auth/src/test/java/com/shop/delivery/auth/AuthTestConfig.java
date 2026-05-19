package com.shop.delivery.auth;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot configuration for auth module integration tests.
 * The auth module does not contain a full Application class — this class
 * provides @SpringBootConfiguration so @DataJpaTest can bootstrap a context.
 * Located at com.shop.delivery.auth so that test-class package walking finds it.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("com.shop.delivery.auth.entity")
@EnableJpaRepositories("com.shop.delivery.auth.repository")
public class AuthTestConfig {
}
