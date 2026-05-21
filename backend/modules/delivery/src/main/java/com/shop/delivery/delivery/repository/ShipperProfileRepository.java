package com.shop.delivery.delivery.repository;

import com.shop.delivery.delivery.domain.ShipperState;
import com.shop.delivery.delivery.entity.ShipperProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipperProfileRepository extends JpaRepository<ShipperProfile, Long> {
    List<ShipperProfile> findAllByCurrentState(ShipperState state);
}
