package com.shop.delivery.shared.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventsTest {

    @Test
    void paymentSucceededCarriesOrderAndPaymentIds() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("250000.00");

        PaymentSucceededEvent ev = new PaymentSucceededEvent(orderId, paymentId, amount, "DH-1-123");

        assertThat(ev.orderId()).isEqualTo(orderId);
        assertThat(ev.paymentId()).isEqualTo(paymentId);
        assertThat(ev.amount()).isEqualByComparingTo("250000.00");
        assertThat(ev.txnRef()).isEqualTo("DH-1-123");
    }

    @Test
    void paymentFailedCarriesResponseCode() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        PaymentFailedEvent ev = new PaymentFailedEvent(orderId, paymentId, "DH-1-123", "07");

        assertThat(ev.orderId()).isEqualTo(orderId);
        assertThat(ev.paymentId()).isEqualTo(paymentId);
        assertThat(ev.txnRef()).isEqualTo("DH-1-123");
        assertThat(ev.responseCode()).isEqualTo("07");
    }

    @Test
    void orderConfirmedCarriesCode() {
        UUID orderId = UUID.randomUUID();

        OrderConfirmedEvent ev = new OrderConfirmedEvent(orderId, "DH20260522-1", "VNPAY");

        assertThat(ev.orderId()).isEqualTo(orderId);
        assertThat(ev.orderCode()).isEqualTo("DH20260522-1");
        assertThat(ev.paymentMethod()).isEqualTo("VNPAY");
    }
}
