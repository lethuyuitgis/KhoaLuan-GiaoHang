package com.shop.delivery.order.repository;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByCode(String code);

    Page<Order> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<Order> findAllByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    boolean existsByCode(String code);
}
