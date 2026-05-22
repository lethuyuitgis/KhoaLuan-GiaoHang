package com.shop.delivery.payment.entity;

import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.payment.domain.PaymentEventType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEntityTest {

    @Test
    void paymentExposesAllFields() {
        Payment p = new Payment();
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        p.setId(id);
        p.setOrderId(orderId);
        p.setMethod(PaymentMethod.VNPAY);
        p.setAmount(new BigDecimal("250000.00"));
        p.setStatus(PaymentStatus.PENDING);
        p.setVnpTxnRef("DH20260522-1-1716100000000");
        p.setVnpTransactionNo("14123456");
        p.setVnpResponseCode("00");
        Instant now = Instant.now();
        p.setPaidAt(now);
        p.setVersion(0);

        assertThat(p.getId()).isEqualTo(id);
        assertThat(p.getOrderId()).isEqualTo(orderId);
        assertThat(p.getMethod()).isEqualTo(PaymentMethod.VNPAY);
        assertThat(p.getAmount()).isEqualByComparingTo("250000.00");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(p.getVnpTxnRef()).isEqualTo("DH20260522-1-1716100000000");
        assertThat(p.getVnpTransactionNo()).isEqualTo("14123456");
        assertThat(p.getVnpResponseCode()).isEqualTo("00");
        assertThat(p.getPaidAt()).isEqualTo(now);
        assertThat(p.getVersion()).isEqualTo(0);
    }

    @Test
    void transactionExposesFieldsIncludingJsonPayload() {
        PaymentTransaction tx = new PaymentTransaction();
        UUID paymentId = UUID.randomUUID();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("vnp_TxnRef", "DH-1");
        payload.put("vnp_Amount", "25000000");
        Instant now = Instant.now();

        tx.setPaymentId(paymentId);
        tx.setEventType(PaymentEventType.IPN);
        tx.setRawPayload(payload);
        tx.setRecordedAt(now);

        assertThat(tx.getPaymentId()).isEqualTo(paymentId);
        assertThat(tx.getEventType()).isEqualTo(PaymentEventType.IPN);
        assertThat(tx.getRawPayload()).containsEntry("vnp_TxnRef", "DH-1");
        assertThat(tx.getRecordedAt()).isEqualTo(now);
    }

    @Test
    void eventTypeHasExpectedValues() {
        assertThat(PaymentEventType.values())
            .containsExactly(PaymentEventType.CREATE, PaymentEventType.IPN, PaymentEventType.RETURN);
    }
}
