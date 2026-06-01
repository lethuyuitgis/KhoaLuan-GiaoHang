package com.shop.delivery.delivery.repository;

import com.shop.delivery.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Native-SQL repository for admin dashboard + reports endpoints.
 * Uses interface projections (Spring Data idiom) — no @SqlResultSetMapping ceremony.
 *
 * <p>All "in this window" predicates use {@code created_at >= :from AND created_at < :to + INTERVAL '1 day'}
 * to make {@code to} inclusive at the day level (research Pitfall 1).
 *
 * <p>{@code groupBy} bucket is whitelisted by the controller to {@code 'day'|'week'} BEFORE
 * binding into {@code DATE_TRUNC} (research §3.2).
 */
public interface ReportsRepository extends JpaRepository<Order, java.util.UUID> {

    // ============================================================
    //  Dashboard summary
    // ============================================================

    @Query(value = """
        SELECT status AS status, COUNT(*) AS cnt
        FROM orders
        WHERE created_at >= CURRENT_DATE
          AND created_at <  CURRENT_DATE + INTERVAL '1 day'
        GROUP BY status
        """, nativeQuery = true)
    List<StatusCount> ordersTodayByStatus();

    interface StatusCount { String getStatus(); Long getCnt(); }

    @Query(value = """
        SELECT COALESCE(SUM(total), 0)
        FROM orders
        WHERE status = 'DELIVERED'
          AND created_at >= CURRENT_DATE
          AND created_at <  CURRENT_DATE + INTERVAL '1 day'
        """, nativeQuery = true)
    BigDecimal revenueToday();

    @Query(value = """
        SELECT COUNT(*) FROM shipper_profile
        WHERE current_state IN ('AVAILABLE', 'BUSY')
        """, nativeQuery = true)
    long activeShippers();

    @Query(value = """
        SELECT COUNT(DISTINCT u.id)
        FROM telegram_user u
        JOIN user_role r ON r.telegram_user_id = u.id
        WHERE r.role = 'CUSTOMER'
          AND u.created_at >= CURRENT_DATE
          AND u.created_at <  CURRENT_DATE + INTERVAL '1 day'
        """, nativeQuery = true)
    long newCustomersToday();

    /**
     * Last 7 days (today inclusive) revenue + order count from DELIVERED orders.
     * Uses {@code generate_series} so we always return 7 rows — even on days with
     * no deliveries (chart x-axis stays continuous).
     */
    @Query(value = """
        SELECT
            d::date AS bucket_date,
            COALESCE(SUM(o.total) FILTER (WHERE o.status = 'DELIVERED'), 0) AS revenue,
            COUNT(o.id) FILTER (WHERE o.status = 'DELIVERED') AS order_count
        FROM generate_series(
            CURRENT_DATE - INTERVAL '6 days',
            CURRENT_DATE,
            INTERVAL '1 day'
        ) AS d
        LEFT JOIN orders o
          ON o.created_at >= d
         AND o.created_at <  d + INTERVAL '1 day'
        GROUP BY d
        ORDER BY d
        """, nativeQuery = true)
    List<RevenuePointRow> revenueLast7Days();

    interface RevenuePointRow {
        LocalDate getBucketDate();
        BigDecimal getRevenue();
        Long getOrderCount();
    }

    // ============================================================
    //  Revenue time-series (reports/revenue)
    // ============================================================

    /**
     * @param bucket WHITELISTED upstream — only 'day' or 'week' allowed.
     */
    @Query(value = """
        SELECT
            DATE_TRUNC(:bucket, o.created_at)::date AS bucket_date,
            COALESCE(SUM(o.total), 0) AS revenue,
            COUNT(*) AS order_count
        FROM orders o
        WHERE o.created_at >= :from
          AND o.created_at <  :to + INTERVAL '1 day'
          AND o.status = 'DELIVERED'
        GROUP BY DATE_TRUNC(:bucket, o.created_at)
        ORDER BY DATE_TRUNC(:bucket, o.created_at)
        """, nativeQuery = true)
    List<RevenuePointRow> revenueSeries(@Param("from") LocalDate from,
                                        @Param("to")   LocalDate to,
                                        @Param("bucket") String bucket);

    // ============================================================
    //  Top shippers (reports/top-shippers)
    // ============================================================

    @Query(value = """
        SELECT
            sp.user_id                AS shipper_id,
            u.first_name              AS first_name,
            u.last_name               AS last_name,
            u.username                AS username,
            COUNT(o.id)               AS delivered_count,
            COALESCE(SUM(o.total), 0) AS revenue_generated,
            sp.rating_avg             AS rating_avg
        FROM shipper_profile sp
        JOIN telegram_user u ON u.id = sp.user_id
        LEFT JOIN delivery_assignment a
               ON a.shipper_id = sp.user_id
              AND a.status = 'COMPLETED'
              AND a.delivered_at >= :from
              AND a.delivered_at <  :to + INTERVAL '1 day'
        LEFT JOIN orders o
               ON o.id = a.order_id
              AND o.status = 'DELIVERED'
        GROUP BY sp.user_id, u.first_name, u.last_name, u.username, sp.rating_avg
        ORDER BY COUNT(o.id) DESC, sp.rating_avg DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<TopShipperRowRaw> topShippers(@Param("from") LocalDate from,
                                       @Param("to")   LocalDate to,
                                       @Param("limit") int limit);

    interface TopShipperRowRaw {
        Long getShipperId();
        String getFirstName();
        String getLastName();
        String getUsername();
        Long getDeliveredCount();
        BigDecimal getRevenueGenerated();
        BigDecimal getRatingAvg();
    }

    // ============================================================
    //  Cancellation (reports/cancellation)
    // ============================================================

    @Query(value = """
        SELECT
            COUNT(*)                                       AS total_orders,
            COUNT(*) FILTER (WHERE status = 'CANCELLED')   AS cancelled_count
        FROM orders
        WHERE created_at >= :from
          AND created_at <  :to + INTERVAL '1 day'
        """, nativeQuery = true)
    CancellationTotals cancellationTotals(@Param("from") LocalDate from,
                                          @Param("to")   LocalDate to);

    interface CancellationTotals {
        Long getTotalOrders();
        Long getCancelledCount();
    }

    @Query(value = """
        SELECT
            COALESCE(NULLIF(TRIM(sh.note), ''), 'Không ghi lý do') AS reason,
            COUNT(*) AS cnt
        FROM status_history sh
        JOIN orders o ON o.id = sh.order_id
        WHERE sh.to_status = 'CANCELLED'
          AND o.created_at >= :from
          AND o.created_at <  :to + INTERVAL '1 day'
        GROUP BY 1
        ORDER BY cnt DESC
        LIMIT 10
        """, nativeQuery = true)
    List<ReasonCount> cancellationByReason(@Param("from") LocalDate from,
                                           @Param("to")   LocalDate to);

    interface ReasonCount { String getReason(); Long getCnt(); }
}
