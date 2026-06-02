package com.shop.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.shop.delivery.support.PostgresTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.test.context.jdbc.Sql;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql(statements = {
    "INSERT INTO telegram_user(id, first_name, language_code) VALUES (5555, 'Test', 'vi') ON CONFLICT DO NOTHING",
    "INSERT INTO product(name, price, stock, is_active) VALUES ('Test Product', 100000, 100, true) ON CONFLICT DO NOTHING"
})
class OrderFlowIT extends PostgresTestContainer {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @Value("${bot.token}") String botToken;

    @BeforeEach
    void seedTestAdmin() {
        String hash = encoder.encode("flowtest123");
        jdbc.update("INSERT INTO admin_user(email, password_hash, full_name, is_active) " +
            "VALUES (?, ?, ?, true) ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash",
            "flow-admin@shop.local", hash, "Flow Admin");
    }

    private HttpHeaders customerHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("X-Telegram-Init-Data", buildInitDataFor(5555L, "Test", "User", "testuser"));
        return h;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders loginH = jsonHeaders();
        ResponseEntity<JsonNode> loginResp = rest.exchange("/api/admin/auth/login", HttpMethod.POST,
            new HttpEntity<>(Map.of("email", "flow-admin@shop.local", "password", "flowtest123"), loginH),
            JsonNode.class);
        if (!loginResp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Test admin login failed: " + loginResp.getBody());
        }
        String token = loginResp.getBody().get("accessToken").asText();
        HttpHeaders h = jsonHeaders();
        h.add("Authorization", "Bearer " + token);
        return h;
    }

    @Test
    void shouldCreateOrderViaRestAndAdminConfirm() {
        // Find the 'Test Product' seeded by this IT's @Sql script (price 100_000).
        // Don't use content[0] — V11 demo seed inserts food products with lower IDs that
        // would change the subtotal assertion downstream.
        ResponseEntity<JsonNode> productList = rest.getForEntity("/api/products?size=100", JsonNode.class);
        assertThat(productList.getStatusCode().is2xxSuccessful()).isTrue();
        Long productId = null;
        for (JsonNode p : productList.getBody().get("content")) {
            if ("Test Product".equals(p.get("name").asText())) {
                productId = p.get("id").asLong();
                break;
            }
        }
        assertThat(productId).as("Test Product must exist via @Sql seed").isNotNull();

        Map<String, Object> body = Map.of(
            "customerName", "Test Customer",
            "customerPhone", "+84900111222",
            "deliveryAddress", "45 Bà Triệu",
            "deliveryLat", "21.0193",
            "deliveryLng", "105.8503",
            "items", List.of(Map.of("productId", productId, "quantity", 2)),
            "paymentMethod", "COD",
            "note", "test order"
        );

        ResponseEntity<JsonNode> createResp = rest.exchange(
            "/api/orders", HttpMethod.POST,
            new HttpEntity<>(body, customerHeaders()), JsonNode.class);

        assertThat(createResp.getStatusCode().value()).isEqualTo(201);
        JsonNode order = createResp.getBody();
        assertThat(order.get("status").asText()).isEqualTo("PENDING");
        assertThat(order.get("code").asText()).startsWith("DH");
        assertThat(order.get("subtotal").asLong()).isEqualTo(200000L);
        assertThat(order.get("items").size()).isEqualTo(1);

        String orderId = order.get("id").asText();

        ResponseEntity<JsonNode> mine = rest.exchange(
            "/api/orders/mine", HttpMethod.GET,
            new HttpEntity<>(customerHeaders()), JsonNode.class);
        assertThat(mine.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(mine.getBody().get("content").size()).isGreaterThanOrEqualTo(1);

        ResponseEntity<JsonNode> confirmed = rest.exchange(
            "/api/admin/orders/" + orderId + "/confirm", HttpMethod.POST,
            new HttpEntity<>(Map.of("note", "auto-confirm in test"), adminHeaders()),
            JsonNode.class);

        assertThat(confirmed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(confirmed.getBody().get("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void unauthorizedRequestShouldReturn401() {
        ResponseEntity<JsonNode> resp = rest.getForEntity("/api/orders/mine", JsonNode.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    private String buildInitDataFor(long userId, String firstName, String lastName, String username) {
        String userJson = String.format(
            "{\"id\":%d,\"first_name\":\"%s\",\"last_name\":\"%s\",\"username\":\"%s\",\"language_code\":\"vi\"}",
            userId, firstName, lastName, username);

        TreeMap<String, String> params = new TreeMap<>();
        params.put("auth_date", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("query_id", "AAH" + System.nanoTime());
        params.put("user", userJson);

        StringBuilder checkString = new StringBuilder();
        boolean first = true;
        for (var e : params.entrySet()) {
            if (!first) checkString.append('\n');
            checkString.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        byte[] secretKey = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8),
            botToken.getBytes(StandardCharsets.UTF_8));
        byte[] hashBytes = hmacSha256(secretKey, checkString.toString().getBytes(StandardCharsets.UTF_8));
        String hash = toHex(hashBytes);

        StringBuilder qs = new StringBuilder();
        for (var e : params.entrySet()) {
            if (qs.length() > 0) qs.append('&');
            qs.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
              .append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        qs.append("&hash=").append(hash);
        return qs.toString();
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
