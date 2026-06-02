package com.shop.delivery.delivery.repository;

import com.shop.delivery.delivery.entity.ShipperRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ShipperRatingRepository extends JpaRepository<ShipperRating, Long> {

    boolean existsByOrderId(UUID orderId);

    Optional<ShipperRating> findByOrderId(UUID orderId);

    /**
     * Aggregate AVG and COUNT for a customer. Mirror of
     * {@code RatingRepository#aggregateForShipper}. Native query for exact
     * Postgres AVG semantics over SMALLINT.
     */
    @Query(value = """
        SELECT COALESCE(AVG(stars), 0) AS avg, COUNT(*) AS count
        FROM shipper_rating
        WHERE customer_id = :customerId
        """, nativeQuery = true)
    CustomerRatingStats aggregateForCustomer(@Param("customerId") Long customerId);

    interface CustomerRatingStats {
        BigDecimal getAvg();
        Integer getCount();
    }
}
