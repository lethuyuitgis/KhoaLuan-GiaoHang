package com.shop.delivery.delivery.repository;

import com.shop.delivery.delivery.entity.LocationPing;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationPingRepository extends JpaRepository<LocationPing, Long> {
    Optional<LocationPing> findFirstByAssignmentIdOrderByRecordedAtDesc(UUID assignmentId);

    /**
     * Most-recent location pings for a shipper across all of their assignments,
     * newest first. Location is stored per-assignment, so a shipper's last-known
     * position is the latest ping of any assignment they were on. Call with
     * {@code PageRequest.of(0, 1)} to fetch just the latest.
     */
    @Query("SELECT lp FROM LocationPing lp "
        + "WHERE lp.assignmentId IN (SELECT da.id FROM DeliveryAssignment da WHERE da.shipperId = :shipperId) "
        + "ORDER BY lp.recordedAt DESC")
    List<LocationPing> findLatestByShipper(@Param("shipperId") Long shipperId, Pageable pageable);
}
