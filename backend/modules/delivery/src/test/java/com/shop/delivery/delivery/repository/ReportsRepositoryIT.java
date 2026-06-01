package com.shop.delivery.delivery.repository;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.order.entity.Order;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ReportsRepositoryIT.TestConfig.class)
@Testcontainers
@Transactional
class ReportsRepositoryIT {

    /**
     * Inner Spring Boot configuration: scans ALL of {@code com.shop.delivery} so Hibernate
     * generates every JPA entity's table (orders, telegram_user, user_role, status_history,
     * delivery_assignment, shipper_profile, location_ping, rating, conversation_state, payment...).
     * Without the broad {@code @EntityScan}, {@code @DataJpaTest} would only scan the test
     * package's entities and leave cross-module joins ("relation 'orders' does not exist").
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.shop.delivery")
    @EnableJpaRepositories(basePackages = "com.shop.delivery")
    static class TestConfig {}

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("delivery_reports_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired ReportsRepository repo;
    @PersistenceContext EntityManager em;

    @Test
    void revenueLast7Days_returnsSevenRowsEvenWhenSparse() {
        // Seed: one DELIVERED order today
        seedOrder(LocalDate.now(), new BigDecimal("500000"), OrderStatus.DELIVERED);

        List<ReportsRepository.RevenuePointRow> rows = repo.revenueLast7Days();

        assertThat(rows).hasSize(7);
        // last row is today
        assertThat(rows.get(6).getBucketDate()).isEqualTo(LocalDate.now());
        assertThat(rows.get(6).getRevenue()).isEqualByComparingTo("500000");
        assertThat(rows.get(6).getOrderCount()).isEqualTo(1L);
        // earlier rows are zero
        assertThat(rows.get(0).getRevenue()).isEqualByComparingTo("0");
        assertThat(rows.get(0).getOrderCount()).isEqualTo(0L);
    }

    @Test
    void revenueSeries_dayBucket_groupsByDate() {
        LocalDate today = LocalDate.now();
        seedOrder(today, new BigDecimal("100000"), OrderStatus.DELIVERED);
        seedOrder(today, new BigDecimal("200000"), OrderStatus.DELIVERED);
        seedOrder(today.minusDays(1), new BigDecimal("50000"), OrderStatus.DELIVERED);

        List<ReportsRepository.RevenuePointRow> rows = repo.revenueSeries(
            today.minusDays(2), today, "day");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getRevenue()).isEqualByComparingTo("50000");
        assertThat(rows.get(1).getRevenue()).isEqualByComparingTo("300000");
    }

    @Test
    void revenueSeries_weekBucket_groupsByIsoWeek() {
        LocalDate monday = LocalDate.of(2026, 5, 25); // Monday
        seedOrder(monday,            new BigDecimal("100000"), OrderStatus.DELIVERED);
        seedOrder(monday.plusDays(3), new BigDecimal("200000"), OrderStatus.DELIVERED);
        seedOrder(monday.plusDays(7), new BigDecimal("400000"), OrderStatus.DELIVERED); // next week

        List<ReportsRepository.RevenuePointRow> rows = repo.revenueSeries(
            monday, monday.plusDays(13), "week");

        assertThat(rows).hasSize(2);
        // Week 1: 300_000
        assertThat(rows.get(0).getRevenue()).isEqualByComparingTo("300000");
        // Week 2: 400_000
        assertThat(rows.get(1).getRevenue()).isEqualByComparingTo("400000");
    }

    @Test
    void revenueSeries_excludesNonDeliveredOrders() {
        LocalDate today = LocalDate.now();
        seedOrder(today, new BigDecimal("100000"), OrderStatus.DELIVERED);
        seedOrder(today, new BigDecimal("999999"), OrderStatus.CANCELLED);  // ignored

        List<ReportsRepository.RevenuePointRow> rows = repo.revenueSeries(
            today.minusDays(1), today, "day");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRevenue()).isEqualByComparingTo("100000");
    }

    @Test
    void cancellationTotals_countsTotalAndCancelled() {
        LocalDate today = LocalDate.now();
        seedOrder(today, new BigDecimal("100000"), OrderStatus.DELIVERED);
        seedOrder(today, new BigDecimal("100000"), OrderStatus.CANCELLED);
        seedOrder(today, new BigDecimal("100000"), OrderStatus.CANCELLED);

        var t = repo.cancellationTotals(today.minusDays(1), today);
        assertThat(t.getTotalOrders()).isEqualTo(3L);
        assertThat(t.getCancelledCount()).isEqualTo(2L);
    }

    @Test
    void emptyRange_returnsEmptyOrZero() {
        LocalDate today = LocalDate.now();
        List<ReportsRepository.RevenuePointRow> rows = repo.revenueSeries(
            today.minusDays(5), today.minusDays(4), "day");
        assertThat(rows).isEmpty();

        var t = repo.cancellationTotals(today.minusDays(5), today.minusDays(4));
        assertThat(t.getTotalOrders()).isEqualTo(0L);
        assertThat(t.getCancelledCount()).isEqualTo(0L);
    }

    // ---- helpers ----

    private void seedOrder(LocalDate date, BigDecimal total, OrderStatus status) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCode("DH-" + System.nanoTime());
        o.setCustomerId(1001L);
        o.setStatus(status);
        o.setPaymentMethod(PaymentMethod.COD);
        o.setPaymentStatus(PaymentStatus.PENDING);
        o.setSubtotal(total);
        o.setDeliveryFee(BigDecimal.ZERO);
        o.setTotal(total);
        // pickup / delivery / distance — required NOT NULL columns
        o.setPickupLat(new BigDecimal("10.7769"));
        o.setPickupLng(new BigDecimal("106.7009"));
        o.setDeliveryAddress("Test address");
        o.setDeliveryLat(new BigDecimal("10.7800"));
        o.setDeliveryLng(new BigDecimal("106.7050"));
        o.setDistanceKm(new BigDecimal("1.500"));
        // Inject created_at — Order extends BaseEntity which has @CreatedDate; we must override.
        // After persisting, update created_at via raw SQL to backdate.
        em.persist(o);
        em.flush();
        // `em` is the standard JPA EntityManager (NOT Spring Boot's TestEntityManager wrapper).
        // Use `em.createNativeQuery(...)` directly — there is no `getEntityManager()` indirection.
        em.createNativeQuery(
            "UPDATE orders SET created_at = :ts WHERE id = :id")
            .setParameter("ts", Instant.ofEpochSecond(date.toEpochSecond(java.time.LocalTime.NOON, ZoneOffset.UTC)))
            .setParameter("id", o.getId())
            .executeUpdate();
        em.clear();
    }
}
