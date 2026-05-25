package com.shop.delivery.payment.service;

import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.payment.config.VnpayProperties;
import com.shop.delivery.payment.entity.Payment;
import com.shop.delivery.payment.repository.PaymentRepository;
import com.shop.delivery.shared.event.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Sweeps PENDING payments older than the VNPay timeout and marks them FAILED.
 *
 * <p>Runs every 60 seconds with an initial 60s delay (warm-up). The threshold
 * is {@code vnpay.timeout-minutes} (default 15 from VNPay's own expiry window).
 *
 * <p>Emits {@link PaymentFailedEvent} per expired row so future P8 listeners
 * can notify the customer. Order state is NOT touched here — the customer can
 * still retry payment (a new {@code Payment} row will be created via
 * {@code POST /create}); the failed sweeper row exists only for audit.
 */
@Component
public class PaymentExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpiryScheduler.class);

    private final PaymentRepository paymentRepo;
    private final VnpayProperties props;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PaymentExpiryScheduler(PaymentRepository paymentRepo,
                                  VnpayProperties props,
                                  ApplicationEventPublisher events,
                                  Clock clock) {
        this.paymentRepo = paymentRepo;
        this.props = props;
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public void expireStalePending() {
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(props.timeoutMinutes()));
        List<Payment> stale = paymentRepo.findStalePending(cutoff);
        if (stale.isEmpty()) return;

        List<Payment> toEmit = new ArrayList<>();
        for (Payment p : stale) {
            // Belt-and-braces: ignore anything the query somehow returned that isn't PENDING
            if (p.getStatus() != PaymentStatus.PENDING) continue;
            p.setStatus(PaymentStatus.FAILED);
            p.setVnpResponseCode("EXPIRED");
            toEmit.add(p);
        }
        if (toEmit.isEmpty()) return;

        paymentRepo.saveAll(toEmit);
        for (Payment p : toEmit) {
            events.publishEvent(new PaymentFailedEvent(
                p.getOrderId(), p.getId(), p.getVnpTxnRef(), "EXPIRED"));
        }
        log.info("PaymentExpiryScheduler: expired {} stale PENDING payments", toEmit.size());
    }
}
