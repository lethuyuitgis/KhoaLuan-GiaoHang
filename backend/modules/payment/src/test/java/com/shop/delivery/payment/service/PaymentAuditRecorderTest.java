package com.shop.delivery.payment.service;

import com.shop.delivery.payment.domain.PaymentEventType;
import com.shop.delivery.payment.entity.PaymentTransaction;
import com.shop.delivery.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentAuditRecorderTest {

    @Mock PaymentTransactionRepository repo;

    @Test
    void recordsTransactionWithCopiedPayload() {
        PaymentAuditRecorder rec = new PaymentAuditRecorder(repo);
        UUID paymentId = UUID.randomUUID();
        Map<String, String> payload = Map.of(
            "vnp_TxnRef", "DH-1",
            "vnp_Amount", "25000000"
        );

        rec.record(paymentId, PaymentEventType.IPN, payload);

        ArgumentCaptor<PaymentTransaction> cap = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(repo).save(cap.capture());
        PaymentTransaction tx = cap.getValue();
        assertThat(tx.getPaymentId()).isEqualTo(paymentId);
        assertThat(tx.getEventType()).isEqualTo(PaymentEventType.IPN);
        assertThat(tx.getRawPayload()).containsEntry("vnp_TxnRef", "DH-1");
        assertThat(tx.getRecordedAt()).isNotNull();
    }

    @Test
    void recordsDoesNotMutateOriginalPayload() {
        PaymentAuditRecorder rec = new PaymentAuditRecorder(repo);
        UUID paymentId = UUID.randomUUID();
        Map<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("vnp_TxnRef", "DH-1");

        rec.record(paymentId, PaymentEventType.CREATE, payload);
        payload.put("vnp_Mutated", "yes");

        ArgumentCaptor<PaymentTransaction> cap = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(repo).save(cap.capture());
        // The saved payload must be a defensive copy — not the live map.
        assertThat(cap.getValue().getRawPayload()).doesNotContainKey("vnp_Mutated");
    }
}
