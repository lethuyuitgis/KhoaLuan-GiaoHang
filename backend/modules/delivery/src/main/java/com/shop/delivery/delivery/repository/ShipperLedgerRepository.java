package com.shop.delivery.delivery.repository;

import com.shop.delivery.delivery.domain.LedgerEntryType;
import com.shop.delivery.delivery.entity.ShipperLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ShipperLedgerRepository extends JpaRepository<ShipperLedgerEntry, Long> {

    Page<ShipperLedgerEntry> findByShipperIdOrderByCreatedAtDesc(Long shipperId, Pageable pageable);

    List<ShipperLedgerEntry> findByShipperIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        Long shipperId, OffsetDateTime from, OffsetDateTime to);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM ShipperLedgerEntry e WHERE e.shipperId = :shipperId")
    BigDecimal sumAmountByShipperId(@Param("shipperId") Long shipperId);

    boolean existsByOrderIdAndEntryType(UUID orderId, LedgerEntryType entryType);

    @Query(value = """
        SELECT COALESCE(NULLIF(TRIM(CONCAT(u.first_name, ' ', COALESCE(u.last_name, ''))), ''),
                        'Shipper #' || l.shipper_id::text) AS group_key,
               COUNT(*) FILTER (WHERE l.entry_type='COMMISSION') AS orders_count,
               COALESCE(SUM(l.amount) FILTER (WHERE l.entry_type='COMMISSION'), 0) AS commission
        FROM shipper_ledger l
        JOIN telegram_user u ON u.id = l.shipper_id
        WHERE l.created_at BETWEEN :from AND :to
        GROUP BY l.shipper_id, u.first_name, u.last_name
        ORDER BY commission DESC
    """, nativeQuery = true)
    List<Object[]> aggregateByShipper(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(value = """
        SELECT to_char(DATE_TRUNC('day', created_at), 'YYYY-MM-DD') AS group_key,
               COUNT(*) FILTER (WHERE entry_type='COMMISSION') AS orders_count,
               COALESCE(SUM(amount) FILTER (WHERE entry_type='COMMISSION'), 0) AS commission
        FROM shipper_ledger
        WHERE created_at BETWEEN :from AND :to
        GROUP BY DATE_TRUNC('day', created_at) ORDER BY DATE_TRUNC('day', created_at)
    """, nativeQuery = true)
    List<Object[]> aggregateByDay(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
