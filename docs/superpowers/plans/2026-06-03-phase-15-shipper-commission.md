# Phase 15 — Shipper Commission & Earnings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When an order is `DELIVERED`, automatically compute shipper commission (% of original delivery fee — from `shop_config`), append a ledger entry, optionally add a COD-owed entry, and surface earnings to shipper (Mini App: Earnings / Wallet / Profile + redesigned Đơn pages with bottom tab nav) and admin (Web Admin: Earnings report + ShipperDetail with settle button).

**Architecture:** Listener on `OrderDeliveredEvent` (already published by delivery module) writes append-only entries to `shipper_ledger` in the same transaction-after-commit phase as the existing notification listeners. Commission is computed on `orders.delivery_fee_original` (Phase 14 snapshot) — shipper isn't penalized by shipping vouchers. Balance is `SUM(amount) WHERE shipper_id`. No new module — entities + service + endpoints live in the existing `delivery` module.

**Tech Stack:** Spring Boot 3 + JPA + Postgres 16 + Flyway 10; React 18 + Vite 5 + TanStack Query + Tailwind + Recharts; pnpm workspace.

**Spec:** [docs/superpowers/specs/2026-06-03-voucher-shipper-commission-design.md](../specs/2026-06-03-voucher-shipper-commission-design.md)

---

## Wave 1 — V15 migration

### Task 1: V15 Flyway migration

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V15__shipper_ledger.sql`
- Create: `backend/modules/promotion/src/test/resources/db/migration/V15__shipper_ledger.sql` (duplicate — see Phase 14 pattern for promotion test classpath)

- [ ] **Step 1: Write migration**

```sql
-- V15__shipper_ledger.sql — Shipper commission + earnings ledger.
--
-- Commission rate stored in shop_config (singleton). Per-order commission
-- snapshot in orders.shipper_commission. Append-only ledger table records
-- COMMISSION (+) and COD_OWED (-) auto entries on DELIVERED, plus manual
-- SETTLEMENT_PAYOUT (-)/SETTLEMENT_DEPOSIT (+) entries from admin.
-- Balance = SUM(amount) WHERE shipper_id = ? (positive = shop owes shipper).

ALTER TABLE shop_config
    ADD COLUMN shipper_commission_pct NUMERIC(5, 2) NOT NULL DEFAULT 80.00
        CHECK (shipper_commission_pct >= 0 AND shipper_commission_pct <= 100);

ALTER TABLE orders
    ADD COLUMN shipper_commission NUMERIC(12, 2)
        CHECK (shipper_commission IS NULL OR shipper_commission >= 0);

CREATE TABLE shipper_ledger (
    id          BIGSERIAL PRIMARY KEY,
    shipper_id  BIGINT NOT NULL REFERENCES shipper_profile(id) ON DELETE RESTRICT,
    entry_type  VARCHAR(32) NOT NULL CHECK (entry_type IN (
        'COMMISSION', 'COD_OWED', 'SETTLEMENT_PAYOUT', 'SETTLEMENT_DEPOSIT'
    )),
    amount      NUMERIC(12, 2) NOT NULL,
    order_id    UUID REFERENCES orders(id) ON DELETE RESTRICT,
    note        TEXT,
    created_by  VARCHAR(64) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_shipper_date ON shipper_ledger(shipper_id, created_at DESC);
CREATE INDEX idx_ledger_order        ON shipper_ledger(order_id) WHERE order_id IS NOT NULL;
```

- [ ] **Step 2: Place copies for promotion test classpath**

```bash
cp backend/app/src/main/resources/db/migration/V15__shipper_ledger.sql \
   backend/modules/promotion/src/test/resources/db/migration/V15__shipper_ledger.sql
```

- [ ] **Step 3: Restart backend so Flyway applies V15**

```bash
lsof -iTCP:8080 -sTCP:LISTEN -P | awk 'NR>1 {print $2}' | xargs kill
cd backend && ./mvnw -pl app -am package -DskipTests 2>&1 | tail -3
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
SPRING_PROFILES_ACTIVE=dev nohup java -jar backend/app/target/delivery-app.jar > /tmp/backend.log 2>&1 &
sleep 8 && tail -8 /tmp/backend.log
```

Expected: `Started Application` and `Migrating schema "public" to version "15 - shipper ledger"`.

- [ ] **Step 4: Verify schema**

```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "
SELECT version, success FROM flyway_schema_history WHERE version='15';
\d shipper_ledger
SELECT shipper_commission_pct FROM shop_config;"
```

Expected: V15 success=t, ledger table with 4 entry_type CHECK values, shop_config has commission_pct=80.00.

- [ ] **Step 5: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V15__shipper_ledger.sql \
       backend/modules/promotion/src/test/resources/db/migration/V15__shipper_ledger.sql
git commit -m "feat(delivery): V15 shipper_ledger + shop_config commission_pct + orders.shipper_commission"
```

---

## Wave 2 — Domain + entity + repository

### Task 2: LedgerEntryType enum + ShipperLedgerEntry entity

**Files:**
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/domain/LedgerEntryType.java`
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/entity/ShipperLedgerEntry.java`

- [ ] **Step 1: LedgerEntryType enum**

```java
package com.shop.delivery.delivery.domain;

/**
 * Sign convention for shipper_ledger.amount (relative to shipper):
 *   COMMISSION         (+) shop owes shipper — auto on DELIVERED
 *   COD_OWED           (−) shipper owes shop — auto on DELIVERED if COD
 *   SETTLEMENT_PAYOUT  (−) shop paid shipper — manual by admin (clears + balance)
 *   SETTLEMENT_DEPOSIT (+) shipper paid shop — manual by admin (clears − balance)
 *
 * Balance = SUM(amount) WHERE shipper_id = ?
 *   Positive = shop owes shipper → admin should PAYOUT
 *   Negative = shipper owes shop → admin should DEPOSIT (after shipper hands cash over)
 */
public enum LedgerEntryType {
    COMMISSION,
    COD_OWED,
    SETTLEMENT_PAYOUT,
    SETTLEMENT_DEPOSIT
}
```

- [ ] **Step 2: ShipperLedgerEntry entity**

```java
package com.shop.delivery.delivery.entity;

import com.shop.delivery.delivery.domain.LedgerEntryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipper_ledger")
public class ShipperLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipper_id", nullable = false)
    private Long shipperId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32)
    private LedgerEntryType entryType;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "order_id", columnDefinition = "uuid")
    private UUID orderId;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public Long getShipperId() { return shipperId; }
    public void setShipperId(Long shipperId) { this.shipperId = shipperId; }
    public LedgerEntryType getEntryType() { return entryType; }
    public void setEntryType(LedgerEntryType entryType) { this.entryType = entryType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 3: Compile + commit**

```bash
cd backend && ./mvnw -pl modules/delivery -am compile 2>&1 | tail -3
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/domain/LedgerEntryType.java \
       backend/modules/delivery/src/main/java/com/shop/delivery/delivery/entity/ShipperLedgerEntry.java
git commit -m "feat(delivery): LedgerEntryType enum + ShipperLedgerEntry JPA entity"
```

### Task 3: ShipperLedgerRepository

**File:** `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/repository/ShipperLedgerRepository.java`

```java
package com.shop.delivery.delivery.repository;

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

    /** Balance = SUM(amount). Coalesce to 0 when shipper has no entries yet. */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM ShipperLedgerEntry e WHERE e.shipperId = :shipperId")
    BigDecimal sumAmountByShipperId(@Param("shipperId") Long shipperId);

    /** Idempotency check for the listener — prevents duplicate COMMISSION rows on event replay. */
    boolean existsByOrderIdAndEntryType(UUID orderId, com.shop.delivery.delivery.domain.LedgerEntryType entryType);
}
```

- [ ] Compile + commit:

```bash
cd backend && ./mvnw -pl modules/delivery -am compile 2>&1 | tail -3
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/repository/ShipperLedgerRepository.java
git commit -m "feat(delivery): ShipperLedgerRepository with balance sum + idempotency check"
```

---

## Wave 3 — CommissionCalculator + ShipperLedgerService

### Task 4: CommissionCalculator (pure fn, TDD with 5 tests)

**Files:**
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/CommissionCalculator.java`
- Test: `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/service/CommissionCalculatorTest.java`

- [ ] **Step 1: Write tests first**

```java
package com.shop.delivery.delivery.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class CommissionCalculatorTest {

    private final CommissionCalculator calc = new CommissionCalculator();

    @Test
    void typical_80pct_30k_fee_yields_24k() {
        assertThat(calc.commission(bd("30000"), bd("80")))
            .isEqualByComparingTo("24000");
    }

    @Test
    void zero_fee_yields_zero_commission() {
        assertThat(calc.commission(BigDecimal.ZERO, bd("80")))
            .isEqualByComparingTo("0");
    }

    @Test
    void zero_pct_yields_zero_commission() {
        assertThat(calc.commission(bd("30000"), BigDecimal.ZERO))
            .isEqualByComparingTo("0");
    }

    @Test
    void hundred_pct_yields_full_fee() {
        assertThat(calc.commission(bd("30000"), bd("100")))
            .isEqualByComparingTo("30000");
    }

    @Test
    void rounding_HALF_UP_to_zero_decimals() {
        // 33.33% of 15000 = 4999.5 → 5000
        assertThat(calc.commission(bd("15000"), bd("33.33")))
            .isEqualByComparingTo("5000");
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
}
```

- [ ] **Step 2: Run, expect compilation failure**

```bash
cd backend && ./mvnw -pl modules/delivery test -Dtest=CommissionCalculatorTest 2>&1 | tail -5
```

- [ ] **Step 3: Implement**

```java
package com.shop.delivery.delivery.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure function: commission = deliveryFeeOriginal × pct / 100, rounded HALF_UP to VND.
 *
 * `deliveryFeeOriginal` is the snapshot from `orders.delivery_fee_original` (Phase 14)
 * — NOT `orders.delivery_fee`. This is intentional: SHIPPING vouchers reduce what
 * the customer pays but shop absorbs the cost; shipper still earns commission on
 * the original fee.
 */
@Component
public class CommissionCalculator {

    public BigDecimal commission(BigDecimal deliveryFeeOriginal, BigDecimal commissionPct) {
        if (deliveryFeeOriginal == null || deliveryFeeOriginal.signum() <= 0) return BigDecimal.ZERO;
        if (commissionPct == null || commissionPct.signum() <= 0) return BigDecimal.ZERO;
        return deliveryFeeOriginal.multiply(commissionPct)
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 4: Run, expect 5 pass**

- [ ] **Step 5: Commit**

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/CommissionCalculator.java \
       backend/modules/delivery/src/test/java/com/shop/delivery/delivery/service/CommissionCalculatorTest.java
git commit -m "feat(delivery): CommissionCalculator with HALF_UP rounding on original fee"
```

### Task 5: ShipperLedgerService

**File:** `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/ShipperLedgerService.java`

```java
package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.domain.LedgerEntryType;
import com.shop.delivery.delivery.entity.ShipperLedgerEntry;
import com.shop.delivery.delivery.repository.ShipperLedgerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ShipperLedgerService {

    private final ShipperLedgerRepository repo;

    public ShipperLedgerService(ShipperLedgerRepository repo) { this.repo = repo; }

    @Transactional
    public ShipperLedgerEntry append(Long shipperId, LedgerEntryType type,
                                     BigDecimal amount, UUID orderId,
                                     String note, String createdBy) {
        ShipperLedgerEntry e = new ShipperLedgerEntry();
        e.setShipperId(shipperId);
        e.setEntryType(type);
        e.setAmount(amount);
        e.setOrderId(orderId);
        e.setNote(note);
        e.setCreatedBy(createdBy);
        return repo.save(e);
    }

    @Transactional(readOnly = true)
    public BigDecimal balance(Long shipperId) {
        BigDecimal b = repo.sumAmountByShipperId(shipperId);
        return b == null ? BigDecimal.ZERO : b;
    }

    @Transactional(readOnly = true)
    public Page<ShipperLedgerEntry> page(Long shipperId, Pageable pageable) {
        return repo.findByShipperIdOrderByCreatedAtDesc(shipperId, pageable);
    }

    @Transactional(readOnly = true)
    public List<ShipperLedgerEntry> range(Long shipperId, OffsetDateTime from, OffsetDateTime to) {
        return repo.findByShipperIdAndCreatedAtBetweenOrderByCreatedAtDesc(shipperId, from, to);
    }

    @Transactional(readOnly = true)
    public boolean alreadyHasEntry(UUID orderId, LedgerEntryType type) {
        return repo.existsByOrderIdAndEntryType(orderId, type);
    }
}
```

- [ ] Compile + commit:

```bash
cd backend && ./mvnw -pl modules/delivery -am compile 2>&1 | tail -3
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/ShipperLedgerService.java
git commit -m "feat(delivery): ShipperLedgerService — append + balance + paginated query"
```

---

## Wave 4 — Listener + ShopConfig integration

### Task 6: ShopConfig — expose commission_pct read

The promotion module's `VoucherApplicatorImpl` already injects `ShopConfigService` (or similar). Phase 15's listener needs `shop_config.shipper_commission_pct`. Add a method to read it.

**Files to inspect first:**

```bash
cat backend/modules/order/src/main/java/com/shop/delivery/order/entity/ShopConfig.java | head -50
cat backend/modules/order/src/main/java/com/shop/delivery/order/service/ShopConfigService.java 2>/dev/null
```

**Files to modify:**
- `backend/modules/order/src/main/java/com/shop/delivery/order/entity/ShopConfig.java` — add `shipperCommissionPct` field + getter/setter
- `backend/modules/order/src/main/java/com/shop/delivery/order/service/ShopConfigService.java` — add `getCommissionPct()` returning the value (or `getConfig()` already returns the full row in which case just expose pct via the DTO)
- `backend/modules/order/src/main/java/com/shop/delivery/order/api/dto/ShopConfigResponse.java` — add `shipperCommissionPct` field
- `backend/modules/order/src/main/java/com/shop/delivery/order/api/dto/UpdateShopConfigRequest.java` — add `shipperCommissionPct` field with validation

- [ ] **Step 1: ShopConfig entity — add column mapping**

In `ShopConfig.java`, add field after `freeKm`:

```java
@Column(name = "shipper_commission_pct", nullable = false, precision = 5, scale = 2)
private BigDecimal shipperCommissionPct = new BigDecimal("80.00");

public BigDecimal getShipperCommissionPct() { return shipperCommissionPct; }
public void setShipperCommissionPct(BigDecimal v) { this.shipperCommissionPct = v; }
```

- [ ] **Step 2: ShopConfigResponse DTO**

Find the existing record/class. Add `BigDecimal shipperCommissionPct` to constructor params.

- [ ] **Step 3: UpdateShopConfigRequest DTO**

Add the field with `@NotNull @DecimalMin("0") @DecimalMax("100")` validation:

```java
@NotNull
@DecimalMin(value = "0", message = "Tỉ lệ hoa hồng phải >= 0")
@DecimalMax(value = "100", message = "Tỉ lệ hoa hồng phải <= 100")
BigDecimal shipperCommissionPct
```

- [ ] **Step 4: ShopConfigService.updateConfig — wire the new field**

Make sure the update path copies `req.shipperCommissionPct()` into the entity (mirroring how other fields are copied).

- [ ] **Step 5: Compile + smoke**

```bash
cd backend && ./mvnw -pl modules/order -am compile -DskipTests 2>&1 | tail -3
lsof -iTCP:8080 -sTCP:LISTEN -P | awk 'NR>1 {print $2}' | xargs kill
cd backend && ./mvnw -pl app -am package -DskipTests 2>&1 | tail -3
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
SPRING_PROFILES_ACTIVE=dev nohup java -jar backend/app/target/delivery-app.jar > /tmp/backend.log 2>&1 &
sleep 8
curl -s http://localhost:8080/api/public/shop-config | jq '.shipperCommissionPct'
```

Expected: `80.0` (or `"80.00"` depending on JSON serialization).

- [ ] **Step 6: Commit**

```bash
git add backend/modules/order/
git commit -m "feat(order): expose shipper_commission_pct in ShopConfig entity + DTOs"
```

### Task 7: OrderCompletionListener — write commission + COD entries on DELIVERED

**Files:**
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/listener/OrderCompletionListener.java`

The listener reacts to `OrderDeliveredEvent` (already published by delivery module's `AssignmentService` when shipper marks delivered) and:
1. Loads order row by `orderId` to read `delivery_fee_original`, `total`, `payment_method`.
2. Loads `shop_config` for current `shipper_commission_pct`.
3. Computes commission via `CommissionCalculator`.
4. Persists `commission` snapshot on `orders.shipper_commission`.
5. Inserts COMMISSION ledger entry (+amount).
6. If `payment_method == 'COD'`: inserts COD_OWED entry (-order.total).
7. Idempotent: skips if a COMMISSION entry already exists for this order_id (defensive against event replay).

**Inspect first:**

```bash
cat backend/modules/notification/src/main/java/com/shop/delivery/notification/OrderLifecycleNotifier.java | head -60
```

— this shows the @TransactionalEventListener pattern. Match it exactly.

**Implementation:**

```java
package com.shop.delivery.delivery.listener;

import com.shop.delivery.delivery.domain.LedgerEntryType;
import com.shop.delivery.delivery.service.CommissionCalculator;
import com.shop.delivery.delivery.service.ShipperLedgerService;
import com.shop.delivery.delivery.service.event.OrderDeliveredEvent;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.order.service.ShopConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

/**
 * On OrderDeliveredEvent (AFTER_COMMIT phase, so order.status='DELIVERED' is
 * already persisted), compute shipper commission and append ledger entries:
 *
 *   - COMMISSION (+commission)
 *   - If payment_method == 'COD': COD_OWED (-order.total)  // shipper holds cash
 *
 * Commission base is `orders.delivery_fee_original` (Phase 14 snapshot). Idempotent
 * via existsByOrderIdAndEntryType — defensive against event replay.
 */
@Component
public class OrderCompletionListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCompletionListener.class);

    private final OrderRepository orderRepo;
    private final ShopConfigService shopConfigService;
    private final CommissionCalculator calculator;
    private final ShipperLedgerService ledgerService;

    public OrderCompletionListener(OrderRepository orderRepo,
                                   ShopConfigService shopConfigService,
                                   CommissionCalculator calculator,
                                   ShipperLedgerService ledgerService) {
        this.orderRepo = orderRepo;
        this.shopConfigService = shopConfigService;
        this.calculator = calculator;
        this.ledgerService = ledgerService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDelivered(OrderDeliveredEvent event) {
        if (ledgerService.alreadyHasEntry(event.orderId(), LedgerEntryType.COMMISSION)) {
            log.debug("Skipping commission for order {} — already recorded", event.orderId());
            return;
        }

        Order order = orderRepo.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.warn("OrderDeliveredEvent for unknown order {}", event.orderId());
            return;
        }

        BigDecimal pct = shopConfigService.getConfig().shipperCommissionPct();   // adapt method name
        BigDecimal commission = calculator.commission(order.getDeliveryFeeOriginal(), pct);

        order.setShipperCommission(commission);
        orderRepo.save(order);

        ledgerService.append(
            event.shipperId(),
            LedgerEntryType.COMMISSION,
            commission,
            event.orderId(),
            "Hoa hồng đơn " + event.orderCode(),
            "system");

        if ("COD".equalsIgnoreCase(String.valueOf(order.getPaymentMethod()))) {
            ledgerService.append(
                event.shipperId(),
                LedgerEntryType.COD_OWED,
                order.getTotal().negate(),
                event.orderId(),
                "Shipper đã thu COD đơn " + event.orderCode(),
                "system");
        }
    }
}
```

**Order.shipperCommission setter** must exist — add it if missing:

```java
// In Order.java alongside other discount fields:
@Column(name = "shipper_commission", precision = 12, scale = 2)
private BigDecimal shipperCommission;
public BigDecimal getShipperCommission() { return shipperCommission; }
public void setShipperCommission(BigDecimal v) { this.shipperCommission = v; }
```

- [ ] Verify build + restart backend:

```bash
cd backend && ./mvnw -pl modules/delivery -am compile -DskipTests 2>&1 | tail -3
lsof -iTCP:8080 -sTCP:LISTEN -P | awk 'NR>1 {print $2}' | xargs kill
cd backend && ./mvnw -pl app -am package -DskipTests 2>&1 | tail -3
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
SPRING_PROFILES_ACTIVE=dev nohup java -jar backend/app/target/delivery-app.jar > /tmp/backend.log 2>&1 &
sleep 8 && tail -8 /tmp/backend.log
```

Expected: clean startup, no Hibernate validation errors.

- [ ] Commit:

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/listener/OrderCompletionListener.java \
       backend/modules/order/src/main/java/com/shop/delivery/order/entity/Order.java
git commit -m "feat(delivery): OrderCompletionListener — auto commission + COD_OWED on DELIVERED"
```

---

## Wave 5 — Shipper self-service endpoints

### Task 8: ShipperEarningsController (shipper self-view)

**File:** `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/ShipperEarningsController.java`

**Endpoints:**
- `GET /api/shipper/me/earnings/summary?date=YYYY-MM-DD` → `{today, week, month, balance}`
- `GET /api/shipper/me/earnings/daily?from=&to=` → `[{date, ordersCount, commission}]`
- `GET /api/shipper/me/ledger?from=&to=&page=&size=` → `Page<LedgerRow>`
- `GET /api/shipper/me/profile` → `{name, phone, ratingAvg, totalOrders, joinedAt}`

DTOs (in `delivery/api/dto/`):

```java
public record EarningsSummary(
    BigDecimal today, BigDecimal week, BigDecimal month, BigDecimal balance,
    int todayOrders, int weekOrders
) {}

public record DailyEarning(LocalDate date, int ordersCount, BigDecimal commission) {}

public record LedgerRow(
    Long id, LedgerEntryType entryType, BigDecimal amount,
    UUID orderId, String note, OffsetDateTime createdAt
) {}

public record ShipperSelfProfile(
    String name, String phone, BigDecimal ratingAvg,
    int totalOrders, OffsetDateTime joinedAt
) {}
```

Controller (mirror `CustomerVoucherController` style; resolve current shipper via `@CurrentUser`):

```java
@RestController
@RequestMapping("/api/shipper/me")
public class ShipperEarningsController {

    private final ShipperLedgerService ledger;
    private final ShipperProfileRepository profileRepo;          // adapt — read the existing repo
    private final OrderRepository orderRepo;                     // for ordersCount aggregation

    // constructor injection…

    @GetMapping("/earnings/summary")
    public EarningsSummary summary(@CurrentUser TelegramUser user) {
        Long shipperId = profileRepo.findShipperIdByTelegramUserId(user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NOT_SHIPPER"));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startToday = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        OffsetDateTime startWeek  = startToday.minusDays(6);
        OffsetDateTime startMonth = startToday.minusDays(29);

        BigDecimal today = sumCommission(shipperId, startToday, now);
        BigDecimal week  = sumCommission(shipperId, startWeek,  now);
        BigDecimal month = sumCommission(shipperId, startMonth, now);

        int todayOrders = countCommissions(shipperId, startToday, now);
        int weekOrders  = countCommissions(shipperId, startWeek,  now);

        BigDecimal balance = ledger.balance(shipperId);
        return new EarningsSummary(today, week, month, balance, todayOrders, weekOrders);
    }

    private BigDecimal sumCommission(Long shipperId, OffsetDateTime from, OffsetDateTime to) {
        return ledger.range(shipperId, from, to).stream()
            .filter(e -> e.getEntryType() == LedgerEntryType.COMMISSION)
            .map(ShipperLedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private int countCommissions(Long shipperId, OffsetDateTime from, OffsetDateTime to) {
        return (int) ledger.range(shipperId, from, to).stream()
            .filter(e -> e.getEntryType() == LedgerEntryType.COMMISSION)
            .count();
    }

    @GetMapping("/earnings/daily")
    public List<DailyEarning> daily(@CurrentUser TelegramUser user,
                                    @RequestParam OffsetDateTime from,
                                    @RequestParam OffsetDateTime to) {
        Long shipperId = profileRepo.findShipperIdByTelegramUserId(user.getId()).orElseThrow();
        var entries = ledger.range(shipperId, from, to).stream()
            .filter(e -> e.getEntryType() == LedgerEntryType.COMMISSION)
            .collect(Collectors.groupingBy(e -> e.getCreatedAt().toLocalDate()));
        return entries.entrySet().stream()
            .map(en -> new DailyEarning(en.getKey(), en.getValue().size(),
                en.getValue().stream().map(ShipperLedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)))
            .sorted((a,b) -> a.date().compareTo(b.date()))
            .toList();
    }

    @GetMapping("/ledger")
    public Page<LedgerRow> ledger(@CurrentUser TelegramUser user, Pageable pageable) {
        Long shipperId = profileRepo.findShipperIdByTelegramUserId(user.getId()).orElseThrow();
        return ledger.page(shipperId, pageable).map(e ->
            new LedgerRow(e.getId(), e.getEntryType(), e.getAmount(),
                          e.getOrderId(), e.getNote(), e.getCreatedAt()));
    }

    @GetMapping("/profile")
    public ShipperSelfProfile profile(@CurrentUser TelegramUser user) {
        // adapt: look up the shipper_profile + rating_avg + joined_at
        // …
    }
}
```

The `profileRepo.findShipperIdByTelegramUserId(Long)` derived query may not exist — you may need to add it to `ShipperProfileRepository` (in delivery module). Match the existing repo signature when adding.

**Note:** `@CurrentUser` and `TelegramUser` are the codebase's existing auth pattern — already used by `CustomerVoucherController` (Phase 14). Match exactly.

- [ ] Compile + smoke (after backend restart):

```bash
USER_ID=$(docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -t -c \
  "SELECT t.id FROM telegram_user t JOIN shipper_profile s ON s.telegram_user_id=t.id LIMIT 1" | tr -d ' ')
curl -s "http://localhost:8080/api/shipper/me/earnings/summary" \
  -H "X-Dev-User-Id: $USER_ID" | jq .
```

Expected: JSON with today/week/month/balance/todayOrders/weekOrders (most zeros initially since no delivered orders trigger commission yet).

- [ ] Commit:

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/ShipperEarningsController.java \
       backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/dto/ \
       backend/modules/delivery/src/main/java/com/shop/delivery/delivery/repository/ShipperProfileRepository.java
git commit -m "feat(delivery): shipper-self endpoints — earnings/summary, /daily, /ledger, /profile"
```

---

## Wave 6 — Admin endpoints

### Task 9: AdminShipperLedgerController

**File:** `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/AdminShipperLedgerController.java`

```java
@RestController
@RequestMapping("/api/admin/shippers/{id}")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminShipperLedgerController {

    private final ShipperLedgerService ledger;
    // …

    @GetMapping("/balance")
    public BalanceResponse balance(@PathVariable Long id) {
        BigDecimal b = ledger.balance(id);
        OffsetDateTime lastSettled = ledger.page(id, Pageable.unpaged()).stream()
            .filter(e -> e.getEntryType() == LedgerEntryType.SETTLEMENT_PAYOUT
                       || e.getEntryType() == LedgerEntryType.SETTLEMENT_DEPOSIT)
            .map(ShipperLedgerEntry::getCreatedAt)
            .findFirst().orElse(null);
        return new BalanceResponse(b, lastSettled);
    }

    @GetMapping("/ledger")
    public Page<LedgerRow> ledger(@PathVariable Long id, Pageable pageable) {
        return ledger.page(id, pageable).map(e ->
            new LedgerRow(e.getId(), e.getEntryType(), e.getAmount(),
                          e.getOrderId(), e.getNote(), e.getCreatedAt()));
    }

    @PostMapping("/settle")
    public LedgerRow settle(@PathVariable Long id, @Valid @RequestBody SettleRequest req,
                            @CurrentAdmin String adminEmail) {
        LedgerEntryType type = switch (req.type()) {
            case "PAYOUT" -> LedgerEntryType.SETTLEMENT_PAYOUT;
            case "DEPOSIT" -> LedgerEntryType.SETTLEMENT_DEPOSIT;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TYPE");
        };
        BigDecimal signed = type == LedgerEntryType.SETTLEMENT_PAYOUT
            ? req.amount().negate() : req.amount();
        var e = ledger.append(id, type, signed, null, req.note(), adminEmail);
        return new LedgerRow(e.getId(), e.getEntryType(), e.getAmount(),
                             e.getOrderId(), e.getNote(), e.getCreatedAt());
    }
}

public record BalanceResponse(BigDecimal balance, OffsetDateTime lastSettledAt) {}

public record SettleRequest(
    @NotBlank @Pattern(regexp = "PAYOUT|DEPOSIT") String type,
    @NotNull @Positive BigDecimal amount,
    @Size(max = 256) String note
) {}
```

If `@CurrentAdmin` doesn't exist, look at how `AdminVoucherController` resolves the current admin email — copy the pattern. Often it's via `Authentication authn` injected by Spring Security:

```java
@PostMapping("/settle")
public LedgerRow settle(@PathVariable Long id, @Valid @RequestBody SettleRequest req,
                        Authentication authn) {
    String adminEmail = authn.getName();  // JWT subject
    // …
}
```

- [ ] Compile + smoke:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/admin/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"shop@example.com","password":"Demo@Shop2026!"}' | jq -r .accessToken)
SHIPPER_ID=$(docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -t -c "SELECT id FROM shipper_profile LIMIT 1" | tr -d ' ')
curl -s "http://localhost:8080/api/admin/shippers/$SHIPPER_ID/balance" -H "Authorization: Bearer $TOKEN" | jq .
```

Expected: `{"balance":"0.00","lastSettledAt":null}`.

- [ ] Commit:

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/AdminShipperLedgerController.java \
       backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/dto/
git commit -m "feat(delivery): admin shipper ledger endpoints + manual settle"
```

### Task 10: AdminShipperReportsController — aggregated earnings

**File:** `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/AdminShipperReportsController.java`

```java
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminShipperReportsController {

    private final ShipperLedgerRepository ledgerRepo;
    private final ShipperProfileRepository profileRepo;

    @GetMapping("/shipper-earnings")
    public List<EarningsBucket> earnings(
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(defaultValue = "shipper") String groupBy) {

        if (!"shipper".equals(groupBy) && !"day".equals(groupBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_GROUP_BY");
        }
        List<Object[]> rows = "shipper".equals(groupBy)
            ? ledgerRepo.aggregateByShipper(from, to)
            : ledgerRepo.aggregateByDay(from, to);
        return rows.stream()
            .map(r -> new EarningsBucket(
                String.valueOf(r[0]),
                ((Number) r[1]).intValue(),
                (BigDecimal) r[2]))
            .toList();
    }
}

public record EarningsBucket(String groupKey, int ordersCount, BigDecimal commission) {}
```

Add the two native queries on `ShipperLedgerRepository`:

```java
@Query(value = """
    SELECT shipper_id::text AS group_key,
           COUNT(*) FILTER (WHERE entry_type='COMMISSION') AS orders_count,
           COALESCE(SUM(amount) FILTER (WHERE entry_type='COMMISSION'), 0) AS commission
    FROM shipper_ledger
    WHERE created_at BETWEEN :from AND :to
    GROUP BY shipper_id ORDER BY shipper_id
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
```

If `groupBy=shipper`, the response's `groupKey` is the shipper_id as string (frontend joins to display name). If `groupBy=day`, the `groupKey` is the ISO date.

- [ ] Smoke:

```bash
TODAY=$(date +%Y-%m-%d)
WEEKAGO=$(date -v-7d +%Y-%m-%d)
curl -s "http://localhost:8080/api/admin/reports/shipper-earnings?from=${WEEKAGO}T00:00:00Z&to=${TODAY}T23:59:59Z&groupBy=shipper" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

Expected: empty array initially (no deliveries → no commission entries).

- [ ] Commit:

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/AdminShipperReportsController.java \
       backend/modules/delivery/src/main/java/com/shop/delivery/delivery/repository/ShipperLedgerRepository.java
git commit -m "feat(delivery): admin reports — shipper earnings aggregated by shipper or day"
```

---

## Wave 7 — Integration tests

### Task 11: OrderCompletionListenerIT — AFTER_COMMIT + idempotent + COD vs VNPay

**File:** `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/listener/OrderCompletionListenerIT.java`

Tests (each is a separate `@Test` method):

1. `cod_order_delivered_creates_commission_and_codOwed_entries` — assert 2 entries (COMMISSION + COD_OWED) with correct signed amounts.
2. `vnpay_order_delivered_creates_only_commission_entry` — assert 1 entry (COMMISSION).
3. `commission_computed_on_delivery_fee_original_not_post_voucher` — seed order with `delivery_fee=10000, delivery_fee_original=30000, discount_shipping=20000`; assert commission = 30000 × 0.8 = 24000 (NOT 8000).
4. `duplicate_event_does_not_double_commission` — fire event twice; assert exactly 1 COMMISSION entry exists for the order.
5. `commission_snapshot_set_on_orders_table` — assert `orders.shipper_commission` populated.

Use Testcontainers + JdbcTemplate for setup. Pattern matches `VoucherStackingIT` from Phase 14.

```java
@SpringBootTest
@Testcontainers
class OrderCompletionListenerIT {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ord_compl_test").withUsername("app").withPassword("app_test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired ApplicationEventPublisher events;
    @Autowired ShipperLedgerRepository ledgerRepo;
    @Autowired JdbcTemplate jdbc;

    @Test
    void cod_order_delivered_creates_commission_and_codOwed_entries() {
        // Arrange — seed shipper_profile + telegram_user + orders row
        Long shipperId = seedShipper();
        UUID orderId = seedOrder(shipperId, "COD", "30000", "30000", "0", "330000");

        // Act — publish event (must run from inside @Transactional that commits)
        publishDeliveredEventInTx(new OrderDeliveredEvent(
            UUID.randomUUID(), orderId, "TEST-COD-1", shipperId, 99001L));

        // Assert
        var entries = ledgerRepo.findByShipperIdOrderByCreatedAtDesc(shipperId, Pageable.unpaged());
        assertThat(entries).hasSize(2);
        assertThat(entries.stream().map(ShipperLedgerEntry::getEntryType))
            .containsExactlyInAnyOrder(LedgerEntryType.COD_OWED, LedgerEntryType.COMMISSION);
        // Commission = 30000 × 80% = 24000
        // COD_OWED = -330000
    }

    // … other tests, each independently seeding fresh data
}
```

The trickiest detail: `@TransactionalEventListener(AFTER_COMMIT)` only fires if the event is published from inside an active transaction that commits. In a test, wrap the publish in a tiny `@Transactional` helper:

```java
@Service
static class TestEventPublisher {
    @Autowired ApplicationEventPublisher events;
    @Transactional
    public void publish(OrderDeliveredEvent e) { events.publishEvent(e); }
}
```

Or use a TransactionTemplate. Match whatever pattern the codebase's existing integration tests use (look at how `OrderFlowIT` does it).

- [ ] Run:

```bash
cd backend && ./mvnw -pl modules/delivery verify -Dtest='OrderCompletionListenerIT' 2>&1 | tail -15
```

Expected: 5 tests pass.

- [ ] Commit:

```bash
git add backend/modules/delivery/src/test/java/com/shop/delivery/delivery/listener/OrderCompletionListenerIT.java
git commit -m "test(delivery): OrderCompletionListener — commission + COD_OWED + idempotency + original-fee base"
```

---

## Wave 8 — Frontend shared types + API client

### Task 12: Shared TypeScript types

**File:** `frontend/shared/src/types/shipper-earnings.ts` (NEW)

```typescript
export type LedgerEntryType = 'COMMISSION' | 'COD_OWED' | 'SETTLEMENT_PAYOUT' | 'SETTLEMENT_DEPOSIT';

export interface EarningsSummary {
  today: number;
  week: number;
  month: number;
  balance: number;
  todayOrders: number;
  weekOrders: number;
}

export interface DailyEarning {
  date: string;        // ISO YYYY-MM-DD
  ordersCount: number;
  commission: number;
}

export interface LedgerRow {
  id: number;
  entryType: LedgerEntryType;
  amount: number;
  orderId: string | null;
  note: string | null;
  createdAt: string;   // ISO
}

export interface ShipperSelfProfile {
  name: string;
  phone: string;
  ratingAvg: number;
  totalOrders: number;
  joinedAt: string;
}

export interface BalanceResponse {
  balance: number;
  lastSettledAt: string | null;
}

export interface SettleRequest {
  type: 'PAYOUT' | 'DEPOSIT';
  amount: number;
  note?: string;
}

export interface EarningsBucket {
  groupKey: string;
  ordersCount: number;
  commission: number;
}
```

Append `export * from './shipper-earnings';` to `frontend/shared/src/types/index.ts`.

- [ ] Type-check + commit:

```bash
cd frontend && pnpm --filter @shop/shared type-check
git add frontend/shared/src/types/shipper-earnings.ts frontend/shared/src/types/index.ts
git commit -m "feat(shared): shipper earnings TypeScript types"
```

### Task 13: Shared API client

**File:** `frontend/shared/src/api/shipper-earnings.ts`

```typescript
import type { AxiosInstance } from 'axios';
import type {
  EarningsSummary, DailyEarning, LedgerRow, ShipperSelfProfile,
  BalanceResponse, SettleRequest, EarningsBucket,
} from '../types';

// Shipper self (Telegram-auth required)
export async function fetchEarningsSummary(client: AxiosInstance): Promise<EarningsSummary> {
  const { data } = await client.get<EarningsSummary>('/api/shipper/me/earnings/summary');
  return data;
}
export async function fetchDailyEarnings(client: AxiosInstance, from: string, to: string): Promise<DailyEarning[]> {
  const { data } = await client.get<DailyEarning[]>('/api/shipper/me/earnings/daily', { params: { from, to } });
  return data;
}
export async function fetchShipperLedger(
  client: AxiosInstance, page = 0, size = 20
): Promise<{ content: LedgerRow[]; totalElements: number }> {
  const { data } = await client.get('/api/shipper/me/ledger', { params: { page, size } });
  return data;
}
export async function fetchShipperSelfProfile(client: AxiosInstance): Promise<ShipperSelfProfile> {
  const { data } = await client.get<ShipperSelfProfile>('/api/shipper/me/profile');
  return data;
}

// Admin (JWT-auth)
export async function fetchAdminShipperBalance(client: AxiosInstance, id: number): Promise<BalanceResponse> {
  const { data } = await client.get<BalanceResponse>(`/api/admin/shippers/${id}/balance`);
  return data;
}
export async function fetchAdminShipperLedger(
  client: AxiosInstance, id: number, page = 0, size = 20
): Promise<{ content: LedgerRow[]; totalElements: number }> {
  const { data } = await client.get(`/api/admin/shippers/${id}/ledger`, { params: { page, size } });
  return data;
}
export async function settleShipper(client: AxiosInstance, id: number, req: SettleRequest): Promise<LedgerRow> {
  const { data } = await client.post<LedgerRow>(`/api/admin/shippers/${id}/settle`, req);
  return data;
}
export async function fetchAdminShipperEarnings(
  client: AxiosInstance, from: string, to: string, groupBy: 'shipper' | 'day' = 'shipper'
): Promise<EarningsBucket[]> {
  const { data } = await client.get<EarningsBucket[]>('/api/admin/reports/shipper-earnings',
    { params: { from, to, groupBy } });
  return data;
}
```

Append `export * from './shipper-earnings';` to `frontend/shared/src/api/index.ts`.

- [ ] Type-check + commit:

```bash
cd frontend && pnpm --filter @shop/shared type-check && pnpm --filter @shop/miniapp type-check && pnpm --filter @shop/webadmin type-check
git add frontend/shared/src/api/shipper-earnings.ts frontend/shared/src/api/index.ts
git commit -m "feat(shared): shipper earnings API client (self + admin)"
```

---

## Wave 9 — Mini App shipper redesign

### Task 14: ShipperLayout + bottom tab nav

**File:** `frontend/miniapp/src/components/ShipperLayout.tsx`

```tsx
import { Outlet, NavLink } from 'react-router-dom';

export function ShipperLayout() {
  return (
    <div className="min-h-screen pb-16 bg-gray-50">
      <Outlet />
      <nav className="fixed bottom-0 inset-x-0 bg-white border-t border-gray-200 flex justify-around py-2"
           style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}>
        <Tab to="/shipper/earnings"    icon="💰" label="Earnings" />
        <Tab to="/shipper/assignments" icon="📋" label="Đơn" />
        <Tab to="/shipper/wallet"      icon="👛" label="Ví" />
        <Tab to="/shipper/profile"     icon="👤" label="Profile" />
      </nav>
    </div>
  );
}

function Tab({ to, icon, label }: { to: string; icon: string; label: string }) {
  return (
    <NavLink to={to}
      className={({ isActive }) =>
        `flex flex-col items-center text-xs ${isActive ? 'text-[var(--brand-primary)]' : 'text-gray-500'}`}>
      <span className="text-xl">{icon}</span>
      <span>{label}</span>
    </NavLink>
  );
}
```

- [ ] Commit:

```bash
git add frontend/miniapp/src/components/ShipperLayout.tsx
git commit -m "feat(miniapp): ShipperLayout with bottom tab nav (Earnings/Đơn/Ví/Profile)"
```

### Task 15: EarningsPage (shipper home)

**File:** `frontend/miniapp/src/pages/shipper/EarningsPage.tsx`

```tsx
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { BarChart, Bar, XAxis, YAxis, ResponsiveContainer, Tooltip } from 'recharts';
import { fetchEarningsSummary, fetchDailyEarnings, formatVnd } from '@shop/shared';
import { api } from '@/lib/api';

export function EarningsPage() {
  const { data: summary } = useQuery({
    queryKey: ['shipper', 'earnings', 'summary'],
    queryFn: () => fetchEarningsSummary(api),
  });

  const to = new Date();
  const from = new Date(to.getTime() - 6 * 86400_000);
  const { data: daily = [] } = useQuery({
    queryKey: ['shipper', 'earnings', 'daily', from.toISOString().slice(0,10)],
    queryFn: () => fetchDailyEarnings(api, from.toISOString(), to.toISOString()),
  });

  if (!summary) return <p className="p-4">Đang tải…</p>;

  return (
    <div className="p-4 space-y-4">
      <header>
        <p className="text-sm text-gray-500">Chào shipper 👋</p>
      </header>

      <section className="bg-gradient-to-br from-[var(--brand-primary)] to-[var(--brand-primary-dark)] text-white rounded-2xl p-5">
        <p className="text-xs uppercase tracking-wide opacity-80">Hôm nay bạn kiếm được</p>
        <p className="text-4xl font-extrabold mt-1">{formatVnd(summary.today)}</p>
        <p className="text-sm opacity-80 mt-1">{summary.todayOrders} đơn</p>
      </section>

      <section className="grid grid-cols-2 gap-3">
        <Kpi label="Tuần này" value={formatVnd(summary.week)} sub={`${summary.weekOrders} đơn`} />
        <Kpi label="Tháng này" value={formatVnd(summary.month)} />
        <Kpi label="Ví hôm nay" value={formatVnd(summary.balance)} />
      </section>

      <section className="bg-white rounded-2xl p-4">
        <p className="font-semibold mb-2">Thu nhập 7 ngày qua</p>
        <ResponsiveContainer width="100%" height={160}>
          <BarChart data={daily}>
            <XAxis dataKey="date" fontSize={10} tickFormatter={d => d.slice(5)} />
            <YAxis fontSize={10} tickFormatter={n => `${Math.round(n/1000)}k`} />
            <Tooltip formatter={(v: number) => formatVnd(v)} />
            <Bar dataKey="commission" fill="var(--brand-primary)" radius={[4,4,0,0]} />
          </BarChart>
        </ResponsiveContainer>
      </section>

      <Link to="/shipper/earnings/history" className="block text-center text-sm text-[var(--brand-primary)]">
        Xem chi tiết theo ngày →
      </Link>
    </div>
  );
}

function Kpi({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="bg-white rounded-xl p-3">
      <p className="text-xs text-gray-500">{label}</p>
      <p className="text-lg font-bold mt-1">{value}</p>
      {sub && <p className="text-xs text-gray-400 mt-0.5">{sub}</p>}
    </div>
  );
}
```

- [ ] Type-check + commit (combine with subsequent pages):

```bash
cd frontend && pnpm --filter @shop/miniapp type-check
git add frontend/miniapp/src/pages/shipper/EarningsPage.tsx
git commit -m "feat(miniapp): ShipperEarningsPage with hero + KPIs + 7-day bar chart"
```

### Task 16: WalletPage

**File:** `frontend/miniapp/src/pages/shipper/WalletPage.tsx`

```tsx
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchShipperLedger, formatVnd, type LedgerEntryType } from '@shop/shared';
import { api } from '@/lib/api';

type Filter = 'all' | LedgerEntryType;

const LABEL: Record<LedgerEntryType, string> = {
  COMMISSION: 'Hoa hồng',
  COD_OWED: 'COD đã thu',
  SETTLEMENT_PAYOUT: 'Shop trả lương',
  SETTLEMENT_DEPOSIT: 'Đã nộp tiền',
};

export function WalletPage() {
  const [filter, setFilter] = useState<Filter>('all');
  const { data } = useQuery({
    queryKey: ['shipper','ledger', 0],
    queryFn: () => fetchShipperLedger(api, 0, 50),
  });
  const entries = (data?.content ?? []).filter(e => filter === 'all' || e.entryType === filter);

  return (
    <div className="p-4 space-y-4">
      <header>
        <h1 className="text-xl font-bold">👛 Ví của bạn</h1>
      </header>

      <section className="bg-white rounded-2xl p-5 text-center">
        <p className="text-xs text-gray-500">Số dư hiện tại</p>
        <p className="text-3xl font-bold mt-1 text-[var(--brand-primary)]">
          {/* Balance from summary endpoint or sum locally; reuse summary in production */}
          {formatVnd((data?.content ?? []).reduce((s, e) => s + Number(e.amount), 0))}
        </p>
      </section>

      <div className="flex gap-2 overflow-x-auto pb-1">
        {(['all','COMMISSION','COD_OWED','SETTLEMENT_PAYOUT','SETTLEMENT_DEPOSIT'] as Filter[]).map(f => (
          <button key={f} onClick={() => setFilter(f)}
            className={`text-xs px-3 py-1 rounded-full whitespace-nowrap ${filter === f
              ? 'bg-[var(--brand-primary)] text-white' : 'bg-white border border-gray-300'}`}>
            {f === 'all' ? 'Tất cả' : LABEL[f as LedgerEntryType]}
          </button>
        ))}
      </div>

      <ul className="space-y-2">
        {entries.map(e => (
          <li key={e.id} className="bg-white rounded-xl p-3 flex justify-between">
            <div>
              <p className="text-sm">{e.note ?? LABEL[e.entryType]}</p>
              <p className="text-xs text-gray-400">{new Date(e.createdAt).toLocaleString('vi-VN')}</p>
            </div>
            <p className={`font-bold ${Number(e.amount) >= 0 ? 'text-green-700' : 'text-red-600'}`}>
              {Number(e.amount) >= 0 ? '+ ' : ''}{formatVnd(e.amount)}
            </p>
          </li>
        ))}
        {entries.length === 0 && <li className="text-center text-sm text-gray-500 py-8">Chưa có giao dịch nào</li>}
      </ul>
    </div>
  );
}
```

### Task 17: EarningsDetailPage

**File:** `frontend/miniapp/src/pages/shipper/EarningsDetailPage.tsx`

Date picker (default today) + per-order breakdown from a /daily-detail-style endpoint (or filter ledger by date locally). For thesis simplicity: filter `fetchShipperLedger` entries by `e.createdAt` matching the picked date, show only COMMISSION entries, with order code + amount.

```tsx
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchShipperLedger, formatVnd } from '@shop/shared';
import { api } from '@/lib/api';

export function EarningsDetailPage() {
  const [date, setDate] = useState(new Date().toISOString().slice(0,10));
  const { data } = useQuery({
    queryKey: ['shipper','ledger','all'],
    queryFn: () => fetchShipperLedger(api, 0, 200),
  });
  const dayEntries = (data?.content ?? []).filter(e =>
    e.entryType === 'COMMISSION' && e.createdAt.startsWith(date));

  const total = dayEntries.reduce((s, e) => s + Number(e.amount), 0);

  return (
    <div className="p-4 space-y-4">
      <input type="date" value={date} onChange={e => setDate(e.target.value)}
        className="w-full px-3 py-2 rounded border" />
      <section className="bg-white rounded-2xl p-4 text-center">
        <p className="text-xs text-gray-500">Tổng hoa hồng ngày này</p>
        <p className="text-2xl font-bold text-[var(--brand-primary)]">{formatVnd(total)}</p>
        <p className="text-xs text-gray-400">{dayEntries.length} đơn</p>
      </section>
      <ul className="space-y-2">
        {dayEntries.map(e => (
          <li key={e.id} className="bg-white rounded-xl p-3 flex justify-between">
            <p className="text-sm">{e.note}</p>
            <p className="font-bold text-green-700">+ {formatVnd(e.amount)}</p>
          </li>
        ))}
        {dayEntries.length === 0 && <li className="text-center text-sm text-gray-500 py-6">Không có đơn nào hoàn tất hôm này</li>}
      </ul>
    </div>
  );
}
```

### Task 18: ShipperProfilePage

**File:** `frontend/miniapp/src/pages/shipper/ShipperProfilePage.tsx`

```tsx
import { useQuery } from '@tanstack/react-query';
import { fetchShipperSelfProfile } from '@shop/shared';
import { api } from '@/lib/api';

export function ShipperProfilePage() {
  const { data: p } = useQuery({
    queryKey: ['shipper','profile'],
    queryFn: () => fetchShipperSelfProfile(api),
  });
  if (!p) return <p className="p-4">Đang tải…</p>;
  return (
    <div className="p-4 space-y-4">
      <section className="bg-white rounded-2xl p-5 text-center">
        <div className="w-20 h-20 rounded-full bg-gray-200 mx-auto mb-3 flex items-center justify-center text-3xl">🛵</div>
        <h2 className="text-xl font-bold">{p.name}</h2>
        <p className="text-sm text-gray-500 mt-1">{p.phone}</p>
        <p className="text-yellow-500 mt-2">★ {p.ratingAvg.toFixed(2)}</p>
      </section>
      <section className="grid grid-cols-2 gap-3">
        <Stat label="Tổng đơn đã giao" value={p.totalOrders.toString()} />
        <Stat label="Tham gia" value={new Date(p.joinedAt).toLocaleDateString('vi-VN')} />
      </section>
    </div>
  );
}
function Stat({ label, value }: { label: string; value: string }) {
  return <div className="bg-white rounded-xl p-3"><p className="text-xs text-gray-500">{label}</p><p className="font-bold mt-1">{value}</p></div>;
}
```

### Task 19: Wire pages into routes + redesign existing 2 pages

**File:** `frontend/miniapp/src/App.tsx`

Modify the `<Route>` block so the 4 shipper paths render INSIDE `<ShipperLayout>`:

```tsx
import { ShipperLayout } from './components/ShipperLayout';
import { EarningsPage } from './pages/shipper/EarningsPage';
import { EarningsDetailPage } from './pages/shipper/EarningsDetailPage';
import { WalletPage } from './pages/shipper/WalletPage';
import { ShipperProfilePage } from './pages/shipper/ShipperProfilePage';

// inside Routes:
<Route element={<ShipperLayout />}>
  <Route path="shipper/earnings"           element={<EarningsPage />} />
  <Route path="shipper/earnings/history"   element={<EarningsDetailPage />} />
  <Route path="shipper/wallet"             element={<WalletPage />} />
  <Route path="shipper/profile"            element={<ShipperProfilePage />} />
  {/* existing routes — move them inside this block to inherit the bottom tab nav */}
  <Route path="shipper/assignments"        element={<ShipperAssignmentsPage />} />
  <Route path="shipper/assignments/:id"    element={<ShipperAssignmentDetailPage />} />
</Route>
```

### Task 19b: Redesign existing ShipperAssignmentsPage card style

Open `frontend/miniapp/src/pages/ShipperAssignmentsPage.tsx`. Restyle each assignment row from whatever the current pattern is to:

```tsx
<article className="bg-white rounded-xl p-3 space-y-1.5 active:scale-[0.98] transition">
  <header className="flex justify-between items-center">
    <span className="text-xs uppercase font-semibold px-2 py-0.5 rounded-full bg-blue-100 text-blue-700">
      {STATUS_LABEL[a.status]}
    </span>
    <span className="text-sm font-bold text-[var(--brand-primary)]">
      +{formatVnd(estCommission(a))} dự kiến
    </span>
  </header>
  <p className="text-sm font-mono">{a.orderCode}</p>
  <p className="text-xs text-gray-500">{a.customerName} · {a.distanceKm.toFixed(1)} km</p>
</article>
```

Where `estCommission(a)` is `Math.round(Number(a.deliveryFeeOriginal ?? a.deliveryFee) * 0.8)` as an estimate (final value snapshots at DELIVERED).

### Commit Wave 9

After all of 14, 15, 16, 17, 18, 19, 19b are done, type-check + smoke test by loading `/shipper/earnings` in playwright (with mock Telegram user that's a real shipper):

```bash
cd frontend && pnpm --filter @shop/miniapp type-check
```

```bash
git add frontend/miniapp/src/
git commit -m "feat(miniapp): shipper redesign — 4 new pages + bottom tab nav + assignment card style"
```

(You may split into smaller commits per page if you prefer.)

---

## Wave 10 — Web Admin pages

### Task 20: SettingsPage — add commission % field

Open `frontend/webadmin/src/pages/SettingsPage.tsx`. Add a new section "Hoa hồng shipper" after the existing "Phí giao hàng" section with a single number input for `shipperCommissionPct` (0–100). Wire it to the form state and POST payload.

```tsx
<Section title="Hoa hồng shipper" subtitle="% phí ship gốc shipper được hưởng khi đơn DELIVERED">
  <Field label="Tỉ lệ hoa hồng (%)" required>
    <input type="number" min={0} max={100} step={0.1} required
      value={form.shipperCommissionPct}
      onChange={e => set('shipperCommissionPct', Number(e.target.value))}
      className="input" />
  </Field>
  <p className="text-xs text-gray-500">Đơn 30k phí ship × {form.shipperCommissionPct}% = <strong>{Math.round(30000 * form.shipperCommissionPct / 100).toLocaleString('vi-VN')}đ</strong></p>
</Section>
```

Also update the `ShopConfig` TS type in `frontend/shared/src/types/shop-config.ts` to include `shipperCommissionPct: number`.

- [ ] Smoke (after backend restart):

Open http://localhost:5181/settings → verify field appears + saves.

- [ ] Commit:

```bash
git add frontend/webadmin/src/pages/SettingsPage.tsx frontend/shared/src/types/shop-config.ts
git commit -m "feat(webadmin): Settings page — shipper commission % field"
```

### Task 21: ShippersPage update — add balance + 7-day earnings columns

Open `frontend/webadmin/src/pages/ShippersPage.tsx`. For each row, add 2 cells: balance (from `/api/admin/shippers/{id}/balance`) and 7-day earnings (compute via `/api/admin/reports/shipper-earnings?from=&to=&groupBy=shipper`).

To avoid N+1 fetches, do ONE aggregated fetch at page load:

```tsx
const { data: agg = [] } = useQuery({
  queryKey: ['admin','shipperEarnings','7d'],
  queryFn: () => fetchAdminShipperEarnings(api, sevenDaysAgo(), nowIso(), 'shipper'),
});
const earningsByShipperId = new Map(agg.map(b => [b.groupKey, b.commission]));
```

Then in the table render, look up `earningsByShipperId.get(String(shipper.id)) ?? 0`. For balance, similarly fetch all balances in parallel (or accept N+1 for the thesis-scale demo).

Add click handler on the row → `navigate('/shippers/' + shipper.id)`.

- [ ] Commit:

```bash
git add frontend/webadmin/src/pages/ShippersPage.tsx
git commit -m "feat(webadmin): ShippersPage — balance + 7-day earnings columns, row click → detail"
```

### Task 22: ShipperDetailPage (new)

**File:** `frontend/webadmin/src/pages/ShipperDetailPage.tsx`

Sections:
- Info card (name, phone, rating, total orders)
- Balance card with "Đối soát" button → opens modal asking type (PAYOUT/DEPOSIT) + amount + note → POST `/api/admin/shippers/{id}/settle`
- Ledger entries table (paginated) with filter chips by entry_type

```tsx
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchAdminShipperBalance, fetchAdminShipperLedger, settleShipper, formatVnd,
  type LedgerEntryType,
} from '@shop/shared';
import { api } from '@/lib/api';

export function ShipperDetailPage() {
  const { id } = useParams();
  const sid = Number(id);
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const [modal, setModal] = useState<null | { type: 'PAYOUT' | 'DEPOSIT' }>(null);

  const { data: bal } = useQuery({
    queryKey: ['admin','shipper','balance', sid],
    queryFn: () => fetchAdminShipperBalance(api, sid),
  });
  const { data: ledger } = useQuery({
    queryKey: ['admin','shipper','ledger', sid, page],
    queryFn: () => fetchAdminShipperLedger(api, sid, page, 20),
  });
  const settle = useMutation({
    mutationFn: ({ type, amount, note }: { type: 'PAYOUT'|'DEPOSIT'; amount: number; note: string }) =>
      settleShipper(api, sid, { type, amount, note }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin','shipper','balance', sid] });
      qc.invalidateQueries({ queryKey: ['admin','shipper','ledger', sid] });
      setModal(null);
    },
  });

  return (
    <div>
      <header className="mb-6">
        <h1 className="text-2xl font-bold">Chi tiết shipper #{sid}</h1>
      </header>

      <div className="grid grid-cols-3 gap-4 mb-6">
        <div className="bg-white rounded-xl p-4 col-span-2">
          <p className="text-xs text-gray-500">Số dư hiện tại</p>
          <p className="text-3xl font-bold mt-1">{bal && formatVnd(bal.balance)}</p>
          <div className="flex gap-2 mt-3">
            <button onClick={() => setModal({ type: 'PAYOUT' })}
              className="px-3 py-1.5 rounded bg-orange-500 text-white text-sm">Đã trả lương</button>
            <button onClick={() => setModal({ type: 'DEPOSIT' })}
              className="px-3 py-1.5 rounded bg-blue-500 text-white text-sm">Đã nhận tiền nộp</button>
          </div>
        </div>
      </div>

      <h2 className="font-semibold mb-3">Lịch sử ledger</h2>
      <table className="w-full bg-white rounded-xl border border-gray-200">
        <thead className="bg-gray-50 text-left text-xs uppercase">
          <tr><th className="px-4 py-3">Thời gian</th><th>Loại</th><th>Đơn</th><th>Ghi chú</th><th className="text-right">Số tiền</th></tr>
        </thead>
        <tbody>
          {(ledger?.content ?? []).map(e => (
            <tr key={e.id} className="border-t border-gray-100">
              <td className="px-4 py-2 text-sm">{new Date(e.createdAt).toLocaleString('vi-VN')}</td>
              <td className="text-xs">{e.entryType}</td>
              <td className="text-xs font-mono">{e.orderId?.slice(0,8) ?? '—'}</td>
              <td className="text-sm">{e.note}</td>
              <td className={`px-4 py-2 text-right font-bold ${Number(e.amount) >= 0 ? 'text-green-700' : 'text-red-600'}`}>
                {Number(e.amount) >= 0 ? '+' : ''}{formatVnd(e.amount)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {modal && (
        <SettleModal type={modal.type} onClose={() => setModal(null)}
          onSubmit={(amount, note) => settle.mutate({ type: modal.type, amount, note })} />
      )}
    </div>
  );
}

function SettleModal({ type, onClose, onSubmit }:{
  type: 'PAYOUT' | 'DEPOSIT';
  onClose: () => void;
  onSubmit: (amount: number, note: string) => void;
}) {
  const [amount, setAmount] = useState(0);
  const [note, setNote] = useState('');
  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={onClose}>
      <div className="bg-white rounded-2xl p-6 w-96 space-y-3" onClick={e => e.stopPropagation()}>
        <h3 className="font-bold text-lg">{type === 'PAYOUT' ? 'Đã trả lương shipper' : 'Đã nhận tiền nộp từ shipper'}</h3>
        <input type="number" min={0} value={amount} onChange={e => setAmount(Number(e.target.value))}
          placeholder="Số tiền (đ)" className="w-full px-3 py-2 rounded border" />
        <input type="text" value={note} onChange={e => setNote(e.target.value)}
          placeholder="Ghi chú (tuỳ chọn)" className="w-full px-3 py-2 rounded border" />
        <div className="flex gap-2 justify-end pt-2">
          <button onClick={onClose} className="px-4 py-2 rounded bg-gray-200">Huỷ</button>
          <button onClick={() => onSubmit(amount, note)} disabled={amount <= 0}
            className="px-4 py-2 rounded bg-orange-500 text-white disabled:opacity-50">Lưu</button>
        </div>
      </div>
    </div>
  );
}
```

Add route: `<Route path="/shippers/:id" element={<ShipperDetailPage />} />` in `App.tsx`.

### Task 23: ShipperEarningsPage (admin earnings report)

**File:** `frontend/webadmin/src/pages/ShipperEarningsPage.tsx`

```tsx
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { BarChart, Bar, XAxis, YAxis, ResponsiveContainer, Tooltip, LineChart, Line } from 'recharts';
import { fetchAdminShipperEarnings, formatVnd } from '@shop/shared';
import { api } from '@/lib/api';

export function ShipperEarningsPage() {
  const [from, setFrom] = useState(new Date(Date.now() - 7*86400_000).toISOString().slice(0,10));
  const [to,   setTo]   = useState(new Date().toISOString().slice(0,10));

  const { data: byShipper = [] } = useQuery({
    queryKey: ['admin','earnings','shipper', from, to],
    queryFn: () => fetchAdminShipperEarnings(api, `${from}T00:00:00Z`, `${to}T23:59:59Z`, 'shipper'),
  });
  const { data: byDay = [] } = useQuery({
    queryKey: ['admin','earnings','day', from, to],
    queryFn: () => fetchAdminShipperEarnings(api, `${from}T00:00:00Z`, `${to}T23:59:59Z`, 'day'),
  });

  const totalCommission = byShipper.reduce((s, b) => s + Number(b.commission), 0);
  const totalOrders = byShipper.reduce((s, b) => s + b.ordersCount, 0);

  return (
    <div>
      <header className="mb-6">
        <h1 className="text-2xl font-bold">Thu nhập shipper</h1>
        <div className="flex gap-3 mt-3">
          <input type="date" value={from} onChange={e => setFrom(e.target.value)} className="px-3 py-2 rounded border" />
          <input type="date" value={to}   onChange={e => setTo(e.target.value)}   className="px-3 py-2 rounded border" />
        </div>
      </header>

      <div className="grid grid-cols-3 gap-4 mb-6">
        <Kpi label="Tổng hoa hồng" value={formatVnd(totalCommission)} />
        <Kpi label="Số đơn" value={String(totalOrders)} />
        <Kpi label="Số shipper hoạt động" value={String(byShipper.length)} />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <Panel title="Top shipper">
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={byShipper.slice(0, 5)} layout="vertical">
              <XAxis type="number" />
              <YAxis dataKey="groupKey" type="category" />
              <Tooltip formatter={(v: number) => formatVnd(v)} />
              <Bar dataKey="commission" fill="#f97316" />
            </BarChart>
          </ResponsiveContainer>
        </Panel>
        <Panel title="Hoa hồng theo ngày">
          <ResponsiveContainer width="100%" height={240}>
            <LineChart data={byDay}>
              <XAxis dataKey="groupKey" />
              <YAxis />
              <Tooltip formatter={(v: number) => formatVnd(v)} />
              <Line type="monotone" dataKey="commission" stroke="#f97316" strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        </Panel>
      </div>
    </div>
  );
}
function Kpi({ label, value }: {label:string;value:string}) {
  return <div className="bg-white rounded-xl p-4"><p className="text-xs text-gray-500">{label}</p><p className="text-xl font-bold mt-1">{value}</p></div>;
}
function Panel({ title, children }: {title:string;children:React.ReactNode}) {
  return <div className="bg-white rounded-xl p-4"><p className="font-semibold mb-2">{title}</p>{children}</div>;
}
```

Add sidebar entry "💰 Thu nhập" → `/shipper-earnings` and route in `App.tsx`.

### Task 24: OrderDetailPage update — Commission section

Open `frontend/webadmin/src/pages/OrderDetailPage.tsx`. When `order.status === 'DELIVERED'`, render a section "Hoa hồng & thanh toán":

```tsx
{order.status === 'DELIVERED' && (
  <section className="bg-white rounded-xl p-4 mt-4">
    <h3 className="font-semibold mb-3">Hoa hồng & thanh toán</h3>
    <dl className="grid grid-cols-2 gap-y-2 text-sm">
      <dt>Phí ship gốc</dt><dd className="text-right">{formatVnd(order.deliveryFeeOriginal)}</dd>
      <dt>Giảm phí ship (voucher)</dt><dd className="text-right">−{formatVnd(order.discountShipping)}</dd>
      <dt>Phí ship khách trả</dt><dd className="text-right">{formatVnd(order.deliveryFee)}</dd>
      <dt>Hoa hồng shipper</dt><dd className="text-right font-bold text-green-700">{formatVnd(order.shipperCommission ?? 0)}</dd>
      <dt>Hình thức thanh toán</dt><dd className="text-right">{order.paymentMethod}</dd>
      {order.paymentMethod === 'COD' && (
        <>
          <dt>Shipper đã thu COD</dt><dd className="text-right">{formatVnd(order.total)}</dd>
        </>
      )}
    </dl>
  </section>
)}
```

Update the `Order`/`OrderResponse` TS type in `frontend/shared/src/types/order.ts` to include `shipperCommission: number | null`.

- [ ] Commit Wave 10 (combined):

```bash
cd frontend && pnpm --filter @shop/webadmin type-check
git add frontend/webadmin/src/ frontend/shared/src/types/order.ts
git commit -m "feat(webadmin): Settings commission % + ShipperEarningsPage + ShipperDetailPage + OrderDetail commission section"
```

---

## Wave 11 — E2E smoke + close-out

### Task 25: End-to-end demo flow

Use playwright to run the full demo path:

1. Admin updates commission to 80% (already default).
2. Admin opens Settings — verify field reads 80.
3. Admin opens `/shipper-earnings` — verify page renders.
4. Manually trigger an order DELIVERED via API (or use existing demo data — V11 seed has DELIVERED orders, but they predate V15 so they have no commission. Need to trigger a NEW order through the pipeline).

For the thesis demo, the cleanest smoke is:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/admin/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"shop@example.com","password":"Demo@Shop2026!"}' | jq -r .accessToken)
SHIPPER_ID=$(docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -t -c "SELECT id FROM shipper_profile LIMIT 1" | tr -d ' ')

# Direct ledger insert to simulate "previously delivered" orders for demo
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "
INSERT INTO shipper_ledger (shipper_id, entry_type, amount, note, created_by, created_at)
VALUES ($SHIPPER_ID, 'COMMISSION', 24000, 'Demo entry 1', 'system', NOW() - INTERVAL '1 day'),
       ($SHIPPER_ID, 'COMMISSION', 18000, 'Demo entry 2', 'system', NOW() - INTERVAL '2 day'),
       ($SHIPPER_ID, 'COMMISSION', 32000, 'Demo entry 3', 'system', NOW() - INTERVAL '3 day');"

# Verify balance + earnings show up
curl -s "http://localhost:8080/api/admin/shippers/$SHIPPER_ID/balance" -H "Authorization: Bearer $TOKEN" | jq .
curl -s "http://localhost:8080/api/admin/reports/shipper-earnings?from=$(date -v-7d +%Y-%m-%d)T00:00:00Z&to=$(date +%Y-%m-%d)T23:59:59Z&groupBy=shipper" \
    -H "Authorization: Bearer $TOKEN" | jq .
```

Expected: balance = 74000, earnings response shows 3 commissions for that shipper.

Then in webadmin: open `/shipper-earnings` → verify charts render. Open `/shippers` → click row → verify ShipperDetailPage shows ledger entries.

- [ ] (No commit for smoke step — verification only.)

### Task 26: Mark spec Phase 15 complete

Edit `docs/superpowers/specs/2026-06-03-voucher-shipper-commission-design.md`:

```
**Status:** Phase 14 complete. Phase 15 not started.
```

becomes:

```
**Status:** Phase 14 + 15 both complete.
```

Commit:
```bash
git add docs/superpowers/specs/2026-06-03-voucher-shipper-commission-design.md
git commit -m "docs(spec): mark Phase 15 (Shipper Commission) complete"
```

---

## Done

After all 26 tasks pass:
- Flyway at V15
- `shop_config.shipper_commission_pct` editable from Settings
- `OrderCompletionListener` auto-creates ledger entries on every DELIVERED
- Shipper Mini App has bottom tab nav with 4 new pages
- Web Admin has Settings field + ShipperEarnings + ShipperDetail + OrderDetail commission section
- ~30 new tests passing (5 calculator unit + 5 listener IT + earnings/settle endpoint smoke)
- Branch `feat/phase-15-shipper-commission` ready to merge

After merge, update thesis docs per spec §10 — add chương 6 mở rộng covering both Phase 14 and Phase 15 since the báo cáo print is fixed.
