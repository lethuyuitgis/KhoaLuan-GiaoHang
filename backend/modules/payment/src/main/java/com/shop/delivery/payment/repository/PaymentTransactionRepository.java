package com.shop.delivery.payment.repository;

import com.shop.delivery.payment.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findAllByPaymentIdOrderByRecordedAtAsc(UUID paymentId);
}
