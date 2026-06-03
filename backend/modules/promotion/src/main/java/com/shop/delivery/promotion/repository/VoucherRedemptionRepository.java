package com.shop.delivery.promotion.repository;

import com.shop.delivery.promotion.entity.VoucherRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemption, Long> {

    int countByVoucherIdAndCustomerId(Long voucherId, Long customerId);
}
