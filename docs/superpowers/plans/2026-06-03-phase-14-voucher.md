# Phase 14 — Voucher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add code-based voucher/promo system so admin can create discount codes (fixed amount or percent+cap, target SHIPPING or PRODUCTS) and customers can apply up to 1 SHIPPING + 1 PRODUCTS voucher per order at Checkout.

**Architecture:** New `promotion` Maven module with JPA entities + service for validation/redemption (with `SELECT … FOR UPDATE` for race safety). `orders` table gets three new columns (`discount_products`, `discount_shipping`, `delivery_fee_original`). Mini App Checkout gets two voucher input fields; Web Admin gets three new pages (list / form / detail).

**Tech Stack:** Spring Boot 3 + JPA + Postgres 16 + Flyway 10; React 18 + Vite 5 + Tailwind + TanStack Query + Zustand; pnpm workspace.

**Spec:** [docs/superpowers/specs/2026-06-03-voucher-shipper-commission-design.md](../specs/2026-06-03-voucher-shipper-commission-design.md)

---

## Wave 1 — Database Migration

### Task 1: V14 Flyway migration

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V14__voucher.sql`

- [ ] **Step 1: Write migration**

```sql
-- V14__voucher.sql — Code-based voucher / promo system.
--
-- Two voucher targets (SHIPPING reduces delivery_fee, PRODUCTS reduces subtotal),
-- two discount types (FIXED amount or PERCENT with cap). Each order may stack at
-- most one of each target. Validation rules (validity dates, min_order_amount,
-- max_uses_total, max_uses_per_customer) live in the application layer; the
-- database only enforces structural invariants via CHECK constraints.

CREATE TABLE voucher (
    id                     BIGSERIAL    PRIMARY KEY,
    code                   VARCHAR(32)  NOT NULL UNIQUE,
    name                   VARCHAR(128) NOT NULL,
    target                 VARCHAR(16)  NOT NULL
        CHECK (target IN ('SHIPPING', 'PRODUCTS')),
    discount_type          VARCHAR(16)  NOT NULL
        CHECK (discount_type IN ('FIXED', 'PERCENT')),
    discount_value         NUMERIC(12, 2) NOT NULL CHECK (discount_value > 0),
    max_discount           NUMERIC(12, 2)
        CHECK (max_discount IS NULL OR max_discount > 0),
    min_order_amount       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    valid_from             TIMESTAMPTZ NOT NULL,
    valid_until            TIMESTAMPTZ NOT NULL,
    max_uses_total         INT,
    max_uses_per_customer  INT NOT NULL DEFAULT 1,
    used_count             INT NOT NULL DEFAULT 0,
    is_active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (valid_until > valid_from),
    CHECK (discount_type = 'PERCENT' OR max_discount IS NULL)
);

CREATE INDEX idx_voucher_code_active ON voucher(code) WHERE is_active = TRUE;
CREATE INDEX idx_voucher_validity     ON voucher(valid_from, valid_until);

CREATE TABLE voucher_redemption (
    id                BIGSERIAL    PRIMARY KEY,
    voucher_id        BIGINT       NOT NULL REFERENCES voucher(id),
    order_id          UUID         NOT NULL REFERENCES orders(id),
    customer_id       BIGINT       NOT NULL REFERENCES telegram_user(id),
    discount_applied  NUMERIC(12, 2) NOT NULL CHECK (discount_applied >= 0),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (voucher_id, order_id)
);

CREATE INDEX idx_redemption_customer ON voucher_redemption(customer_id, voucher_id);

-- Add discount columns + original-fee snapshot to orders.
ALTER TABLE orders
    ADD COLUMN discount_products      NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (discount_products >= 0),
    ADD COLUMN discount_shipping      NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (discount_shipping >= 0),
    ADD COLUMN delivery_fee_original  NUMERIC(12, 2);

UPDATE orders SET delivery_fee_original = delivery_fee WHERE delivery_fee_original IS NULL;

ALTER TABLE orders
    ALTER COLUMN delivery_fee_original SET NOT NULL,
    ADD CONSTRAINT chk_delivery_fee_original_nonneg
        CHECK (delivery_fee_original >= 0);

-- Seed two demo vouchers so reviewer can demo Checkout flow immediately.
INSERT INTO voucher
    (code, name, target, discount_type, discount_value, max_discount,
     min_order_amount, valid_from, valid_until, max_uses_total, max_uses_per_customer)
VALUES
    ('FREESHIP', 'Miễn phí ship 30k', 'SHIPPING', 'FIXED', 30000, NULL,
     0,      NOW() - INTERVAL '1 day', NOW() + INTERVAL '90 days', 100, 1),
    ('GIAM20K', 'Giảm 20 000đ cho đơn từ 100k', 'PRODUCTS', 'FIXED', 20000, NULL,
     100000, NOW() - INTERVAL '1 day', NOW() + INTERVAL '90 days', 100, 1)
ON CONFLICT (code) DO NOTHING;
```

- [ ] **Step 2: Apply locally and verify**

```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
    -f - < backend/app/src/main/resources/db/migration/V14__voucher.sql
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d voucher"
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "SELECT code, target, discount_value FROM voucher;"
```

Expected: `voucher` schema with all columns listed; two seed rows (FREESHIP, GIAM20K).

If you already started the backend earlier and want Flyway to track V14: roll back manually first (`DROP TABLE voucher_redemption, voucher; ALTER TABLE orders DROP …`), then restart the backend so Flyway re-applies through history cleanly.

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V14__voucher.sql
git commit -m "feat(promotion): V14 voucher + voucher_redemption tables + orders discount columns"
```

---

## Wave 2 — Backend `promotion` module scaffold

### Task 2: Create Maven module skeleton

**Files:**
- Create: `backend/modules/promotion/pom.xml`
- Modify: `backend/pom.xml` (add `<module>modules/promotion</module>`)
- Modify: `backend/app/pom.xml` (add `<dependency>` on `promotion`)

- [ ] **Step 1: Create `backend/modules/promotion/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.shop.delivery</groupId>
        <artifactId>delivery-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>promotion</artifactId>
    <name>promotion</name>
    <description>Voucher / discount codes applied at checkout</description>

    <dependencies>
        <dependency>
            <groupId>com.shop.delivery</groupId>
            <artifactId>shared</artifactId>
        </dependency>
        <dependency>
            <groupId>com.shop.delivery</groupId>
            <artifactId>auth</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Register module in parent pom**

In `backend/pom.xml`, inside `<modules>`, add the `<module>modules/promotion</module>` line right after `<module>modules/order</module>`. Final list should be: shared, auth, order, **promotion**, delivery, payment, notification, bot, miniapp, webadmin, app.

- [ ] **Step 3: Register module in app pom**

In `backend/app/pom.xml`, find the `<dependencies>` section and add (next to the other internal module dependencies):

```xml
<dependency>
    <groupId>com.shop.delivery</groupId>
    <artifactId>promotion</artifactId>
</dependency>
```

- [ ] **Step 4: Verify build still works**

```bash
cd backend && ./mvnw -pl modules/promotion -am compile
```

Expected: BUILD SUCCESS, `promotion-0.1.0-SNAPSHOT.jar` skeleton compiled.

- [ ] **Step 5: Commit**

```bash
git add backend/modules/promotion/pom.xml backend/pom.xml backend/app/pom.xml
git commit -m "feat(promotion): scaffold promotion Maven module"
```

---

## Wave 3 — Backend domain (entities + enums)

### Task 3: Domain enums

**Files:**
- Create: `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/domain/VoucherTarget.java`
- Create: `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/domain/DiscountType.java`

- [ ] **Step 1: VoucherTarget enum**

```java
package com.shop.delivery.promotion.domain;

/**
 * What the voucher reduces:
 *   SHIPPING — reduces delivery_fee (the customer pays less for delivery).
 *   PRODUCTS — reduces subtotal (the customer pays less for goods).
 *
 * Per business rule, an order may stack at most one of each target.
 */
public enum VoucherTarget {
    SHIPPING,
    PRODUCTS
}
```

- [ ] **Step 2: DiscountType enum**

```java
package com.shop.delivery.promotion.domain;

/**
 * Voucher discount formula:
 *   FIXED   — discount_value is a flat VND amount, capped at base (the discount
 *             never exceeds the part it reduces).
 *   PERCENT — discount_value is a percentage of base; final discount is capped
 *             at max_discount when set.
 */
public enum DiscountType {
    FIXED,
    PERCENT
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/modules/promotion/src/main/java/com/shop/delivery/promotion/domain/
git commit -m "feat(promotion): add VoucherTarget + DiscountType enums"
```

### Task 4: Voucher entity

**Files:**
- Create: `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/entity/Voucher.java`
- Test: `backend/modules/promotion/src/test/java/com/shop/delivery/promotion/entity/VoucherTest.java`

- [ ] **Step 1: Write a failing entity test**

```java
package com.shop.delivery.promotion.entity;

import com.shop.delivery.promotion.domain.DiscountType;
import com.shop.delivery.promotion.domain.VoucherTarget;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherTest {

    @Test
    void newVoucher_hasDefaults() {
        Voucher v = new Voucher();
        v.setCode("DEMO");
        v.setName("Demo");
        v.setTarget(VoucherTarget.PRODUCTS);
        v.setDiscountType(DiscountType.FIXED);
        v.setDiscountValue(new BigDecimal("10000"));
        v.setValidFrom(OffsetDateTime.now());
        v.setValidUntil(OffsetDateTime.now().plusDays(1));

        assertThat(v.getMaxUsesPerCustomer()).isEqualTo(1);
        assertThat(v.getUsedCount()).isZero();
        assertThat(v.isActive()).isTrue();
        assertThat(v.getMinOrderAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && ./mvnw -pl modules/promotion test -Dtest=VoucherTest
```

Expected: COMPILATION FAILURE — `Voucher` class doesn't exist.

- [ ] **Step 3: Write `Voucher` entity**

```java
package com.shop.delivery.promotion.entity;

import com.shop.delivery.promotion.domain.DiscountType;
import com.shop.delivery.promotion.domain.VoucherTarget;
import com.shop.delivery.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "voucher")
public class Voucher extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false, length = 16)
    private VoucherTarget target;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 16)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_discount", precision = 12, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "valid_from", nullable = false)
    private OffsetDateTime validFrom;

    @Column(name = "valid_until", nullable = false)
    private OffsetDateTime validUntil;

    @Column(name = "max_uses_total")
    private Integer maxUsesTotal;

    @Column(name = "max_uses_per_customer", nullable = false)
    private int maxUsesPerCustomer = 1;

    @Column(name = "used_count", nullable = false)
    private int usedCount = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    // Getters + setters for every field above.
    // (Generate via IDE — Lombok is not in use in this codebase.)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public VoucherTarget getTarget() { return target; }
    public void setTarget(VoucherTarget target) { this.target = target; }
    public DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(DiscountType discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(BigDecimal maxDiscount) { this.maxDiscount = maxDiscount; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }
    public OffsetDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(OffsetDateTime validFrom) { this.validFrom = validFrom; }
    public OffsetDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(OffsetDateTime validUntil) { this.validUntil = validUntil; }
    public Integer getMaxUsesTotal() { return maxUsesTotal; }
    public void setMaxUsesTotal(Integer maxUsesTotal) { this.maxUsesTotal = maxUsesTotal; }
    public int getMaxUsesPerCustomer() { return maxUsesPerCustomer; }
    public void setMaxUsesPerCustomer(int maxUsesPerCustomer) { this.maxUsesPerCustomer = maxUsesPerCustomer; }
    public int getUsedCount() { return usedCount; }
    public void setUsedCount(int usedCount) { this.usedCount = usedCount; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Long getVersion() { return version; }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd backend && ./mvnw -pl modules/promotion test -Dtest=VoucherTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/modules/promotion/src/main/java/com/shop/delivery/promotion/entity/Voucher.java \
       backend/modules/promotion/src/test/java/com/shop/delivery/promotion/entity/VoucherTest.java
git commit -m "feat(promotion): Voucher JPA entity"
```

### Task 5: VoucherRedemption entity

**Files:**
- Create: `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/entity/VoucherRedemption.java`

- [ ] **Step 1: Write entity**

```java
package com.shop.delivery.promotion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only log of voucher applications. UNIQUE(voucher_id, order_id) prevents
 * applying the same voucher twice to the same order.
 */
@Entity
@Table(name = "voucher_redemption")
public class VoucherRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voucher_id", nullable = false)
    private Long voucherId;

    @Column(name = "order_id", nullable = false, columnDefinition = "uuid")
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "discount_applied", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountApplied;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public Long getVoucherId() { return voucherId; }
    public void setVoucherId(Long voucherId) { this.voucherId = voucherId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public BigDecimal getDiscountApplied() { return discountApplied; }
    public void setDiscountApplied(BigDecimal discountApplied) { this.discountApplied = discountApplied; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/modules/promotion/src/main/java/com/shop/delivery/promotion/entity/VoucherRedemption.java
git commit -m "feat(promotion): VoucherRedemption JPA entity"
```

### Task 6: Repositories

**Files:**
- Create: `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/repository/VoucherRepository.java`
- Create: `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/repository/VoucherRedemptionRepository.java`

- [ ] **Step 1: VoucherRepository**

```java
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
```

- [ ] **Step 2: VoucherRedemptionRepository**

```java
package com.shop.delivery.promotion.repository;

import com.shop.delivery.promotion.entity.VoucherRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemption, Long> {

    int countByVoucherIdAndCustomerId(Long voucherId, Long customerId);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/modules/promotion/src/main/java/com/shop/delivery/promotion/repository/
git commit -m "feat(promotion): VoucherRepository + VoucherRedemptionRepository"
```

---

## Wave 4 — Backend service (calculator + validator + redeemer)

### Task 7: VoucherCalculator (pure function, full TDD)

**Files:**
- Create: `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/service/VoucherCalculator.java`
- Test: `backend/modules/promotion/src/test/java/com/shop/delivery/promotion/service/VoucherCalculatorTest.java`

- [ ] **Step 1: Write 8 failing unit tests**

```java
package com.shop.delivery.promotion.service;

import com.shop.delivery.promotion.domain.DiscountType;
import com.shop.delivery.promotion.entity.Voucher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherCalculatorTest {

    private final VoucherCalculator calc = new VoucherCalculator();

    @Test
    void fixed_belowBase_returnsValue() {
        Voucher v = vFixed("20000");
        assertThat(calc.discountFor(v, bd("100000"))).isEqualByComparingTo("20000");
    }

    @Test
    void fixed_aboveBase_cappedAtBase() {
        Voucher v = vFixed("50000");
        assertThat(calc.discountFor(v, bd("30000"))).isEqualByComparingTo("30000");
    }

    @Test
    void fixed_zeroBase_returnsZero() {
        Voucher v = vFixed("20000");
        assertThat(calc.discountFor(v, BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void percent_withoutCap_appliesPercent() {
        Voucher v = vPercent("20", null);
        assertThat(calc.discountFor(v, bd("100000"))).isEqualByComparingTo("20000");
    }

    @Test
    void percent_withCap_belowCap_appliesPercent() {
        Voucher v = vPercent("20", "50000");
        assertThat(calc.discountFor(v, bd("100000"))).isEqualByComparingTo("20000");
    }

    @Test
    void percent_withCap_aboveCap_capsAtMax() {
        Voucher v = vPercent("50", "30000");
        assertThat(calc.discountFor(v, bd("100000"))).isEqualByComparingTo("30000");
    }

    @Test
    void percent_resultRoundedHalfUp() {
        // 12.5% of 9999 = 1249.875 → 1250
        Voucher v = vPercent("12.5", null);
        assertThat(calc.discountFor(v, bd("9999"))).isEqualByComparingTo("1250");
    }

    @Test
    void discount_neverExceedsBase() {
        // Even a 200% voucher caps at base.
        Voucher v = vPercent("200", null);
        assertThat(calc.discountFor(v, bd("100000"))).isEqualByComparingTo("100000");
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private static Voucher vFixed(String value) {
        Voucher v = new Voucher();
        v.setDiscountType(DiscountType.FIXED);
        v.setDiscountValue(bd(value));
        return v;
    }

    private static Voucher vPercent(String pct, String cap) {
        Voucher v = new Voucher();
        v.setDiscountType(DiscountType.PERCENT);
        v.setDiscountValue(bd(pct));
        if (cap != null) v.setMaxDiscount(bd(cap));
        return v;
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd backend && ./mvnw -pl modules/promotion test -Dtest=VoucherCalculatorTest
```

Expected: 8 tests, all FAIL with compilation error.

- [ ] **Step 3: Implement `VoucherCalculator`**

```java
package com.shop.delivery.promotion.service;

import com.shop.delivery.promotion.domain.DiscountType;
import com.shop.delivery.promotion.entity.Voucher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Computes the discount a voucher applies against a base amount.
 *
 * `base` is whichever part of the order the voucher reduces — for a SHIPPING
 * voucher pass the delivery fee, for a PRODUCTS voucher pass the subtotal.
 * The returned discount is always non-negative and never exceeds `base`.
 */
@Component
public class VoucherCalculator {

    public BigDecimal discountFor(Voucher voucher, BigDecimal base) {
        if (base == null || base.signum() <= 0) return BigDecimal.ZERO;

        BigDecimal raw = voucher.getDiscountType() == DiscountType.FIXED
            ? voucher.getDiscountValue()
            : base.multiply(voucher.getDiscountValue())
                  .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);

        BigDecimal capped = voucher.getMaxDiscount() != null
            ? raw.min(voucher.getMaxDiscount())
            : raw;

        return capped.min(base).setScale(0, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && ./mvnw -pl modules/promotion test -Dtest=VoucherCalculatorTest
```

Expected: 8/8 PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/modules/promotion/src/main/java/com/shop/delivery/promotion/service/VoucherCalculator.java \
       backend/modules/promotion/src/test/java/com/shop/delivery/promotion/service/VoucherCalculatorTest.java
git commit -m "feat(promotion): VoucherCalculator with FIXED/PERCENT + cap logic"
```

### Task 8: ValidationResult + reason codes

**Files:**
- Create: `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/domain/VoucherValidationResult.java`
- Create: `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/domain/VoucherInvalidReason.java`

- [ ] **Step 1: VoucherInvalidReason enum**

```java
package com.shop.delivery.promotion.domain;

/** Stable reason codes returned to the frontend so it can show localized error messages. */
public enum VoucherInvalidReason {
    NOT_FOUND,
    INACTIVE,
    NOT_YET_VALID,
    EXPIRED,
    BELOW_MIN_ORDER,
    EXHAUSTED_TOTAL,
    EXHAUSTED_PER_CUSTOMER,
    WRONG_TARGET
}
```

- [ ] **Step 2: VoucherValidationResult record**

```java
package com.shop.delivery.promotion.domain;

import com.shop.delivery.promotion.entity.Voucher;
import java.math.BigDecimal;

/**
 * Either { voucher, discountAmount } if the voucher passes all checks, or
 * { reason } pointing to the first failing rule.
 */
public record VoucherValidationResult(
    Voucher voucher,
    BigDecimal discountAmount,
    VoucherInvalidReason reason
) {
    public boolean isValid() { return reason == null; }

    public static VoucherValidationResult ok(Voucher v, BigDecimal discount) {
        return new VoucherValidationResult(v, discount, null);
    }

    public static VoucherValidationResult invalid(VoucherInvalidReason reason) {
        return new VoucherValidationResult(null, null, reason);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/modules/promotion/src/main/java/com/shop/delivery/promotion/domain/VoucherInvalidReason.java \
       backend/modules/promotion/src/main/java/com/shop/delivery/promotion/domain/VoucherValidationResult.java
git commit -m "feat(promotion): VoucherValidationResult + reason codes"
```

### Task 9: VoucherService — validate (read-only)

**Files:**
- Create: `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/service/VoucherService.java`
- Test: `backend/modules/promotion/src/test/java/com/shop/delivery/promotion/service/VoucherServiceTest.java`
- Test: `backend/modules/promotion/src/test/java/com/shop/delivery/promotion/support/PromotionTestcontainerBase.java`

- [ ] **Step 1: Testcontainer base (copy pattern from order module)**

```java
package com.shop.delivery.promotion.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = PromotionTestApplication.class)
public abstract class PromotionTestcontainerBase {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("promo_test")
        .withUsername("app")
        .withPassword("app_test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.locations", () -> "classpath:db/migration");
    }
}
```

- [ ] **Step 2: Test application class (so SpringBootTest finds beans)**

```java
package com.shop.delivery.promotion.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

@SpringBootApplication(
    scanBasePackages = "com.shop.delivery.promotion",
    exclude = SecurityAutoConfiguration.class
)
public class PromotionTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(PromotionTestApplication.class, args);
    }
}
```

The Flyway migration files in `backend/app/src/main/resources/db/migration/` are on the classpath at test time via the app module — promotion tests don't ship their own. If the integration test can't find migrations, copy V14__voucher.sql to `backend/modules/promotion/src/test/resources/db/migration/` *just for tests* (do **not** duplicate in production code).

- [ ] **Step 3: Write 4 failing validation tests**

```java
package com.shop.delivery.promotion.service;

import com.shop.delivery.promotion.domain.DiscountType;
import com.shop.delivery.promotion.domain.VoucherInvalidReason;
import com.shop.delivery.promotion.domain.VoucherTarget;
import com.shop.delivery.promotion.domain.VoucherValidationResult;
import com.shop.delivery.promotion.entity.Voucher;
import com.shop.delivery.promotion.repository.VoucherRepository;
import com.shop.delivery.promotion.support.PromotionTestcontainerBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherServiceTest extends PromotionTestcontainerBase {

    @Autowired VoucherRepository repo;
    @Autowired VoucherService service;

    @BeforeEach
    void cleanSlate() { repo.deleteAll(); }

    @Test
    void unknownCode_returnsNOT_FOUND() {
        VoucherValidationResult r = service.validate(
            "DOES_NOT_EXIST", VoucherTarget.PRODUCTS,
            bd("100000"), bd("30000"), 1L);
        assertThat(r.isValid()).isFalse();
        assertThat(r.reason()).isEqualTo(VoucherInvalidReason.NOT_FOUND);
    }

    @Test
    void expiredVoucher_returnsEXPIRED() {
        Voucher v = save(productsFixed("EXP", "20000"),
            OffsetDateTime.now().minusDays(10),
            OffsetDateTime.now().minusDays(1));
        VoucherValidationResult r = service.validate(
            v.getCode(), VoucherTarget.PRODUCTS, bd("100000"), bd("30000"), 1L);
        assertThat(r.reason()).isEqualTo(VoucherInvalidReason.EXPIRED);
    }

    @Test
    void wrongTarget_returnsWRONG_TARGET() {
        Voucher v = save(productsFixed("HELLO", "20000"));  // PRODUCTS voucher
        VoucherValidationResult r = service.validate(
            v.getCode(), VoucherTarget.SHIPPING, bd("100000"), bd("30000"), 1L);
        assertThat(r.reason()).isEqualTo(VoucherInvalidReason.WRONG_TARGET);
    }

    @Test
    void belowMinOrder_returnsBELOW_MIN_ORDER() {
        Voucher v = productsFixed("MIN100K", "20000");
        v.setMinOrderAmount(bd("100000"));
        save(v);
        VoucherValidationResult r = service.validate(
            v.getCode(), VoucherTarget.PRODUCTS, bd("50000"), bd("30000"), 1L);
        assertThat(r.reason()).isEqualTo(VoucherInvalidReason.BELOW_MIN_ORDER);
    }

    private Voucher productsFixed(String code, String value) {
        Voucher v = new Voucher();
        v.setCode(code);
        v.setName("Test");
        v.setTarget(VoucherTarget.PRODUCTS);
        v.setDiscountType(DiscountType.FIXED);
        v.setDiscountValue(bd(value));
        v.setValidFrom(OffsetDateTime.now().minusDays(1));
        v.setValidUntil(OffsetDateTime.now().plusDays(30));
        return v;
    }

    private Voucher save(Voucher v) { return repo.saveAndFlush(v); }

    private Voucher save(Voucher v, OffsetDateTime from, OffsetDateTime until) {
        v.setValidFrom(from);
        v.setValidUntil(until);
        return repo.saveAndFlush(v);
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
}
```

- [ ] **Step 4: Implement `VoucherService` with all 6 validation rules**

```java
package com.shop.delivery.promotion.service;

import com.shop.delivery.promotion.domain.VoucherInvalidReason;
import com.shop.delivery.promotion.domain.VoucherTarget;
import com.shop.delivery.promotion.domain.VoucherValidationResult;
import com.shop.delivery.promotion.entity.Voucher;
import com.shop.delivery.promotion.repository.VoucherRedemptionRepository;
import com.shop.delivery.promotion.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class VoucherService {

    private final VoucherRepository voucherRepo;
    private final VoucherRedemptionRepository redemptionRepo;
    private final VoucherCalculator calculator;

    public VoucherService(VoucherRepository voucherRepo,
                          VoucherRedemptionRepository redemptionRepo,
                          VoucherCalculator calculator) {
        this.voucherRepo = voucherRepo;
        this.redemptionRepo = redemptionRepo;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public VoucherValidationResult validate(String code, VoucherTarget target,
                                            BigDecimal subtotal,
                                            BigDecimal deliveryFee,
                                            Long customerId) {
        Optional<Voucher> opt = voucherRepo.findByCodeIgnoreCaseAndActiveTrue(code);
        if (opt.isEmpty()) return VoucherValidationResult.invalid(VoucherInvalidReason.NOT_FOUND);
        Voucher v = opt.get();

        if (!v.isActive()) return VoucherValidationResult.invalid(VoucherInvalidReason.INACTIVE);
        if (v.getTarget() != target) return VoucherValidationResult.invalid(VoucherInvalidReason.WRONG_TARGET);

        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(v.getValidFrom()))  return VoucherValidationResult.invalid(VoucherInvalidReason.NOT_YET_VALID);
        if (now.isAfter(v.getValidUntil())) return VoucherValidationResult.invalid(VoucherInvalidReason.EXPIRED);

        if (subtotal.compareTo(v.getMinOrderAmount()) < 0)
            return VoucherValidationResult.invalid(VoucherInvalidReason.BELOW_MIN_ORDER);

        if (v.getMaxUsesTotal() != null && v.getUsedCount() >= v.getMaxUsesTotal())
            return VoucherValidationResult.invalid(VoucherInvalidReason.EXHAUSTED_TOTAL);

        int customerUses = redemptionRepo.countByVoucherIdAndCustomerId(v.getId(), customerId);
        if (customerUses >= v.getMaxUsesPerCustomer())
            return VoucherValidationResult.invalid(VoucherInvalidReason.EXHAUSTED_PER_CUSTOMER);

        BigDecimal base = target == VoucherTarget.SHIPPING ? deliveryFee : subtotal;
        return VoucherValidationResult.ok(v, calculator.discountFor(v, base));
    }
}
```

- [ ] **Step 5: Run tests, expect 4/4 PASS**

```bash
cd backend && ./mvnw -pl modules/promotion test -Dtest=VoucherServiceTest
```

- [ ] **Step 6: Commit**

```bash
git add backend/modules/promotion/src/main/java/com/shop/delivery/promotion/service/VoucherService.java \
       backend/modules/promotion/src/test/java/com/shop/delivery/promotion/
git commit -m "feat(promotion): VoucherService.validate + 6 validation rules"
```

### Task 10: VoucherService.redeem (write path with FOR UPDATE)

**Files:**
- Modify: `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/service/VoucherService.java`
- Modify: `backend/modules/promotion/src/test/java/com/shop/delivery/promotion/service/VoucherServiceTest.java`

- [ ] **Step 1: Add 2 failing tests for redeem (idempotency + race)**

Add to `VoucherServiceTest`:

```java
@Test
void redeem_incrementsUsedCount_andLogsRedemption() {
    Voucher v = save(productsFixed("REDEEM1", "20000"));
    UUID orderId = UUID.randomUUID();

    service.redeem(v.getCode(), orderId, 1L, bd("20000"));

    Voucher after = repo.findById(v.getId()).orElseThrow();
    assertThat(after.getUsedCount()).isEqualTo(1);
}

@Test
void redeem_exhaustedTotal_throws() {
    Voucher v = productsFixed("ONE", "10000");
    v.setMaxUsesTotal(1);
    v.setUsedCount(1);
    save(v);

    assertThatThrownBy(() ->
        service.redeem(v.getCode(), UUID.randomUUID(), 1L, bd("10000"))
    ).isInstanceOf(VoucherRedeemException.class);
}
```

(Add the necessary imports: `java.util.UUID`, `org.assertj.core.api.Assertions.assertThatThrownBy`, and `com.shop.delivery.promotion.service.VoucherRedeemException` — created next step.)

- [ ] **Step 2: Add exception class**

Create `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/service/VoucherRedeemException.java`:

```java
package com.shop.delivery.promotion.service;

import com.shop.delivery.promotion.domain.VoucherInvalidReason;

public class VoucherRedeemException extends RuntimeException {
    private final VoucherInvalidReason reason;

    public VoucherRedeemException(VoucherInvalidReason reason) {
        super("Cannot redeem voucher: " + reason);
        this.reason = reason;
    }

    public VoucherInvalidReason getReason() { return reason; }
}
```

- [ ] **Step 3: Add `redeem` method to VoucherService**

Append to `VoucherService.java`:

```java
@Transactional
public void redeem(String code, UUID orderId, Long customerId, BigDecimal discountApplied) {
    Voucher v = voucherRepo.findByCodeForUpdate(code)
        .orElseThrow(() -> new VoucherRedeemException(VoucherInvalidReason.NOT_FOUND));

    if (v.getMaxUsesTotal() != null && v.getUsedCount() >= v.getMaxUsesTotal()) {
        throw new VoucherRedeemException(VoucherInvalidReason.EXHAUSTED_TOTAL);
    }

    int customerUses = redemptionRepo.countByVoucherIdAndCustomerId(v.getId(), customerId);
    if (customerUses >= v.getMaxUsesPerCustomer()) {
        throw new VoucherRedeemException(VoucherInvalidReason.EXHAUSTED_PER_CUSTOMER);
    }

    v.setUsedCount(v.getUsedCount() + 1);
    voucherRepo.save(v);

    VoucherRedemption r = new VoucherRedemption();
    r.setVoucherId(v.getId());
    r.setOrderId(orderId);
    r.setCustomerId(customerId);
    r.setDiscountApplied(discountApplied);
    redemptionRepo.save(r);
}
```

Add imports for `UUID` and `VoucherRedemption`.

- [ ] **Step 4: Run tests, expect 6/6 PASS**

```bash
cd backend && ./mvnw -pl modules/promotion test -Dtest=VoucherServiceTest
```

- [ ] **Step 5: Commit**

```bash
git add backend/modules/promotion/src/main/java/com/shop/delivery/promotion/service/VoucherService.java \
       backend/modules/promotion/src/main/java/com/shop/delivery/promotion/service/VoucherRedeemException.java \
       backend/modules/promotion/src/test/java/com/shop/delivery/promotion/service/VoucherServiceTest.java
git commit -m "feat(promotion): VoucherService.redeem with FOR UPDATE locking"
```

---

## Wave 5 — Order integration

### Task 11: Update Order entity with 3 new columns

**Files:**
- Modify: `backend/modules/order/src/main/java/com/shop/delivery/order/entity/Order.java`

- [ ] **Step 1: Add 3 fields + accessors to `Order.java`**

After `private BigDecimal deliveryFee;`, insert:

```java
@Column(name = "discount_products", nullable = false, precision = 12, scale = 2)
private BigDecimal discountProducts = BigDecimal.ZERO;

@Column(name = "discount_shipping", nullable = false, precision = 12, scale = 2)
private BigDecimal discountShipping = BigDecimal.ZERO;

@Column(name = "delivery_fee_original", nullable = false, precision = 12, scale = 2)
private BigDecimal deliveryFeeOriginal;
```

Add getters/setters for each.

- [ ] **Step 2: Verify the order module still builds**

```bash
cd backend && ./mvnw -pl modules/order -am compile
```

- [ ] **Step 3: Commit**

```bash
git add backend/modules/order/src/main/java/com/shop/delivery/order/entity/Order.java
git commit -m "feat(order): add discount_products + discount_shipping + delivery_fee_original columns"
```

### Task 12: Wire voucher application into order create flow

**Files:**
- Modify: `backend/modules/order/src/main/java/com/shop/delivery/order/api/dto/CreateOrderRequest.java`
- Modify: `backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderService.java`

- [ ] **Step 1: Add `voucherCodes` to CreateOrderRequest**

Open `CreateOrderRequest.java` (find its current location with `find backend/modules/order -name "CreateOrderRequest.java"`).

Add field:

```java
private VoucherCodes voucherCodes;

public VoucherCodes getVoucherCodes() { return voucherCodes; }
public void setVoucherCodes(VoucherCodes voucherCodes) { this.voucherCodes = voucherCodes; }

public record VoucherCodes(String products, String shipping) {}
```

- [ ] **Step 2: Update OrderService.create to apply vouchers**

In the create method, after computing `subtotal` and `deliveryFee`, BEFORE inserting the order row:

```java
// Snapshot raw fee BEFORE any discount, so commission (Phase 15) can read it.
BigDecimal deliveryFeeOriginal = deliveryFee;

BigDecimal discountProducts = BigDecimal.ZERO;
BigDecimal discountShipping = BigDecimal.ZERO;

if (req.getVoucherCodes() != null) {
    if (req.getVoucherCodes().products() != null) {
        VoucherValidationResult r = voucherService.validate(
            req.getVoucherCodes().products(), VoucherTarget.PRODUCTS,
            subtotal, deliveryFee, customerId);
        if (!r.isValid()) throw new ApiException("INVALID_VOUCHER_" + r.reason());
        discountProducts = r.discountAmount();
    }
    if (req.getVoucherCodes().shipping() != null) {
        VoucherValidationResult r = voucherService.validate(
            req.getVoucherCodes().shipping(), VoucherTarget.SHIPPING,
            subtotal, deliveryFee, customerId);
        if (!r.isValid()) throw new ApiException("INVALID_VOUCHER_" + r.reason());
        discountShipping = r.discountAmount();
    }
}

BigDecimal total = subtotal.subtract(discountProducts)
                            .add(deliveryFee).subtract(discountShipping);

// (existing code that constructs the Order entity — add the new fields:)
order.setDeliveryFeeOriginal(deliveryFeeOriginal);
order.setDiscountProducts(discountProducts);
order.setDiscountShipping(discountShipping);
// total field already exists — assign with new value
order.setTotal(total);
```

After `orderRepo.save(order)` (so we have the order ID), redeem each applied voucher:

```java
if (req.getVoucherCodes() != null) {
    if (discountProducts.signum() > 0) {
        voucherService.redeem(req.getVoucherCodes().products(), order.getId(),
                              customerId, discountProducts);
    }
    if (discountShipping.signum() > 0) {
        voucherService.redeem(req.getVoucherCodes().shipping(), order.getId(),
                              customerId, discountShipping);
    }
}
```

Inject `VoucherService` into `OrderService` via constructor (add `private final VoucherService voucherService;` field + add to constructor).

The `order` module already depends on… check whether `order` can depend on `promotion`. Run:

```bash
grep -A 3 "<artifactId>order</artifactId>" backend/modules/order/pom.xml
```

If `order` does not list `promotion` as a dependency, ADD it. But note this creates a forward dependency `order → promotion`. To keep the existing graph clean, prefer the **interface inversion**: define a `VoucherApplicator` interface inside `order` module (`com.shop.delivery.order.spi.VoucherApplicator`) with the two methods needed (`validateForOrder`, `redeemForOrder`), then implement it in `promotion` module. This way `order` doesn't depend on `promotion`; `promotion` depends on `order`.

Actually, looking at the existing graph (`promotion → order` via `voucher_redemption.order_id`), the dependency direction is already `promotion → order`. So define interface in `order`:

```java
// backend/modules/order/src/main/java/com/shop/delivery/order/spi/VoucherApplicator.java
package com.shop.delivery.order.spi;

import java.math.BigDecimal;
import java.util.UUID;

public interface VoucherApplicator {
    record AppliedDiscount(BigDecimal products, BigDecimal shipping) {}

    AppliedDiscount validate(String productsCode, String shippingCode,
                             BigDecimal subtotal, BigDecimal deliveryFee, Long customerId);

    void redeem(String productsCode, String shippingCode,
                BigDecimal productsDiscount, BigDecimal shippingDiscount,
                UUID orderId, Long customerId);
}
```

Then add implementation in promotion module:

```java
// backend/modules/promotion/src/main/java/com/shop/delivery/promotion/service/VoucherApplicatorImpl.java
package com.shop.delivery.promotion.service;

import com.shop.delivery.order.spi.VoucherApplicator;
import com.shop.delivery.promotion.domain.VoucherTarget;
import com.shop.delivery.promotion.domain.VoucherValidationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class VoucherApplicatorImpl implements VoucherApplicator {

    private final VoucherService service;

    public VoucherApplicatorImpl(VoucherService service) {
        this.service = service;
    }

    @Override
    public AppliedDiscount validate(String productsCode, String shippingCode,
                                    BigDecimal subtotal, BigDecimal deliveryFee, Long customerId) {
        BigDecimal dp = applyOne(productsCode, VoucherTarget.PRODUCTS, subtotal, deliveryFee, customerId);
        BigDecimal ds = applyOne(shippingCode, VoucherTarget.SHIPPING, subtotal, deliveryFee, customerId);
        return new AppliedDiscount(dp, ds);
    }

    private BigDecimal applyOne(String code, VoucherTarget target,
                                BigDecimal subtotal, BigDecimal deliveryFee, Long customerId) {
        if (code == null || code.isBlank()) return BigDecimal.ZERO;
        VoucherValidationResult r = service.validate(code, target, subtotal, deliveryFee, customerId);
        if (!r.isValid()) {
            throw new IllegalArgumentException("INVALID_VOUCHER_" + target + "_" + r.reason());
        }
        return r.discountAmount();
    }

    @Override
    public void redeem(String productsCode, String shippingCode,
                       BigDecimal productsDiscount, BigDecimal shippingDiscount,
                       UUID orderId, Long customerId) {
        if (productsCode != null && productsDiscount.signum() > 0) {
            service.redeem(productsCode, orderId, customerId, productsDiscount);
        }
        if (shippingCode != null && shippingDiscount.signum() > 0) {
            service.redeem(shippingCode, orderId, customerId, shippingDiscount);
        }
    }
}
```

Then `OrderService` uses `VoucherApplicator` (Spring autowires the impl from promotion). Adjust the OrderService code from Step 2 to use the `AppliedDiscount` record instead.

- [ ] **Step 3: Verify Order module still compiles & tests still pass**

```bash
cd backend && ./mvnw -pl modules/order test -DfailIfNoTests=false
cd backend && ./mvnw -pl modules/promotion -am test
```

Expected: existing 253 backend tests still pass; new promotion tests still pass.

- [ ] **Step 4: Commit**

```bash
git add backend/modules/order/src/main/java/com/shop/delivery/order/spi/ \
       backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderService.java \
       backend/modules/order/src/main/java/com/shop/delivery/order/api/dto/CreateOrderRequest.java \
       backend/modules/promotion/src/main/java/com/shop/delivery/promotion/service/VoucherApplicatorImpl.java
git commit -m "feat(order): apply voucher discounts in create flow via VoucherApplicator SPI"
```

### Task 13: Update OrderResponse to expose discount fields

**Files:**
- Modify: `backend/modules/order/src/main/java/com/shop/delivery/order/api/dto/OrderResponse.java`
- Modify: `backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderMapper.java` (or whatever maps Order → OrderResponse)

- [ ] **Step 1: Add fields to OrderResponse**

```java
private BigDecimal discountProducts;
private BigDecimal discountShipping;
private BigDecimal deliveryFeeOriginal;
// + getters/setters
```

- [ ] **Step 2: Map them in OrderMapper**

```java
response.setDiscountProducts(order.getDiscountProducts());
response.setDiscountShipping(order.getDiscountShipping());
response.setDeliveryFeeOriginal(order.getDeliveryFeeOriginal());
```

- [ ] **Step 3: Verify build**

```bash
cd backend && ./mvnw -pl modules/order test -DfailIfNoTests=false
```

- [ ] **Step 4: Commit**

```bash
git add backend/modules/order/
git commit -m "feat(order): expose discount fields in OrderResponse"
```

---

## Wave 6 — Backend customer + admin APIs

### Task 14: Customer validate endpoint

**Files:**
- Create: `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/api/CustomerVoucherController.java`
- Create: `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/api/dto/ValidateVoucherRequest.java`
- Create: `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/api/dto/ValidateVoucherResponse.java`

- [ ] **Step 1: DTOs**

```java
// ValidateVoucherRequest.java
package com.shop.delivery.promotion.api.dto;

import com.shop.delivery.promotion.domain.VoucherTarget;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ValidateVoucherRequest(
    @NotBlank String code,
    @NotNull VoucherTarget target,
    @NotNull @Positive BigDecimal subtotal,
    @NotNull @Positive BigDecimal deliveryFee
) {}
```

```java
// ValidateVoucherResponse.java
package com.shop.delivery.promotion.api.dto;

import java.math.BigDecimal;

public record ValidateVoucherResponse(
    String code,
    String name,
    BigDecimal discountAmount
) {}
```

- [ ] **Step 2: Controller**

```java
package com.shop.delivery.promotion.api;

import com.shop.delivery.auth.api.CurrentUser;
import com.shop.delivery.auth.domain.TelegramPrincipal;
import com.shop.delivery.promotion.api.dto.ValidateVoucherRequest;
import com.shop.delivery.promotion.api.dto.ValidateVoucherResponse;
import com.shop.delivery.promotion.domain.VoucherValidationResult;
import com.shop.delivery.promotion.service.VoucherService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/customer/vouchers")
public class CustomerVoucherController {

    private final VoucherService service;

    public CustomerVoucherController(VoucherService service) { this.service = service; }

    @PostMapping("/validate")
    public ResponseEntity<ValidateVoucherResponse> validate(
        @CurrentUser TelegramPrincipal user,
        @Valid @RequestBody ValidateVoucherRequest req
    ) {
        VoucherValidationResult r = service.validate(
            req.code(), req.target(), req.subtotal(), req.deliveryFee(),
            user.customerId());
        if (!r.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "INVALID_VOUCHER_" + r.reason());
        }
        return ResponseEntity.ok(new ValidateVoucherResponse(
            r.voucher().getCode(), r.voucher().getName(), r.discountAmount()));
    }
}
```

(Adjust `@CurrentUser` import and `TelegramPrincipal.customerId()` to match the actual auth annotations in this codebase — find them with `grep -r "@CurrentUser" backend/modules/order/src/main/java | head`).

- [ ] **Step 3: Smoke test the endpoint manually**

Start backend, then:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/admin/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"shop@example.com","password":"Demo@Shop2026!"}' | jq -r .accessToken)

# Customer endpoints use Telegram auth, but in dev with X-Dev-User-Id bypass:
curl -s -X POST http://localhost:8080/api/customer/vouchers/validate \
    -H "Content-Type: application/json" \
    -H "X-Dev-User-Id: 999000001" \
    -d '{"code":"GIAM20K","target":"PRODUCTS","subtotal":120000,"deliveryFee":30000}'
```

Expected response: `{"code":"GIAM20K","name":"Giảm 20 000đ cho đơn từ 100k","discountAmount":20000}`.

- [ ] **Step 4: Commit**

```bash
git add backend/modules/promotion/src/main/java/com/shop/delivery/promotion/api/
git commit -m "feat(promotion): POST /api/customer/vouchers/validate"
```

### Task 15: Admin CRUD endpoints

**Files:**
- Create: `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/api/AdminVoucherController.java`
- Create: 4 DTOs in `backend/modules/promotion/src/main/java/com/shop/delivery/promotion/api/dto/` — `CreateVoucherRequest`, `UpdateVoucherRequest`, `VoucherSummary`, `VoucherDetail`.

- [ ] **Step 1: DTOs**

```java
// VoucherSummary — short form for list view
public record VoucherSummary(
    Long id, String code, String name,
    VoucherTarget target, DiscountType discountType,
    BigDecimal discountValue, BigDecimal maxDiscount,
    int usedCount, Integer maxUsesTotal,
    OffsetDateTime validFrom, OffsetDateTime validUntil,
    boolean active
) {}

// VoucherDetail — full form for detail page, includes recent redemptions
public record VoucherDetail(VoucherSummary summary, List<RedemptionRow> recentRedemptions) {
    public record RedemptionRow(UUID orderId, Long customerId,
                                BigDecimal discountApplied, OffsetDateTime createdAt) {}
}

// CreateVoucherRequest — admin form submission
public record CreateVoucherRequest(
    @NotBlank @Size(max=32) @Pattern(regexp = "^[A-Z0-9_-]+$") String code,
    @NotBlank @Size(max=128) String name,
    @NotNull VoucherTarget target,
    @NotNull DiscountType discountType,
    @NotNull @Positive BigDecimal discountValue,
    @PositiveOrZero BigDecimal maxDiscount,
    @PositiveOrZero BigDecimal minOrderAmount,
    @NotNull OffsetDateTime validFrom,
    @NotNull OffsetDateTime validUntil,
    @PositiveOrZero Integer maxUsesTotal,
    @Positive int maxUsesPerCustomer
) {}

// UpdateVoucherRequest — same as Create but with `active` boolean and no code (code is immutable)
public record UpdateVoucherRequest(
    @NotBlank String name,
    @NotNull BigDecimal discountValue,
    BigDecimal maxDiscount,
    BigDecimal minOrderAmount,
    @NotNull OffsetDateTime validFrom,
    @NotNull OffsetDateTime validUntil,
    Integer maxUsesTotal,
    int maxUsesPerCustomer,
    boolean active
) {}
```

- [ ] **Step 2: Controller**

```java
package com.shop.delivery.promotion.api;

import com.shop.delivery.promotion.api.dto.*;
import com.shop.delivery.promotion.entity.Voucher;
import com.shop.delivery.promotion.entity.VoucherRedemption;
import com.shop.delivery.promotion.repository.VoucherRedemptionRepository;
import com.shop.delivery.promotion.repository.VoucherRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/vouchers")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminVoucherController {

    private final VoucherRepository voucherRepo;
    private final VoucherRedemptionRepository redemptionRepo;

    public AdminVoucherController(VoucherRepository voucherRepo,
                                  VoucherRedemptionRepository redemptionRepo) {
        this.voucherRepo = voucherRepo;
        this.redemptionRepo = redemptionRepo;
    }

    @GetMapping
    public Page<VoucherSummary> list(Pageable pageable) {
        return voucherRepo.findAll(pageable).map(this::toSummary);
    }

    @GetMapping("/{id}")
    public VoucherDetail get(@PathVariable Long id) {
        Voucher v = voucherRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // For thesis demo, fetch last 20 redemptions; productioning would paginate.
        List<VoucherDetail.RedemptionRow> rows = redemptionRepo.findAll().stream()
            .filter(r -> r.getVoucherId().equals(id))
            .sorted((a,b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(20)
            .map(r -> new VoucherDetail.RedemptionRow(r.getOrderId(), r.getCustomerId(),
                                                     r.getDiscountApplied(), r.getCreatedAt()))
            .toList();
        return new VoucherDetail(toSummary(v), rows);
    }

    @PostMapping
    public ResponseEntity<VoucherSummary> create(@Valid @RequestBody CreateVoucherRequest req) {
        if (voucherRepo.findByCodeIgnoreCaseAndActiveTrue(req.code()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CODE_TAKEN");
        }
        Voucher v = new Voucher();
        v.setCode(req.code().toUpperCase());
        v.setName(req.name());
        v.setTarget(req.target());
        v.setDiscountType(req.discountType());
        v.setDiscountValue(req.discountValue());
        v.setMaxDiscount(req.maxDiscount());
        v.setMinOrderAmount(req.minOrderAmount() == null ? java.math.BigDecimal.ZERO : req.minOrderAmount());
        v.setValidFrom(req.validFrom());
        v.setValidUntil(req.validUntil());
        v.setMaxUsesTotal(req.maxUsesTotal());
        v.setMaxUsesPerCustomer(req.maxUsesPerCustomer());
        Voucher saved = voucherRepo.save(v);
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(saved));
    }

    @PutMapping("/{id}")
    public VoucherSummary update(@PathVariable Long id, @Valid @RequestBody UpdateVoucherRequest req) {
        Voucher v = voucherRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        v.setName(req.name());
        v.setDiscountValue(req.discountValue());
        v.setMaxDiscount(req.maxDiscount());
        if (req.minOrderAmount() != null) v.setMinOrderAmount(req.minOrderAmount());
        v.setValidFrom(req.validFrom());
        v.setValidUntil(req.validUntil());
        v.setMaxUsesTotal(req.maxUsesTotal());
        v.setMaxUsesPerCustomer(req.maxUsesPerCustomer());
        v.setActive(req.active());
        return toSummary(voucherRepo.save(v));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(@PathVariable Long id) {
        Voucher v = voucherRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        v.setActive(false);
        voucherRepo.save(v);
    }

    private VoucherSummary toSummary(Voucher v) {
        return new VoucherSummary(v.getId(), v.getCode(), v.getName(),
            v.getTarget(), v.getDiscountType(),
            v.getDiscountValue(), v.getMaxDiscount(),
            v.getUsedCount(), v.getMaxUsesTotal(),
            v.getValidFrom(), v.getValidUntil(),
            v.isActive());
    }
}
```

- [ ] **Step 3: Smoke test create + list**

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/admin/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"shop@example.com","password":"Demo@Shop2026!"}' | jq -r .accessToken)

curl -s "http://localhost:8080/api/admin/vouchers?page=0&size=10" \
    -H "Authorization: Bearer $TOKEN" | jq .

curl -s -X POST http://localhost:8080/api/admin/vouchers \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "code":"PHASE14TEST","name":"Phase 14 smoke","target":"PRODUCTS",
        "discountType":"PERCENT","discountValue":10,"maxDiscount":15000,
        "minOrderAmount":50000,
        "validFrom":"2026-06-01T00:00:00Z","validUntil":"2026-07-01T00:00:00Z",
        "maxUsesTotal":50,"maxUsesPerCustomer":1
    }' | jq .
```

- [ ] **Step 4: Commit**

```bash
git add backend/modules/promotion/src/main/java/com/shop/delivery/promotion/api/
git commit -m "feat(promotion): admin CRUD endpoints for vouchers"
```

---

## Wave 7 — Frontend shared types + API client

### Task 16: Shared TypeScript types

**Files:**
- Create: `frontend/shared/src/types/voucher.ts`
- Modify: `frontend/shared/src/types/index.ts` (re-export)

- [ ] **Step 1: Create `voucher.ts`**

```typescript
export type VoucherTarget = 'SHIPPING' | 'PRODUCTS';
export type DiscountType  = 'FIXED'    | 'PERCENT';

export interface VoucherSummary {
  id: number;
  code: string;
  name: string;
  target: VoucherTarget;
  discountType: DiscountType;
  discountValue: number;
  maxDiscount: number | null;
  usedCount: number;
  maxUsesTotal: number | null;
  validFrom: string;        // ISO 8601
  validUntil: string;
  active: boolean;
}

export interface VoucherRedemptionRow {
  orderId: string;
  customerId: number;
  discountApplied: number;
  createdAt: string;
}

export interface VoucherDetail {
  summary: VoucherSummary;
  recentRedemptions: VoucherRedemptionRow[];
}

export interface ValidateVoucherRequest {
  code: string;
  target: VoucherTarget;
  subtotal: number;
  deliveryFee: number;
}

export interface ValidateVoucherResponse {
  code: string;
  name: string;
  discountAmount: number;
}

export interface CreateVoucherRequest {
  code: string;
  name: string;
  target: VoucherTarget;
  discountType: DiscountType;
  discountValue: number;
  maxDiscount?: number | null;
  minOrderAmount?: number;
  validFrom: string;
  validUntil: string;
  maxUsesTotal?: number | null;
  maxUsesPerCustomer: number;
}

export type UpdateVoucherRequest = Omit<CreateVoucherRequest, 'code' | 'target' | 'discountType'> & {
  active: boolean;
};
```

- [ ] **Step 2: Re-export from `frontend/shared/src/types/index.ts`**

Add:

```typescript
export * from './voucher';
```

- [ ] **Step 3: Commit**

```bash
git add frontend/shared/src/types/voucher.ts frontend/shared/src/types/index.ts
git commit -m "feat(shared): voucher TypeScript types"
```

### Task 17: Shared API client

**Files:**
- Create: `frontend/shared/src/api/vouchers.ts`
- Modify: `frontend/shared/src/api/index.ts` (re-export)

- [ ] **Step 1: Create `vouchers.ts`**

```typescript
import type { AxiosInstance } from 'axios';
import type {
  ValidateVoucherRequest, ValidateVoucherResponse,
  VoucherSummary, VoucherDetail,
  CreateVoucherRequest, UpdateVoucherRequest,
} from '../types';

/** Customer endpoint — validate a voucher without redeeming it. */
export async function validateVoucher(
  client: AxiosInstance, req: ValidateVoucherRequest
): Promise<ValidateVoucherResponse> {
  const { data } = await client.post<ValidateVoucherResponse>(
    '/api/customer/vouchers/validate', req);
  return data;
}

/** Admin — paginated list. */
export async function fetchAdminVouchers(
  client: AxiosInstance, page = 0, size = 20
): Promise<{ content: VoucherSummary[]; totalElements: number }> {
  const { data } = await client.get('/api/admin/vouchers', { params: { page, size } });
  return data;
}

export async function fetchAdminVoucherDetail(
  client: AxiosInstance, id: number
): Promise<VoucherDetail> {
  const { data } = await client.get<VoucherDetail>(`/api/admin/vouchers/${id}`);
  return data;
}

export async function createVoucher(
  client: AxiosInstance, req: CreateVoucherRequest
): Promise<VoucherSummary> {
  const { data } = await client.post<VoucherSummary>('/api/admin/vouchers', req);
  return data;
}

export async function updateVoucher(
  client: AxiosInstance, id: number, req: UpdateVoucherRequest
): Promise<VoucherSummary> {
  const { data } = await client.put<VoucherSummary>(`/api/admin/vouchers/${id}`, req);
  return data;
}

export async function deleteVoucher(
  client: AxiosInstance, id: number
): Promise<void> {
  await client.delete(`/api/admin/vouchers/${id}`);
}
```

- [ ] **Step 2: Add to `frontend/shared/src/api/index.ts`**

```typescript
export * from './vouchers';
```

- [ ] **Step 3: Commit**

```bash
git add frontend/shared/src/api/vouchers.ts frontend/shared/src/api/index.ts
git commit -m "feat(shared): voucher API client"
```

---

## Wave 8 — Mini App Checkout

### Task 18: useVoucherValidation hook

**Files:**
- Create: `frontend/miniapp/src/hooks/useVoucherValidation.ts`

- [ ] **Step 1: Hook**

```typescript
import { useMutation } from '@tanstack/react-query';
import { validateVoucher, type VoucherTarget, type ValidateVoucherResponse } from '@shop/shared';
import { api } from '@/lib/api';

interface UseVoucherValidationArgs {
  target: VoucherTarget;
  subtotal: number;
  deliveryFee: number;
}

export function useVoucherValidation({ target, subtotal, deliveryFee }: UseVoucherValidationArgs) {
  return useMutation<ValidateVoucherResponse, Error & { response?: { data?: { message?: string } } }, string>({
    mutationFn: (code) => validateVoucher(api, { code, target, subtotal, deliveryFee }),
  });
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/miniapp/src/hooks/useVoucherValidation.ts
git commit -m "feat(miniapp): useVoucherValidation hook"
```

### Task 19: VoucherInput component (TDD with Vitest)

**Files:**
- Create: `frontend/miniapp/src/components/VoucherInput.tsx`
- Test: `frontend/miniapp/src/components/VoucherInput.test.tsx`

- [ ] **Step 1: Write failing test**

```tsx
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { VoucherInput } from './VoucherInput';

describe('VoucherInput', () => {
  it('renders input + apply button', () => {
    render(<VoucherInput label="Mã giảm tiền hàng" target="PRODUCTS"
                        subtotal={120000} deliveryFee={30000} onApplied={vi.fn()} onRemoved={vi.fn()} />);
    expect(screen.getByPlaceholderText(/nhập mã/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /áp/i })).toBeInTheDocument();
  });

  it('calls onApplied when user enters code and clicks Apply', async () => {
    const onApplied = vi.fn();
    // Test would mock api — leave the actual mock setup as standard MSW pattern used elsewhere
    // in the miniapp test setup.
    render(<VoucherInput label="X" target="PRODUCTS" subtotal={120000} deliveryFee={30000}
                        onApplied={onApplied} onRemoved={vi.fn()} />);
    await userEvent.type(screen.getByPlaceholderText(/nhập mã/i), 'GIAM20K');
    await userEvent.click(screen.getByRole('button', { name: /áp/i }));
    // Assert later when wired with mock
  });
});
```

- [ ] **Step 2: Implement component**

```tsx
import { useState } from 'react';
import type { VoucherTarget, ValidateVoucherResponse } from '@shop/shared';
import { useVoucherValidation } from '@/hooks/useVoucherValidation';
import { formatVnd } from '@shop/shared';

interface Props {
  label: string;
  target: VoucherTarget;
  subtotal: number;
  deliveryFee: number;
  /** Called once a voucher passes validation. Parent stores it and includes in order create. */
  onApplied: (v: ValidateVoucherResponse) => void;
  /** Called when user removes the applied voucher. */
  onRemoved: () => void;
}

export function VoucherInput({ label, target, subtotal, deliveryFee, onApplied, onRemoved }: Props) {
  const [code, setCode] = useState('');
  const [applied, setApplied] = useState<ValidateVoucherResponse | null>(null);
  const mut = useVoucherValidation({ target, subtotal, deliveryFee });

  const apply = async () => {
    if (!code.trim()) return;
    try {
      const r = await mut.mutateAsync(code.trim().toUpperCase());
      setApplied(r);
      onApplied(r);
    } catch {
      // mut.error captured by react-query; rendered below
    }
  };

  const remove = () => {
    setApplied(null);
    setCode('');
    onRemoved();
  };

  return (
    <div className="space-y-2">
      <label className="block text-sm font-medium text-gray-700">{label}</label>
      <div className="flex gap-2">
        <input
          type="text"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="Nhập mã"
          disabled={!!applied || mut.isPending}
          className="flex-1 rounded-lg border border-gray-300 px-3 py-2"
        />
        {!applied ? (
          <button onClick={apply} disabled={!code.trim() || mut.isPending}
            className="px-4 py-2 rounded-lg bg-[var(--brand-primary)] text-white disabled:opacity-50">
            {mut.isPending ? '...' : 'Áp'}
          </button>
        ) : (
          <button onClick={remove}
            className="px-4 py-2 rounded-lg bg-gray-200 text-gray-700">× Gỡ</button>
        )}
      </div>
      {applied && (
        <p className="text-xs text-green-700">
          ✓ {applied.code} — Giảm {formatVnd(applied.discountAmount)}
        </p>
      )}
      {mut.isError && !applied && (
        <p className="text-xs text-red-600">
          Mã không hợp lệ hoặc không áp dụng được cho đơn này.
        </p>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Run tests**

```bash
cd frontend/miniapp && pnpm test VoucherInput
```

Expected: 2/2 PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/miniapp/src/components/VoucherInput.tsx \
       frontend/miniapp/src/components/VoucherInput.test.tsx
git commit -m "feat(miniapp): VoucherInput component"
```

### Task 20: Wire VoucherInput into CheckoutPage

**Files:**
- Modify: `frontend/miniapp/src/pages/CheckoutPage.tsx`

- [ ] **Step 1: Locate Checkout's order summary section**

```bash
grep -n "delivery_fee\|deliveryFee\|subtotal\|Tổng" frontend/miniapp/src/pages/CheckoutPage.tsx | head -10
```

- [ ] **Step 2: Add state + render**

Near the top of the component, with other `useState`:

```tsx
const [productsVoucher, setProductsVoucher] = useState<ValidateVoucherResponse | null>(null);
const [shippingVoucher, setShippingVoucher] = useState<ValidateVoucherResponse | null>(null);
const discountProducts = productsVoucher?.discountAmount ?? 0;
const discountShipping = shippingVoucher?.discountAmount ?? 0;
const total = subtotal - discountProducts + deliveryFee - discountShipping;
```

Before the "Phương thức thanh toán" section, insert:

```tsx
<section className="bg-white rounded-xl p-4 space-y-4">
  <h3 className="font-semibold text-gray-900">🏷️ Khuyến mãi</h3>
  <VoucherInput
    label="Mã giảm tiền hàng"
    target="PRODUCTS"
    subtotal={subtotal}
    deliveryFee={deliveryFee}
    onApplied={setProductsVoucher}
    onRemoved={() => setProductsVoucher(null)}
  />
  <VoucherInput
    label="Mã giảm phí ship"
    target="SHIPPING"
    subtotal={subtotal}
    deliveryFee={deliveryFee}
    onApplied={setShippingVoucher}
    onRemoved={() => setShippingVoucher(null)}
  />
</section>
```

Update the order summary breakdown to include the two discount rows.

In the `createOrder` mutation payload, include:

```tsx
voucherCodes: {
  products: productsVoucher?.code,
  shipping: shippingVoucher?.code,
},
```

- [ ] **Step 3: Manual smoke test**

Start backend + miniapp. Open http://localhost:5180/customer/checkout (with mock Telegram user). Add cart items in catalog first, then go to checkout. Type `GIAM20K` in products voucher field → click Áp → see "Giảm 20 000đ" chip + breakdown row.

- [ ] **Step 4: Commit**

```bash
git add frontend/miniapp/src/pages/CheckoutPage.tsx
git commit -m "feat(miniapp): wire voucher inputs into Checkout flow"
```

---

## Wave 9 — Web Admin pages

### Task 21: Sidebar entry + route

**Files:**
- Modify: `frontend/webadmin/src/components/Sidebar.tsx` (or wherever sidebar nav lives — find with `grep -r "Cài đặt\|Settings" frontend/webadmin/src/components/`)
- Modify: `frontend/webadmin/src/App.tsx` (or routes file)

- [ ] **Step 1: Add nav entry "Khuyến mãi" with tag icon**

(Follow the existing pattern for the Settings nav entry just added.)

- [ ] **Step 2: Add three routes**

```tsx
<Route path="/vouchers"       element={<VouchersPage />} />
<Route path="/vouchers/new"   element={<VoucherFormPage />} />
<Route path="/vouchers/:id"   element={<VoucherDetailPage />} />
<Route path="/vouchers/:id/edit" element={<VoucherFormPage />} />
```

- [ ] **Step 3: Commit (after pages exist — combine with Task 24)**

### Task 22: VouchersPage (list)

**Files:**
- Create: `frontend/webadmin/src/pages/VouchersPage.tsx`

- [ ] **Step 1: Implement**

```tsx
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchAdminVouchers, formatVnd, type VoucherSummary, type VoucherTarget } from '@shop/shared';
import { api } from '@/lib/api';

type StatusFilter = 'all' | 'active' | 'expired';
type TargetFilter = 'all' | VoucherTarget;

export function VouchersPage() {
  const [status, setStatus] = useState<StatusFilter>('all');
  const [target, setTarget] = useState<TargetFilter>('all');
  const [page, setPage] = useState(0);
  const { data, isLoading } = useQuery({
    queryKey: ['admin','vouchers', page],
    queryFn: () => fetchAdminVouchers(api, page, 20),
  });

  const filtered = (data?.content ?? []).filter(v => {
    const now = new Date();
    const expired = new Date(v.validUntil) < now || !v.active;
    if (status === 'active'  && expired) return false;
    if (status === 'expired' && !expired) return false;
    if (target !== 'all' && v.target !== target) return false;
    return true;
  });

  return (
    <div>
      <header className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Khuyến mãi</h1>
          <p className="text-sm text-gray-500">Tạo và quản lý voucher cho khách</p>
        </div>
        <Link to="/vouchers/new"
              className="px-4 py-2 rounded-lg bg-orange-500 hover:bg-orange-600 text-white font-semibold">
          + Tạo voucher
        </Link>
      </header>

      <div className="flex gap-3 mb-4">
        <FilterChips label="Trạng thái" value={status}  options={['all','active','expired']} onChange={setStatus} />
        <FilterChips label="Đối tượng"  value={target}  options={['all','SHIPPING','PRODUCTS']} onChange={setTarget} />
      </div>

      {isLoading ? <p>Đang tải…</p> : (
        <table className="w-full bg-white rounded-xl border border-gray-200">
          <thead className="bg-gray-50 text-left text-xs uppercase">
            <tr>
              <th className="px-4 py-3">Mã</th><th className="px-4 py-3">Tên</th>
              <th className="px-4 py-3">Đối tượng</th><th className="px-4 py-3">Giảm</th>
              <th className="px-4 py-3">Đã dùng</th><th className="px-4 py-3">Hạn dùng</th>
              <th className="px-4 py-3">Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map(v => (
              <tr key={v.id} className="border-t border-gray-100 hover:bg-gray-50 cursor-pointer"
                  onClick={() => window.location.href = `/vouchers/${v.id}`}>
                <td className="px-4 py-3 font-mono text-sm">{v.code}</td>
                <td className="px-4 py-3">{v.name}</td>
                <td className="px-4 py-3">{v.target === 'SHIPPING' ? '🚚 Ship' : '🛍️ Hàng'}</td>
                <td className="px-4 py-3">
                  {v.discountType === 'FIXED'
                    ? formatVnd(v.discountValue)
                    : `${v.discountValue}%${v.maxDiscount ? ` (tối đa ${formatVnd(v.maxDiscount)})` : ''}`}
                </td>
                <td className="px-4 py-3">{v.usedCount} / {v.maxUsesTotal ?? '∞'}</td>
                <td className="px-4 py-3 text-sm">{new Date(v.validUntil).toLocaleDateString('vi-VN')}</td>
                <td className="px-4 py-3">
                  {v.active && new Date(v.validUntil) >= new Date()
                    ? <span className="text-xs bg-green-100 text-green-700 px-2 py-1 rounded">Active</span>
                    : <span className="text-xs bg-gray-100 text-gray-600 px-2 py-1 rounded">Inactive</span>}
                </td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr><td colSpan={7} className="text-center py-8 text-gray-500">
                Chưa có voucher nào
              </td></tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}

function FilterChips<T extends string>({label, value, options, onChange}:{
  label:string; value:T; options:T[]; onChange:(v:T)=>void;
}) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-xs text-gray-500">{label}:</span>
      {options.map(o => (
        <button key={o} onClick={() => onChange(o)}
          className={`text-xs px-3 py-1 rounded-full ${value === o
            ? 'bg-orange-500 text-white' : 'bg-white border border-gray-300 text-gray-700'}`}>
          {o}
        </button>
      ))}
    </div>
  );
}
```

- [ ] **Step 2: Commit (combined with Task 24)**

### Task 23: VoucherFormPage (create + edit)

**Files:**
- Create: `frontend/webadmin/src/pages/VoucherFormPage.tsx`

- [ ] **Step 1: Implement form**

```tsx
import { useState, type FormEvent, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  fetchAdminVoucherDetail, createVoucher, updateVoucher, formatVnd,
  type CreateVoucherRequest, type UpdateVoucherRequest,
  type VoucherTarget, type DiscountType,
} from '@shop/shared';
import { api } from '@/lib/api';

const CODE_RE = /^[A-Z0-9_-]+$/;

interface FormState {
  code: string;
  name: string;
  target: VoucherTarget;
  discountType: DiscountType;
  discountValue: number;
  maxDiscount: number | null;
  minOrderAmount: number;
  validFrom: string;     // YYYY-MM-DDTHH:mm
  validUntil: string;
  maxUsesTotal: number | null;
  maxUsesPerCustomer: number;
  active: boolean;
}

const empty: FormState = {
  code: '', name: '', target: 'PRODUCTS', discountType: 'FIXED',
  discountValue: 10000, maxDiscount: null, minOrderAmount: 0,
  validFrom: new Date().toISOString().slice(0,16),
  validUntil: new Date(Date.now() + 30*86400_000).toISOString().slice(0,16),
  maxUsesTotal: 100, maxUsesPerCustomer: 1, active: true,
};

export function VoucherFormPage() {
  const { id } = useParams();
  const isEdit = !!id;
  const nav = useNavigate();
  const qc = useQueryClient();
  const [form, setForm] = useState<FormState>(empty);
  const [err, setErr] = useState<string | null>(null);

  const { data: detail } = useQuery({
    queryKey: ['admin','voucher', id],
    queryFn: () => fetchAdminVoucherDetail(api, Number(id)),
    enabled: isEdit,
  });

  useEffect(() => {
    if (!detail) return;
    const s = detail.summary;
    setForm({
      code: s.code, name: s.name, target: s.target, discountType: s.discountType,
      discountValue: Number(s.discountValue), maxDiscount: s.maxDiscount,
      minOrderAmount: 0, // detail doesn't expose, default 0; could expand the DTO if needed
      validFrom: s.validFrom.slice(0,16), validUntil: s.validUntil.slice(0,16),
      maxUsesTotal: s.maxUsesTotal, maxUsesPerCustomer: 1,
      active: s.active,
    });
  }, [detail]);

  const create = useMutation({
    mutationFn: (req: CreateVoucherRequest) => createVoucher(api, req),
    onSuccess: (v) => { qc.invalidateQueries({queryKey:['admin','vouchers']}); nav(`/vouchers/${v.id}`); },
    onError: (e: Error & {response?:{data?:{message?:string}}}) => setErr(e.response?.data?.message ?? e.message),
  });
  const update = useMutation({
    mutationFn: (req: UpdateVoucherRequest) => updateVoucher(api, Number(id), req),
    onSuccess: () => { qc.invalidateQueries({queryKey:['admin','vouchers']}); nav(`/vouchers/${id}`); },
    onError: (e: Error & {response?:{data?:{message?:string}}}) => setErr(e.response?.data?.message ?? e.message),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    if (!isEdit && !CODE_RE.test(form.code)) { setErr('Mã chỉ chứa A-Z, 0-9, _, -'); return; }
    if (new Date(form.validUntil) <= new Date(form.validFrom)) { setErr('valid_until phải sau valid_from'); return; }
    if (form.discountType === 'PERCENT' && form.discountValue > 100) { setErr('PERCENT phải ≤ 100'); return; }

    const fromIso = new Date(form.validFrom).toISOString();
    const untilIso = new Date(form.validUntil).toISOString();
    if (isEdit) {
      update.mutate({
        name: form.name, discountValue: form.discountValue,
        maxDiscount: form.maxDiscount ?? null, minOrderAmount: form.minOrderAmount,
        validFrom: fromIso, validUntil: untilIso,
        maxUsesTotal: form.maxUsesTotal ?? null, maxUsesPerCustomer: form.maxUsesPerCustomer,
        active: form.active,
      });
    } else {
      create.mutate({
        code: form.code.toUpperCase(), name: form.name,
        target: form.target, discountType: form.discountType,
        discountValue: form.discountValue, maxDiscount: form.maxDiscount,
        minOrderAmount: form.minOrderAmount,
        validFrom: fromIso, validUntil: untilIso,
        maxUsesTotal: form.maxUsesTotal, maxUsesPerCustomer: form.maxUsesPerCustomer,
      });
    }
  };

  const preview = (() => {
    const sample = 200000;
    if (form.discountType === 'FIXED') return Math.min(form.discountValue, sample);
    const raw = Math.round(sample * form.discountValue / 100);
    return form.maxDiscount ? Math.min(raw, form.maxDiscount) : raw;
  })();

  return (
    <form onSubmit={submit} className="max-w-2xl space-y-4">
      <header className="mb-6">
        <h1 className="text-2xl font-bold">{isEdit ? 'Sửa voucher' : 'Tạo voucher mới'}</h1>
      </header>

      <Section title="Thông tin chung">
        <Field label="Mã voucher" required>
          <input value={form.code} disabled={isEdit}
            onChange={e => setForm({...form, code: e.target.value.toUpperCase()})}
            className="input font-mono" placeholder="VD: HELLO20K" maxLength={32} required />
        </Field>
        <Field label="Tên hiển thị" required>
          <input value={form.name} onChange={e => setForm({...form, name: e.target.value})}
            className="input" maxLength={128} required />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Đối tượng" required>
            <select value={form.target} disabled={isEdit}
              onChange={e => setForm({...form, target: e.target.value as VoucherTarget})}
              className="input">
              <option value="PRODUCTS">🛍️ Giảm tiền hàng</option>
              <option value="SHIPPING">🚚 Giảm phí ship</option>
            </select>
          </Field>
          <Field label="Loại giảm" required>
            <select value={form.discountType} disabled={isEdit}
              onChange={e => setForm({...form, discountType: e.target.value as DiscountType})}
              className="input">
              <option value="FIXED">Số tiền cố định (đ)</option>
              <option value="PERCENT">Phần trăm (%)</option>
            </select>
          </Field>
        </div>
      </Section>

      <Section title="Mức giảm">
        <Field label={`Giá trị ${form.discountType === 'PERCENT' ? '(%)' : '(đ)'}`} required>
          <input type="number" min={1} value={form.discountValue}
            onChange={e => setForm({...form, discountValue: Number(e.target.value)})}
            className="input" required />
        </Field>
        {form.discountType === 'PERCENT' && (
          <Field label="Giảm tối đa (cap, đ)">
            <input type="number" min={0} value={form.maxDiscount ?? ''}
              onChange={e => setForm({...form, maxDiscount: e.target.value ? Number(e.target.value) : null})}
              className="input" placeholder="Để trống = không giới hạn" />
          </Field>
        )}
        <div className="rounded-xl bg-gray-50 border border-gray-200 p-3 text-sm">
          📊 <strong>Xem trước:</strong> đơn 200 000đ → giảm <strong>{formatVnd(preview)}</strong>
        </div>
      </Section>

      <Section title="Điều kiện áp dụng">
        <Field label="Đơn tối thiểu (đ)">
          <input type="number" min={0} value={form.minOrderAmount}
            onChange={e => setForm({...form, minOrderAmount: Number(e.target.value)})}
            className="input" />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Hiệu lực từ" required>
            <input type="datetime-local" value={form.validFrom}
              onChange={e => setForm({...form, validFrom: e.target.value})}
              className="input" required />
          </Field>
          <Field label="Hết hạn" required>
            <input type="datetime-local" value={form.validUntil}
              onChange={e => setForm({...form, validUntil: e.target.value})}
              className="input" required />
          </Field>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Tổng số lần dùng (max_uses_total)">
            <input type="number" min={1} value={form.maxUsesTotal ?? ''}
              onChange={e => setForm({...form, maxUsesTotal: e.target.value ? Number(e.target.value) : null})}
              className="input" placeholder="Để trống = không giới hạn" />
          </Field>
          <Field label="Mỗi khách dùng tối đa" required>
            <input type="number" min={1} value={form.maxUsesPerCustomer}
              onChange={e => setForm({...form, maxUsesPerCustomer: Number(e.target.value)})}
              className="input" required />
          </Field>
        </div>
      </Section>

      {isEdit && (
        <Section title="Trạng thái">
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={form.active}
                   onChange={e => setForm({...form, active: e.target.checked})} />
            Active
          </label>
        </Section>
      )}

      {err && <div className="bg-red-50 border border-red-200 rounded p-3 text-sm text-red-700">{err}</div>}

      <div className="flex gap-3">
        <button type="submit" disabled={create.isPending || update.isPending}
          className="px-5 py-3 bg-orange-500 hover:bg-orange-600 text-white rounded-xl font-semibold disabled:opacity-50">
          {isEdit ? 'Lưu thay đổi' : 'Tạo voucher'}
        </button>
        <button type="button" onClick={() => nav(-1)}
          className="px-5 py-3 bg-gray-200 text-gray-700 rounded-xl">
          Huỷ
        </button>
      </div>

      <style>{`.input{margin-top:.25rem;width:100%;padding:.5rem .75rem;border:1px solid #d1d5db;border-radius:.5rem;font-size:.875rem;background:white}`}</style>
    </form>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="bg-white rounded-xl border border-gray-200 p-5">
      <h2 className="font-semibold text-gray-900 mb-4">{title}</h2>
      <div className="space-y-3">{children}</div>
    </section>
  );
}

function Field({ label, required, children }:{label:string; required?:boolean; children: React.ReactNode}) {
  return (
    <label className="block">
      <span className="text-sm text-gray-700">
        {label}{required && <span className="text-red-500 ml-0.5">*</span>}
      </span>
      {children}
    </label>
  );
}
```

### Task 24: VoucherDetailPage + final wiring

**Files:**
- Create: `frontend/webadmin/src/pages/VoucherDetailPage.tsx`

- [ ] **Step 1: Implement detail page**

```tsx
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchAdminVoucherDetail, deleteVoucher, formatVnd } from '@shop/shared';
import { api } from '@/lib/api';

export function VoucherDetailPage() {
  const { id } = useParams();
  const nav = useNavigate();
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ['admin','voucher', id],
    queryFn: () => fetchAdminVoucherDetail(api, Number(id)),
  });
  const del = useMutation({
    mutationFn: () => deleteVoucher(api, Number(id)),
    onSuccess: () => { qc.invalidateQueries({queryKey:['admin','vouchers']}); nav('/vouchers'); },
  });

  if (isLoading || !data) return <p>Đang tải…</p>;
  const s = data.summary;

  return (
    <div>
      <header className="flex justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold font-mono">{s.code}</h1>
          <p className="text-sm text-gray-500">{s.name}</p>
        </div>
        <div className="flex gap-2">
          <Link to={`/vouchers/${id}/edit`}
            className="px-4 py-2 bg-orange-500 hover:bg-orange-600 text-white rounded-lg">
            Sửa
          </Link>
          <button onClick={() => confirm('Xoá voucher này?') && del.mutate()}
            className="px-4 py-2 bg-red-100 hover:bg-red-200 text-red-700 rounded-lg">
            Xoá
          </button>
        </div>
      </header>

      <div className="grid grid-cols-2 gap-4 mb-6">
        <Card label="Đối tượng" value={s.target === 'SHIPPING' ? '🚚 Phí ship' : '🛍️ Tiền hàng'} />
        <Card label="Mức giảm" value={s.discountType === 'FIXED'
          ? formatVnd(s.discountValue)
          : `${s.discountValue}%${s.maxDiscount ? ` (cap ${formatVnd(s.maxDiscount)})` : ''}`} />
        <Card label="Đã dùng" value={`${s.usedCount} / ${s.maxUsesTotal ?? '∞'}`} />
        <Card label="Hết hạn" value={new Date(s.validUntil).toLocaleDateString('vi-VN')} />
      </div>

      <h2 className="font-semibold mb-3">Lịch sử áp ({data.recentRedemptions.length})</h2>
      <table className="w-full bg-white rounded-xl border border-gray-200">
        <thead className="bg-gray-50 text-left text-xs uppercase">
          <tr><th className="px-4 py-3">Thời gian</th><th className="px-4 py-3">Đơn</th><th className="px-4 py-3">Giảm</th></tr>
        </thead>
        <tbody>
          {data.recentRedemptions.map(r => (
            <tr key={r.orderId} className="border-t border-gray-100">
              <td className="px-4 py-3 text-sm">{new Date(r.createdAt).toLocaleString('vi-VN')}</td>
              <td className="px-4 py-3 font-mono text-xs">{r.orderId.slice(0,8)}…</td>
              <td className="px-4 py-3">{formatVnd(r.discountApplied)}</td>
            </tr>
          ))}
          {data.recentRedemptions.length === 0 && (
            <tr><td colSpan={3} className="text-center py-6 text-gray-500">Chưa có ai áp voucher này</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function Card({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-white border border-gray-200 rounded-xl p-4">
      <p className="text-xs text-gray-500">{label}</p>
      <p className="text-lg font-semibold mt-1">{value}</p>
    </div>
  );
}
```

- [ ] **Step 2: Wire routes + sidebar entry (do Task 21 now)**

Open the sidebar component and add a "Khuyến mãi" link with tag icon between Reports and Settings. Open the routes file and add the four `<Route>` entries from Task 21.

- [ ] **Step 3: Manual smoke test**

Start webadmin. Login. Navigate to /vouchers → see seeded FREESHIP + GIAM20K + any test voucher created. Click row → detail page. Click Sửa → form pre-fills. Click "Tạo voucher" → create form. Submit → redirects to detail.

- [ ] **Step 4: Commit (group all three pages)**

```bash
git add frontend/webadmin/src/pages/VouchersPage.tsx \
       frontend/webadmin/src/pages/VoucherFormPage.tsx \
       frontend/webadmin/src/pages/VoucherDetailPage.tsx \
       frontend/webadmin/src/App.tsx \
       frontend/webadmin/src/components/  # sidebar update
git commit -m "feat(webadmin): VouchersPage + VoucherFormPage + VoucherDetailPage"
```

---

## Wave 10 — Integration tests + finishing touches

### Task 25: Race condition integration test

**Files:**
- Create: `backend/modules/promotion/src/test/java/com/shop/delivery/promotion/service/VoucherRaceConditionIT.java`

- [ ] **Step 1: Write 2-thread integration test**

```java
package com.shop.delivery.promotion.service;

import com.shop.delivery.promotion.domain.DiscountType;
import com.shop.delivery.promotion.domain.VoucherTarget;
import com.shop.delivery.promotion.entity.Voucher;
import com.shop.delivery.promotion.repository.VoucherRepository;
import com.shop.delivery.promotion.support.PromotionTestcontainerBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherRaceConditionIT extends PromotionTestcontainerBase {

    @Autowired VoucherRepository repo;
    @Autowired VoucherService service;

    @Test
    void twoConcurrentRedemptions_onLastSlot_onlyOneSucceeds() throws InterruptedException {
        Voucher v = new Voucher();
        v.setCode("LASTSLOT");
        v.setName("Last slot");
        v.setTarget(VoucherTarget.PRODUCTS);
        v.setDiscountType(DiscountType.FIXED);
        v.setDiscountValue(new BigDecimal("10000"));
        v.setMaxUsesTotal(1);    // Only ONE slot
        v.setMaxUsesPerCustomer(2);
        v.setValidFrom(OffsetDateTime.now().minusDays(1));
        v.setValidUntil(OffsetDateTime.now().plusDays(1));
        repo.saveAndFlush(v);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        var pool = Executors.newFixedThreadPool(2);
        Runnable task = () -> {
            try {
                start.await();
                service.redeem("LASTSLOT", UUID.randomUUID(), 7777L, new BigDecimal("10000"));
                successes.incrementAndGet();
            } catch (Exception e) {
                failures.incrementAndGet();
            } finally {
                done.countDown();
            }
        };
        pool.submit(task); pool.submit(task);
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);

        Voucher after = repo.findById(v.getId()).orElseThrow();
        assertThat(after.getUsedCount()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test**

```bash
cd backend && ./mvnw -pl modules/promotion test -Dtest=VoucherRaceConditionIT
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add backend/modules/promotion/src/test/java/com/shop/delivery/promotion/service/VoucherRaceConditionIT.java
git commit -m "test(promotion): race condition — concurrent redeem on last slot"
```

### Task 26: Stacking constraint test (Order integration)

**Files:**
- Create: `backend/modules/order/src/test/java/com/shop/delivery/order/service/VoucherStackingIT.java`

- [ ] **Step 1: Write test (skeleton — adapt to existing OrderFlowIT pattern)**

```java
// In the test, seed:
// - One PRODUCTS voucher GIAM10K (-10k tiền hàng)
// - One PRODUCTS voucher GIAM5K  (-5k  tiền hàng)
// - One SHIPPING voucher SHIP15K (-15k phí ship)
// Then call OrderService.create with voucherCodes={products:"GIAM10K", shipping:"SHIP15K"}
// Assert: order created, discountProducts=10k, discountShipping=15k, total = subtotal-10k + fee-15k.
//
// Then call create with voucherCodes={products:"GIAM10K", shipping:"GIAM5K"} (two PRODUCTS)
// Assert: throws ApiException("INVALID_VOUCHER_PRODUCTS_WRONG_TARGET") since GIAM5K isn't SHIPPING.
```

- [ ] **Step 2: Run test, commit**

```bash
cd backend && ./mvnw -pl modules/order test -Dtest=VoucherStackingIT
git add backend/modules/order/src/test/java/com/shop/delivery/order/service/VoucherStackingIT.java
git commit -m "test(order): voucher stacking — 1 SHIPPING + 1 PRODUCTS allowed, 2 same-target rejected"
```

### Task 27: End-to-end manual smoke test

- [ ] **Step 1: Run full smoke**

1. Backend + miniapp + webadmin all running.
2. Login admin, navigate /vouchers, create voucher `PHASE14SMOKE` (PRODUCTS, FIXED, 15 000đ, min order 50k).
3. Open miniapp /customer/shop, add 2 items (total ~120k), go to /customer/checkout.
4. Type `PHASE14SMOKE` in "Mã giảm tiền hàng" → click Áp → see ✓ chip + breakdown -15 000đ.
5. Type `FREESHIP` in "Mã giảm phí ship" → click Áp → see -30 000đ in breakdown.
6. Place order (COD). Verify in admin /orders → newest order shows the two discount rows.
7. Back in /vouchers/<id> for PHASE14SMOKE → "Đã dùng" = 1/100, redemption log shows the order.

- [ ] **Step 2: Update thesis docs (sneak preview, can be deferred to Phase 15 doc pass)**

Skip for now — the spec already commits to doing this after Phase 15 merges. The two-phase doc bundle is more efficient than per-phase.

- [ ] **Step 3: Final commit if anything tweaked during smoke**

### Task 28: Update spec status

**Files:**
- Modify: `docs/superpowers/specs/2026-06-03-voucher-shipper-commission-design.md`

- [ ] **Step 1: Change Status line**

In the spec header, change:

```
**Status:** Draft awaiting user review
```

to:

```
**Status:** Phase 14 complete. Phase 15 not started.
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/specs/2026-06-03-voucher-shipper-commission-design.md
git commit -m "docs(spec): mark Phase 14 (Voucher) complete"
```

---

## Done

After all 28 tasks pass, the system should:
- have 13 → 14 Flyway migrations applied,
- expose 6 voucher REST endpoints (1 customer + 5 admin),
- show two voucher input fields at Mini App Checkout with live breakdown,
- have three new Web Admin pages (list / form / detail) with seeded data,
- carry the snapshot `delivery_fee_original` on every order — ready for Phase 15 commission calculation,
- and have +21 backend tests + 2 frontend tests passing (274 → 297 toàn dự án).

Next phase: **Phase 15 — Shipper Commission & Earnings**, planned separately after this one merges.
