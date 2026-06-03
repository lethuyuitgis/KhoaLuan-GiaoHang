package com.shop.delivery.order.repository;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.order.OrderTestConfig;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.support.OrderTestcontainerBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(OrderTestConfig.class)
class OrderRepositoryIT extends OrderTestcontainerBase {

    @Autowired OrderRepository orderRepo;
    @Autowired EntityManager em;

    @Test
    void shouldPersistOrderAndFindByCode() {
        TelegramUser customer = new TelegramUser();
        customer.setId(1234L);
        customer.setFirstName("Bob");
        em.persist(customer);

        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCode("DH20260519-ABCDE");
        o.setCustomerId(1234L);
        o.setPickupLat(new BigDecimal("21.0285"));
        o.setPickupLng(new BigDecimal("105.8542"));
        o.setDeliveryAddress("45 Bà Triệu");
        o.setDeliveryLat(new BigDecimal("21.0193"));
        o.setDeliveryLng(new BigDecimal("105.8503"));
        o.setDistanceKm(new BigDecimal("1.234"));
        o.setSubtotal(new BigDecimal("100000"));
        o.setDeliveryFee(new BigDecimal("25000"));
        o.setDeliveryFeeOriginal(new BigDecimal("25000"));
        o.setTotal(new BigDecimal("125000"));
        o.setPaymentMethod(PaymentMethod.COD);
        orderRepo.save(o);

        Page<Order> result = orderRepo.findAllByCustomerIdOrderByCreatedAtDesc(1234L, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCode()).isEqualTo("DH20260519-ABCDE");
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void findByCodeShouldReturnOrder() {
        TelegramUser customer = new TelegramUser();
        customer.setId(5555L);
        em.persist(customer);

        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCode("DH20260519-XYZ12");
        o.setCustomerId(5555L);
        o.setPickupLat(new BigDecimal("21.0"));
        o.setPickupLng(new BigDecimal("105.8"));
        o.setDeliveryAddress("test");
        o.setDeliveryLat(new BigDecimal("21.1"));
        o.setDeliveryLng(new BigDecimal("105.9"));
        o.setDistanceKm(new BigDecimal("5.0"));
        o.setSubtotal(new BigDecimal("50000"));
        o.setDeliveryFee(new BigDecimal("10000"));
        o.setDeliveryFeeOriginal(new BigDecimal("10000"));
        o.setTotal(new BigDecimal("60000"));
        o.setPaymentMethod(PaymentMethod.VNPAY);
        orderRepo.save(o);

        var found = orderRepo.findByCode("DH20260519-XYZ12");
        assertThat(found).isPresent();
        assertThat(found.get().getCustomerId()).isEqualTo(5555L);
    }
}
