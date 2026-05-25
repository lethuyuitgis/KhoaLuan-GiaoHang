package com.shop.delivery.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.payment.entity.Payment;
import com.shop.delivery.payment.repository.PaymentRepository;
import com.shop.delivery.payment.service.VnpaySignatureService;
import com.shop.delivery.support.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class VnpayControllerIT extends PostgresTestContainer {

    @Autowired MockMvc mvc;
    @Autowired OrderRepository orderRepo;
    @Autowired PaymentRepository paymentRepo;
    @Autowired VnpaySignatureService sig;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private Long seedTelegramUser() {
        long id = 700000L + (System.nanoTime() & 0xFFFF);
        jdbc.update("INSERT INTO telegram_user(id, first_name, language_code) " +
            "VALUES (?, 'VnpayIT', 'vi') ON CONFLICT DO NOTHING", id);
        return id;
    }

    @Test
    void ipnHappyPath_flipsPaymentAndOrder() throws Exception {
        // Arrange: insert an order + pending payment directly.
        Order order = persistVnpayOrder(new BigDecimal("250000.00"));
        Payment payment = persistPendingPayment(order, "DH-IT-1-" + System.currentTimeMillis());

        // Build a signed IPN body.
        Map<String, String> ipn = signedIpn(payment, "00", "00");

        // Act: POST to /ipn with form params.
        mvc.perform(post("/api/payment/vnpay/ipn")
                .contentType("application/x-www-form-urlencoded")
                .content(toForm(ipn)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.RspCode").value("00"))
            .andExpect(jsonPath("$.Message").value("Confirm Success"));

        // Assert payment flipped synchronously.
        Payment refreshed = paymentRepo.findById(payment.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(refreshed.getPaidAt()).isNotNull();

        // Assert order eventually confirms (AFTER_COMMIT listener fires in a separate TX,
        // so we Await rather than asserting immediately). Query via JDBC to bypass
        // the test thread's JPA session cache (OSIV would otherwise return stale data).
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String status = jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, order.getId());
            String paymentStatus = jdbc.queryForObject(
                "SELECT payment_status FROM orders WHERE id = ?", String.class, order.getId());
            assertThat(status).isEqualTo(OrderStatus.CONFIRMED.name());
            assertThat(paymentStatus).isEqualTo(PaymentStatus.SUCCESS.name());
        });
    }

    @Test
    void ipnReplay_returns02_doesNotChangeAnything() throws Exception {
        Order order = persistVnpayOrder(new BigDecimal("250000.00"));
        Payment payment = persistPendingPayment(order, "DH-IT-2-" + System.currentTimeMillis());
        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepo.save(payment);

        Map<String, String> ipn = signedIpn(payment, "00", "00");

        mvc.perform(post("/api/payment/vnpay/ipn")
                .contentType("application/x-www-form-urlencoded")
                .content(toForm(ipn)))
            .andExpect(jsonPath("$.RspCode").value("02"));
    }

    // ---- helpers ----

    private Order persistVnpayOrder(BigDecimal total) {
        Long customerId = seedTelegramUser();
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCode("DH-IT-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4));
        o.setCustomerId(customerId);
        o.setPickupLat(new BigDecimal("21.0285"));
        o.setPickupLng(new BigDecimal("105.8542"));
        o.setDeliveryAddress("Test addr");
        o.setDeliveryLat(new BigDecimal("21.0193"));
        o.setDeliveryLng(new BigDecimal("105.8503"));
        o.setDistanceKm(new BigDecimal("3.200"));
        o.setSubtotal(total.subtract(new BigDecimal("25000")));
        o.setDeliveryFee(new BigDecimal("25000"));
        o.setTotal(total);
        o.setPaymentMethod(PaymentMethod.VNPAY);
        o.setPaymentStatus(PaymentStatus.PENDING);
        o.setStatus(OrderStatus.PENDING);
        return orderRepo.save(o);
    }

    private Payment persistPendingPayment(Order order, String txnRef) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOrderId(order.getId());
        p.setMethod(PaymentMethod.VNPAY);
        p.setAmount(order.getTotal());
        p.setStatus(PaymentStatus.PENDING);
        p.setVnpTxnRef(txnRef);
        return paymentRepo.save(p);
    }

    private Map<String, String> signedIpn(Payment p, String responseCode, String txStatus) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode",          "TEST01");
        params.put("vnp_Amount",           p.getAmount().multiply(new BigDecimal("100")).toBigInteger().toString());
        params.put("vnp_BankCode",         "NCB");
        params.put("vnp_OrderInfo",        "Test");
        params.put("vnp_ResponseCode",     responseCode);
        params.put("vnp_TransactionStatus",txStatus);
        params.put("vnp_TransactionNo",    "14000001");
        params.put("vnp_TxnRef",           p.getVnpTxnRef());
        params.put("vnp_PayDate",          "20260522170000");
        String hash = sig.buildHashAndQuery(params).hash();
        params.put("vnp_SecureHash", hash);
        return params;
    }

    private static String toForm(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=')
                .append(java.net.URLEncoder.encode(e.getValue(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
