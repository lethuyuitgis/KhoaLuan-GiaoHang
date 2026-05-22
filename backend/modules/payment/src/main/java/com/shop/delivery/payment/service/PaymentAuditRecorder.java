package com.shop.delivery.payment.service;

import com.shop.delivery.payment.domain.PaymentEventType;
import com.shop.delivery.payment.entity.PaymentTransaction;
import com.shop.delivery.payment.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only writer of {@code payment_transaction} rows. Used by every public
 * endpoint of {@code VnpayController} to leave a JSONB audit breadcrumb.
 *
 * <p>Always copies the payload to insulate the stored row from later mutation
 * by the caller (e.g. if the caller adds extra fields after auditing).
 */
@Component
public class PaymentAuditRecorder {

    private final PaymentTransactionRepository repo;

    public PaymentAuditRecorder(PaymentTransactionRepository repo) {
        this.repo = repo;
    }

    public void record(UUID paymentId, PaymentEventType type, Map<String, String> payload) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setPaymentId(paymentId);
        tx.setEventType(type);
        // Defensive copy — keep ordering for nicer JSONB inspection
        tx.setRawPayload(new LinkedHashMap<>(payload));
        tx.setRecordedAt(Instant.now());
        repo.save(tx);
    }
}
