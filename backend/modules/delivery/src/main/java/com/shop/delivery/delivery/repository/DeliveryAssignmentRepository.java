package com.shop.delivery.delivery.repository;

import com.shop.delivery.delivery.domain.AssignmentStatus;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {

    Optional<DeliveryAssignment> findByOrderId(UUID orderId);

    Page<DeliveryAssignment> findAllByShipperIdAndStatusInOrderByAssignedAtDesc(
        Long shipperId, List<AssignmentStatus> statuses, Pageable pageable);

    List<DeliveryAssignment> findAllByShipperIdAndStatusIn(Long shipperId, List<AssignmentStatus> statuses);

    List<DeliveryAssignment> findAllByShipperIdAndStatusInOrderByAssignedAtDesc(
        Long shipperId, List<AssignmentStatus> statuses);

    boolean existsByShipperIdAndStatus(Long shipperId, AssignmentStatus status);
}
