package com.shop.delivery;

import com.shop.delivery.support.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationTests extends PostgresTestContainer {

    @Test
    void contextLoads() {
        // Verifies Spring context bootstraps with all configurations.
    }
}
