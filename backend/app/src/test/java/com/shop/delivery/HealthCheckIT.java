package com.shop.delivery;

import com.shop.delivery.support.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthCheckIT extends PostgresTestContainer {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void healthEndpointShouldReturnUp() {
        @SuppressWarnings({"rawtypes", "unchecked"})
        ResponseEntity<Map> response = (ResponseEntity)
            restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void healthEndpointShouldReportDbAsUp() {
        ResponseEntity<Map> response = (ResponseEntity)
            restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", Map.class);

        Map<String, Object> components = (Map<String, Object>) response.getBody().get("components");
        assertThat(components).isNotNull();

        Map<String, Object> db = (Map<String, Object>) components.get("db");
        assertThat(db).isNotNull();
        assertThat(db.get("status")).isEqualTo("UP");
    }
}
