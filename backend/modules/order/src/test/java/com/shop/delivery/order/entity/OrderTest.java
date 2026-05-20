package com.shop.delivery.order.entity;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    @Test
    void shouldHaveSensibleDefaults() {
        Order o = new Order();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(o.getVersion()).isZero();
    }

    @Test
    void shouldExposeFields() {
        UUID id = UUID.randomUUID();
        Order o = new Order();
        o.setId(id);
        o.setCode("DH20260519-7XK2A");
        o.setCustomerId(123L);
        o.setCustomerName("Thúy Lê");
        o.setCustomerPhone("+84901234567");
        o.setPickupLat(new BigDecimal("21.0285"));
        o.setPickupLng(new BigDecimal("105.8542"));
        o.setDeliveryAddress("45 Bà Triệu");
        o.setDeliveryLat(new BigDecimal("21.0193"));
        o.setDeliveryLng(new BigDecimal("105.8503"));
        o.setDistanceKm(new BigDecimal("1.234"));
        o.setSubtotal(new BigDecimal("250000"));
        o.setDeliveryFee(new BigDecimal("25000"));
        o.setTotal(new BigDecimal("275000"));
        o.setPaymentMethod(PaymentMethod.COD);
        o.setPaymentStatus(PaymentStatus.PENDING);
        o.setStatus(OrderStatus.PENDING);
        o.setNote("Giao tối 6-8h");

        assertThat(o.getId()).isEqualTo(id);
        assertThat(o.getCode()).isEqualTo("DH20260519-7XK2A");
        assertThat(o.getCustomerId()).isEqualTo(123L);
        assertThat(o.getTotal()).isEqualByComparingTo("275000");
        assertThat(o.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
    }
}
