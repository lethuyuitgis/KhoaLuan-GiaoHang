package com.shop.delivery.order.repository;

import com.shop.delivery.order.entity.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    List<StatusHistory> findAllByOrderIdOrderByChangedAtAsc(UUID orderId);
}
