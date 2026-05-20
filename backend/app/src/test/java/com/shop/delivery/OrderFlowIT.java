package com.shop.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.shop.delivery.support.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql(statements = {
    "INSERT INTO telegram_user(id, first_name, language_code) VALUES (5555, 'Test', 'vi') ON CONFLICT DO NOTHING",
    "INSERT INTO product(name, price, stock, is_active) VALUES ('Test Product', 100000, 100, true) ON CONFLICT DO NOTHING"
})
class OrderFlowIT extends PostgresTestContainer {

    @Autowired TestRestTemplate rest;

    private HttpHeaders customerHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("X-Customer-Id", "5555");
        return h;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void shouldCreateOrderViaRestAndAdminConfirm() {
        // 1. Find a product
        ResponseEntity<JsonNode> productList = rest.getForEntity("/api/products", JsonNode.class);
        assertThat(productList.getStatusCode().is2xxSuccessful()).isTrue();
        Long productId = productList.getBody().get("content").get(0).get("id").asLong();

        // 2. Create order
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

        // 3. Check /api/orders/mine
        ResponseEntity<JsonNode> mine = rest.exchange(
            "/api/orders/mine", HttpMethod.GET,
            new HttpEntity<>(customerHeaders()), JsonNode.class);
        assertThat(mine.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(mine.getBody().get("content").size()).isGreaterThanOrEqualTo(1);

        // 4. Admin confirms
        ResponseEntity<JsonNode> confirmed = rest.exchange(
            "/api/admin/orders/" + orderId + "/confirm", HttpMethod.POST,
            new HttpEntity<>(Map.of("note", "auto-confirm in test"), jsonHeaders()),
            JsonNode.class);

        assertThat(confirmed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(confirmed.getBody().get("status").asText()).isEqualTo("CONFIRMED");
    }
}
