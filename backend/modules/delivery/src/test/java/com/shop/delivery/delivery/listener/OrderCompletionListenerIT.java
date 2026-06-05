package com.shop.delivery.delivery.listener;

import com.shop.delivery.delivery.domain.LedgerEntryType;
import com.shop.delivery.delivery.entity.ShipperLedgerEntry;
import com.shop.delivery.delivery.repository.ShipperLedgerRepository;
import com.shop.delivery.delivery.service.event.OrderDeliveredEvent;
import com.shop.delivery.delivery.support.DeliveryListenerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCompletionListenerIT extends DeliveryListenerTestBase {

    @Autowired ShipperLedgerRepository ledgerRepo;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestEventPublisher publisher;

    @BeforeEach
    void cleanSlate() {
        jdbc.update("DELETE FROM shipper_ledger");
        jdbc.update("DELETE FROM orders WHERE code LIKE 'TEST-%'");
    }

    @Test
    void cod_order_delivered_creates_commission_and_codOwed_entries() {
        Long shipperId = seedShipper("90001");
        UUID orderId = seedOrder(shipperId, "COD", "30000", "30000", "0", "130000");

        publisher.publish(new OrderDeliveredEvent(
            UUID.randomUUID(), orderId, "TEST-COD-1", shipperId, 99001L));

        List<ShipperLedgerEntry> entries = ledgerRepo
            .findByShipperIdOrderByCreatedAtDesc(shipperId, Pageable.unpaged()).getContent();
        assertThat(entries).hasSize(2);
        assertThat(entries.stream().map(ShipperLedgerEntry::getEntryType))
            .containsExactlyInAnyOrder(LedgerEntryType.COD_OWED, LedgerEntryType.COMMISSION);
        var commission = entries.stream()
            .filter(e -> e.getEntryType() == LedgerEntryType.COMMISSION).findFirst().orElseThrow();
        var cod = entries.stream()
            .filter(e -> e.getEntryType() == LedgerEntryType.COD_OWED).findFirst().orElseThrow();
        assertThat(commission.getAmount()).isEqualByComparingTo("24000");
        assertThat(cod.getAmount()).isEqualByComparingTo("-130000");
    }

    @Test
    void vnpay_order_delivered_creates_only_commission_entry() {
        Long shipperId = seedShipper("90002");
        UUID orderId = seedOrder(shipperId, "VNPAY", "30000", "30000", "0", "130000");
        publisher.publish(new OrderDeliveredEvent(
            UUID.randomUUID(), orderId, "TEST-VNPAY-1", shipperId, 99002L));

        var entries = ledgerRepo
            .findByShipperIdOrderByCreatedAtDesc(shipperId, Pageable.unpaged()).getContent();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getEntryType()).isEqualTo(LedgerEntryType.COMMISSION);
    }

    @Test
    void commission_computed_on_delivery_fee_original_not_post_voucher() {
        Long shipperId = seedShipper("90003");
        // delivery_fee=10000 (after SHIPPING voucher), delivery_fee_original=30000, discount_shipping=20000
        UUID orderId = seedOrder(shipperId, "COD", "10000", "30000", "20000", "110000");
        publisher.publish(new OrderDeliveredEvent(
            UUID.randomUUID(), orderId, "TEST-VOUCHER-1", shipperId, 99003L));

        var entries = ledgerRepo
            .findByShipperIdOrderByCreatedAtDesc(shipperId, Pageable.unpaged()).getContent();
        var commission = entries.stream()
            .filter(e -> e.getEntryType() == LedgerEntryType.COMMISSION).findFirst().orElseThrow();
        // 30000 × 80% = 24000, NOT 10000 × 80% = 8000
        assertThat(commission.getAmount()).isEqualByComparingTo("24000");
    }

    @Test
    void duplicate_event_does_not_double_commission() {
        Long shipperId = seedShipper("90004");
        UUID orderId = seedOrder(shipperId, "COD", "30000", "30000", "0", "130000");
        var event = new OrderDeliveredEvent(
            UUID.randomUUID(), orderId, "TEST-DUP-1", shipperId, 99004L);
        publisher.publish(event);
        publisher.publish(event);  // fire twice — idempotency guard must prevent double entry

        long commissionCount = ledgerRepo
            .findByShipperIdOrderByCreatedAtDesc(shipperId, Pageable.unpaged())
            .stream()
            .filter(e -> e.getEntryType() == LedgerEntryType.COMMISSION)
            .count();
        assertThat(commissionCount).isEqualTo(1);
    }

    @Test
    void commission_snapshot_set_on_orders_table() {
        Long shipperId = seedShipper("90005");
        UUID orderId = seedOrder(shipperId, "VNPAY", "30000", "30000", "0", "130000");
        publisher.publish(new OrderDeliveredEvent(
            UUID.randomUUID(), orderId, "TEST-SNAP-1", shipperId, 99005L));

        BigDecimal snapshot = jdbc.queryForObject(
            "SELECT shipper_commission FROM orders WHERE id = ?",
            BigDecimal.class, orderId);
        assertThat(snapshot).isEqualByComparingTo("24000");
    }

    // === Seed helpers ===

    private Long seedShipper(String idStr) {
        Long id = Long.parseLong(idStr);
        jdbc.update(
            "INSERT INTO telegram_user (id, first_name, language_code, created_at, updated_at) " +
            "VALUES (?, 'TestShipper', 'vi', NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
            id);
        jdbc.update(
            "INSERT INTO shipper_profile (user_id, vehicle_type, current_state, updated_at) " +
            "VALUES (?, 'MOTORBIKE', 'OFFLINE', NOW()) ON CONFLICT (user_id) DO NOTHING",
            id);
        return id;
    }

    private UUID seedOrder(Long shipperId, String paymentMethod,
                           String deliveryFee, String deliveryFeeOriginal,
                           String discountShipping, String total) {
        UUID id = UUID.randomUUID();
        Long customerId = 99000L + shipperId;
        jdbc.update(
            "INSERT INTO telegram_user (id, first_name, language_code, created_at, updated_at) " +
            "VALUES (?, 'TestCustomer', 'vi', NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
            customerId);
        jdbc.update(
            "INSERT INTO orders (id, code, customer_id, customer_name, customer_phone, " +
            "pickup_lat, pickup_lng, delivery_address, delivery_lat, delivery_lng, distance_km, " +
            "subtotal, delivery_fee, total, payment_method, payment_status, status, " +
            "delivery_fee_original, discount_products, discount_shipping, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'Test', '0123456789', 21.0, 105.8, 'Test Addr', 21.0, 105.8, 0, " +
            "100000, ?::numeric, ?::numeric, ?, 'PENDING', 'DELIVERED', ?::numeric, 0, ?::numeric, NOW(), NOW())",
            id,
            "TEST-" + paymentMethod + "-" + id.toString().substring(0, 6),
            customerId,
            deliveryFee,
            total,
            paymentMethod,
            deliveryFeeOriginal,
            discountShipping);
        return id;
    }
}

@Component
class TestEventPublisher {
    @Autowired ApplicationEventPublisher events;

    /**
     * Wraps publishEvent in @Transactional so that the AFTER_COMMIT phase fires.
     * The TransactionalEventListener only runs once this method's transaction commits.
     */
    @Transactional
    public void publish(OrderDeliveredEvent e) {
        events.publishEvent(e);
    }
}
