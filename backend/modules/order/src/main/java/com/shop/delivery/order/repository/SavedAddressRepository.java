package com.shop.delivery.order.repository;

import com.shop.delivery.order.entity.SavedAddress;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SavedAddressRepository extends JpaRepository<SavedAddress, Long> {

    List<SavedAddress> findByCustomerIdOrderByLastUsedAtDesc(Long customerId, Pageable pageable);

    Optional<SavedAddress> findByCustomerIdAndLatAndLng(Long customerId, BigDecimal lat, BigDecimal lng);
}
