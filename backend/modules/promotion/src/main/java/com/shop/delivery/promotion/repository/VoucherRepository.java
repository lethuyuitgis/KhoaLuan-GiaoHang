package com.shop.delivery.promotion.repository;

import com.shop.delivery.promotion.entity.Voucher;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    Optional<Voucher> findByCodeIgnoreCaseAndActiveTrue(String code);

    /** Pessimistic lock so two simultaneous redemptions can't both pass the max_uses_total check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voucher v WHERE v.code = :code")
    Optional<Voucher> findByCodeForUpdate(@Param("code") String code);

    Page<Voucher> findAll(Pageable pageable);
}
