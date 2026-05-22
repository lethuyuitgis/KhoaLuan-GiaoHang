package com.shop.delivery.payment.repository;

import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** IPN idempotency lookup. */
    Optional<Payment> findByVnpTxnRef(String vnpTxnRef);

    /** Used by {@code POST /create} to mark prior PENDING attempts as SUPERSEDED. */
    List<Payment> findAllByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    /** Sweeper query — PENDING payments older than {@code cutoff}. */
    @Query("""
        SELECT p FROM Payment p
        WHERE p.status = com.shop.delivery.order.domain.PaymentStatus.PENDING
          AND p.createdAt < :cutoff
        """)
    List<Payment> findStalePending(@Param("cutoff") Instant cutoff);
}
