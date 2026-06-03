package com.shop.delivery.order.repository;

import com.shop.delivery.order.entity.ShopConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopConfigRepository extends JpaRepository<ShopConfig, Short> {
    /** Singleton row always has id = 1. */
    short SINGLETON_ID = 1;
}
