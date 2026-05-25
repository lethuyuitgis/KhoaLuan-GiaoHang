package com.shop.delivery.payment.service;

import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.payment.config.VnpayProperties;
import com.shop.delivery.payment.entity.Payment;
import com.shop.delivery.payment.repository.PaymentRepository;
import com.shop.delivery.shared.event.PaymentFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentExpirySchedulerTest {

    @Mock PaymentRepository repo;
    @Mock ApplicationEventPublisher events;

    private VnpayProperties props;
    private Clock fixedClock;
    private PaymentExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        props = new VnpayProperties("T", "S", "u1", "u2", "u3", 15);
        // Time = 2026-05-22T10:30:00Z → cutoff = 10:15:00Z
        fixedClock = Clock.fixed(Instant.parse("2026-05-22T10:30:00Z"), ZoneId.of("UTC"));
        scheduler = new PaymentExpiryScheduler(repo, props, events, fixedClock);
    }

    @Test
    void expireStalePending_marksFAILED_andEmitsPaymentFailedEvent() {
        Payment stale = pending(Instant.parse("2026-05-22T10:10:00Z"));
        when(repo.findStalePending(Instant.parse("2026-05-22T10:15:00Z")))
            .thenReturn(List.of(stale));

        scheduler.expireStalePending();

        assertThat(stale.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(stale.getVnpResponseCode()).isEqualTo("EXPIRED");
        verify(repo).saveAll(List.of(stale));

        ArgumentCaptor<PaymentFailedEvent> ev = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().responseCode()).isEqualTo("EXPIRED");
        assertThat(ev.getValue().paymentId()).isEqualTo(stale.getId());
    }

    @Test
    void expireStalePending_noStaleRows_noEventNoSave() {
        when(repo.findStalePending(any(Instant.class))).thenReturn(List.of());

        scheduler.expireStalePending();

        verify(repo, never()).saveAll(anyIterable());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void expireStalePending_doesNotTouchSUCCESSorFAILED() {
        // Repository.findStalePending is responsible for the WHERE status=PENDING filter,
        // so this test asserts the scheduler trusts the repo. We just verify it never
        // mutates rows that come back with a non-PENDING status if the repo misbehaves.
        Payment alreadyOk = pending(Instant.parse("2026-05-22T10:00:00Z"));
        alreadyOk.setStatus(PaymentStatus.SUCCESS);
        when(repo.findStalePending(any(Instant.class))).thenReturn(List.of(alreadyOk));

        scheduler.expireStalePending();

        // Scheduler should keep status SUCCESS, not flip it to FAILED.
        assertThat(alreadyOk.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(events, never()).publishEvent(any());
    }

    private Payment pending(Instant createdAt) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOrderId(UUID.randomUUID());
        p.setMethod(PaymentMethod.VNPAY);
        p.setAmount(new BigDecimal("250000.00"));
        p.setStatus(PaymentStatus.PENDING);
        p.setVnpTxnRef("DH-S-" + createdAt.toEpochMilli());
        // Note: BaseEntity.createdAt is package-protected setter; using reflection in a real
        // test, or simply skipping (the scheduler reads only via repo.findStalePending).
        return p;
    }

    private static <T> T any(Class<T> c) { return org.mockito.ArgumentMatchers.any(c); }
    private static Object any() { return org.mockito.ArgumentMatchers.any(); }
}
