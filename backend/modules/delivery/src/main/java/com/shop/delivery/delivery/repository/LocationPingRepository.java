package com.shop.delivery.delivery.repository;

import com.shop.delivery.delivery.entity.LocationPing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LocationPingRepository extends JpaRepository<LocationPing, Long> {
    Optional<LocationPing> findFirstByAssignmentIdOrderByRecordedAtDesc(UUID assignmentId);
}
