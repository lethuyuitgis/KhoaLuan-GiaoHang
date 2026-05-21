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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminAuthIT extends PostgresTestContainer {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void seedAdmin() {
        String hash = encoder.encode("test123");
        jdbc.update("INSERT INTO admin_user(email, password_hash, full_name, is_active) " +
            "VALUES (?, ?, ?, true) ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash",
            "auth-test@shop.local", hash, "Auth Test");
    }

    @Test
    void loginWithCorrectCredentialsShouldReturnJwt() {
        Map<String, String> body = Map.of("email", "auth-test@shop.local", "password", "test123");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<JsonNode> resp = rest.exchange(
            "/api/admin/auth/login", HttpMethod.POST,
            new HttpEntity<>(body, h), JsonNode.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().get("accessToken").asText()).isNotBlank();
        assertThat(resp.getBody().get("refreshToken").asText()).hasSize(36);
        assertThat(resp.getBody().get("adminUserId").asLong()).isPositive();
    }

    @Test
    void loginWithWrongPasswordShouldReturn401() {
        Map<String, String> body = Map.of("email", "auth-test@shop.local", "password", "WRONG");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<JsonNode> resp = rest.exchange(
            "/api/admin/auth/login", HttpMethod.POST,
            new HttpEntity<>(body, h), JsonNode.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void adminEndpointWithoutTokenShouldReturn401Or403() {
        ResponseEntity<JsonNode> resp = rest.getForEntity("/api/admin/orders", JsonNode.class);
        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    void adminEndpointWithValidTokenShouldReturn200() {
        Map<String, String> loginBody = Map.of("email", "auth-test@shop.local", "password", "test123");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> loginResp = rest.exchange("/api/admin/auth/login", HttpMethod.POST,
            new HttpEntity<>(loginBody, h), JsonNode.class);
        String token = loginResp.getBody().get("accessToken").asText();

        HttpHeaders auth = new HttpHeaders();
        auth.add("Authorization", "Bearer " + token);
        ResponseEntity<JsonNode> resp = rest.exchange("/api/admin/orders", HttpMethod.GET,
            new HttpEntity<>(auth), JsonNode.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
