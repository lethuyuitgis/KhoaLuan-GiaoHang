package com.shop.delivery.promotion.service;

import com.shop.delivery.promotion.domain.DiscountType;
import com.shop.delivery.promotion.domain.VoucherTarget;
import com.shop.delivery.promotion.entity.Voucher;
import com.shop.delivery.promotion.repository.VoucherRepository;
import com.shop.delivery.promotion.support.PromotionTestcontainerBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherRaceConditionIT extends PromotionTestcontainerBase {

    @Autowired VoucherRepository repo;
    @Autowired VoucherService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void twoConcurrentRedemptions_onLastSlot_onlyOneSucceeds() throws InterruptedException {
        // Seed customer + 2 distinct orders (one per thread) so the redeem path has valid FKs.
        jdbc.update("INSERT INTO telegram_user (id, first_name, language_code, created_at, updated_at) " +
                    "VALUES (7777, 'Race', 'vi', NOW(), NOW()) ON CONFLICT (id) DO NOTHING");
        UUID o1 = seedOrder(jdbc, "RACE1");
        UUID o2 = seedOrder(jdbc, "RACE2");

        // Delete any leftover LASTSLOT voucher from a previous run
        jdbc.update("DELETE FROM voucher_redemption WHERE voucher_id IN (SELECT id FROM voucher WHERE code = 'LASTSLOT')");
        jdbc.update("DELETE FROM voucher WHERE code = 'LASTSLOT'");

        Voucher v = new Voucher();
        v.setCode("LASTSLOT");
        v.setName("Last slot");
        v.setTarget(VoucherTarget.PRODUCTS);
        v.setDiscountType(DiscountType.FIXED);
        v.setDiscountValue(new BigDecimal("10000"));
        v.setMaxUsesTotal(1);
        v.setMaxUsesPerCustomer(2);
        v.setValidFrom(OffsetDateTime.now().minusDays(1));
        v.setValidUntil(OffsetDateTime.now().plusDays(1));
        repo.saveAndFlush(v);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Runnable t1 = () -> attempt(start, done, successes, failures, o1);
        Runnable t2 = () -> attempt(start, done, successes, failures, o2);
        pool.submit(t1);
        pool.submit(t2);
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);

        Voucher after = repo.findById(v.getId()).orElseThrow();
        assertThat(after.getUsedCount()).isEqualTo(1);
    }

    private void attempt(CountDownLatch start, CountDownLatch done,
                         AtomicInteger ok, AtomicInteger fail, UUID orderId) {
        try {
            start.await();
            service.redeem("LASTSLOT", orderId, 7777L, new BigDecimal("10000"));
            ok.incrementAndGet();
        } catch (Exception e) {
            fail.incrementAndGet();
        } finally {
            done.countDown();
        }
    }

    static UUID seedOrder(JdbcTemplate jdbc, String codeSuffix) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO orders (id, code, customer_id, customer_name, customer_phone, " +
                    "pickup_lat, pickup_lng, delivery_address, delivery_lat, delivery_lng, distance_km, " +
                    "subtotal, delivery_fee, total, payment_method, payment_status, status, " +
                    "delivery_fee_original, created_at, updated_at) " +
                    "VALUES (?, ?, 7777, 'Race', '0123456789', 21.0, 105.8, 'Test', 21.0, 105.8, 0, " +
                    "100000, 30000, 130000, 'COD', 'PENDING', 'PENDING', 30000, NOW(), NOW())",
                    id, "RACE-" + codeSuffix + "-" + id.toString().substring(0, 6));
        return id;
    }
}
