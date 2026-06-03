package com.shop.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.shop.delivery.support.PostgresTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the shop_config endpoints:
 * - GET /api/public/shop-config (no auth, public shape)
 * - GET /api/admin/shop-config (auth required)
 * - PUT /api/admin/shop-config (auth + validation + update flow)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ShopConfigIT extends PostgresTestContainer {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private String adminToken() {
        String hash = encoder.encode("test123");
        jdbc.update("INSERT INTO admin_user(email, password_hash, full_name, is_active) " +
            "VALUES (?, ?, ?, true) ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash",
            "shopcfg-test@shop.local", hash, "Shop Config Test");

        Map<String, String> body = Map.of("email", "shopcfg-test@shop.local", "password", "test123");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> resp = rest.exchange("/api/admin/auth/login", HttpMethod.POST,
            new HttpEntity<>(body, h), JsonNode.class);
        return resp.getBody().get("accessToken").asText();
    }

    @BeforeEach
    void resetSingleton() {
        // Reset the singleton row back to defaults before each test so updates
        // from previous tests don't leak across. We cannot rely on @Transactional
        // because TestRestTemplate makes real HTTP calls outside the test tx.
        jdbc.update("UPDATE shop_config SET " +
            "name = 'Shop Giao Hàng', " +
            "tagline = 'Giao đồ ăn nhanh • Thanh toán dễ', " +
            "logo_url = NULL, " +
            "brand_primary = '#D97706', " +
            "brand_secondary = '#FB923C', " +
            "contact_phone = NULL, contact_email = NULL, " +
            "opening_hours = '08:00 - 22:00 hằng ngày', " +
            "pickup_lat = 21.0285, pickup_lng = 105.8542, " +
            "pickup_address = 'Shop default', " +
            "fee_base = 15000, fee_per_km = 5000, free_km = 0, " +
            "updated_at = NOW() WHERE id = 1");
    }

    @Test
    void publicEndpointReturnsConfigShape() {
        ResponseEntity<JsonNode> resp = rest.getForEntity("/api/public/shop-config", JsonNode.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode body = resp.getBody();
        assertThat(body.get("name").asText()).isEqualTo("Shop Giao Hàng");
        assertThat(body.get("brandPrimary").asText()).isEqualTo("#D97706");
        assertThat(body.get("brandSecondary").asText()).isEqualTo("#FB923C");
        assertThat(body.get("pickupAddress").asText()).isNotBlank();
    }

    @Test
    void adminEndpointRequiresAuth() {
        ResponseEntity<JsonNode> resp = rest.getForEntity("/api/admin/shop-config", JsonNode.class);
        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    void putWithValidBodyUpdatesConfig() {
        String token = adminToken();
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = validBody();
        body.put("name", "My Custom Shop");
        body.put("brandPrimary", "#123456");

        ResponseEntity<JsonNode> resp = rest.exchange("/api/admin/shop-config",
            HttpMethod.PUT, new HttpEntity<>(body, h), JsonNode.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().get("name").asText()).isEqualTo("My Custom Shop");
        assertThat(resp.getBody().get("brandPrimary").asText()).isEqualTo("#123456");

        // Public endpoint should reflect the change immediately (no client-side
        // cache between calls, just StaleTime in TanStack Query on the FE).
        ResponseEntity<JsonNode> pub = rest.getForEntity("/api/public/shop-config", JsonNode.class);
        assertThat(pub.getBody().get("name").asText()).isEqualTo("My Custom Shop");
    }

    @Test
    void putWithInvalidColorIsRejected() {
        String token = adminToken();
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = validBody();
        body.put("brandPrimary", "not-a-color");

        ResponseEntity<JsonNode> resp = rest.exchange("/api/admin/shop-config",
            HttpMethod.PUT, new HttpEntity<>(body, h), JsonNode.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void putWithTooShortNameIsRejected() {
        String token = adminToken();
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = validBody();
        body.put("name", "ab"); // min = 3

        ResponseEntity<JsonNode> resp = rest.exchange("/api/admin/shop-config",
            HttpMethod.PUT, new HttpEntity<>(body, h), JsonNode.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void putWithNegativeFeeIsRejected() {
        String token = adminToken();
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = validBody();
        body.put("feeBase", -1000);

        ResponseEntity<JsonNode> resp = rest.exchange("/api/admin/shop-config",
            HttpMethod.PUT, new HttpEntity<>(body, h), JsonNode.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    private Map<String, Object> validBody() {
        // Mutable map so individual tests can override fields.
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Test Shop");
        body.put("tagline", "A tagline");
        body.put("logoUrl", null);
        body.put("brandPrimary", "#D97706");
        body.put("brandSecondary", "#FB923C");
        body.put("contactPhone", "+84901234567");
        body.put("contactEmail", "shop@example.com");
        body.put("openingHours", "08:00 - 22:00");
        body.put("pickupLat", 21.0285);
        body.put("pickupLng", 105.8542);
        body.put("pickupAddress", "123 Le Loi, Hanoi");
        body.put("feeBase", 15000);
        body.put("feePerKm", 5000);
        body.put("freeKm", 0);
        return body;
    }
}
