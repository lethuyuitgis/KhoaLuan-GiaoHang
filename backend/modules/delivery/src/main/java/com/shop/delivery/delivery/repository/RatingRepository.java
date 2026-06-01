package com.shop.delivery.delivery.repository;

import com.shop.delivery.delivery.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    /** Idempotency check (used by notifier and {@code RatingService}). */
    boolean existsByOrderId(UUID orderId);

    /** Used by {@code RatingService.updateComment} and by integration tests. */
    Optional<Rating> findByOrderId(UUID orderId);

    /**
     * Aggregate AVG and COUNT for a shipper. Native query because we want exact
     * Postgres AVG semantics over SMALLINT (returns NUMERIC). COALESCE handles the
     * "no ratings yet" case → AVG = 0.
     *
     * <p>Called by {@code RatingService.rate} after every insert; must be cheap.
     * Index {@code idx_rating_shipper_created (shipper_id, created_at DESC)} backs
     * the {@code WHERE shipper_id = ?} predicate.
     */
    @Query(value = """
        SELECT COALESCE(AVG(stars), 0) AS avg, COUNT(*) AS count
        FROM rating
        WHERE shipper_id = :shipperId
        """, nativeQuery = true)
    RatingStats aggregateForShipper(@Param("shipperId") Long shipperId);

    interface RatingStats {
        BigDecimal getAvg();
        Integer getCount();
    }
}
