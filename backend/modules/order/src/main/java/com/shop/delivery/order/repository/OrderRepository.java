package com.shop.delivery.order.repository;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByCode(String code);

    Page<Order> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<Order> findAllByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    boolean existsByCode(String code);

    /** [status, count] cho các tab lọc của trang admin. */
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countGroupByStatusRaw();

    default List<Object[]> countGroupByStatus() {
        return countGroupByStatusRaw().stream()
            .map(r -> new Object[]{ ((OrderStatus) r[0]).name(), (Long) r[1] })
            .toList();
    }
}
