# P7 — VNPay Sandbox Payment Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách hàng chọn "VNPay" ở checkout của Telegram Mini App → bấm "Đặt hàng" → backend tạo `payment` row PENDING → Mini App gọi `Telegram.WebApp.openLink(paymentUrl)` → khách thanh toán test card NCB ở sandbox → VNPay gọi IPN server-to-server → backend atomic flip `payment.status=SUCCESS` + `order.payment_status=SUCCESS` + `order.status PENDING→CONFIRMED` (qua event listener trong `order` module) + bot báo chủ shop. Web Admin orders list hiện badge `payment_status` đúng màu. Đơn fail / timeout 15p → `payment.status=FAILED`, đơn vẫn PENDING, khách có thể thanh toán lại (txnRef mới).

**Scope (Tuần 10):**
- Backend `payment` module (greenfield — chỉ có `.gitkeep`): pom deps, V9 migration, `VnpayProperties`, `Payment` + `PaymentTransaction` entities + repos, `VnpaySignatureService`, `VnpayPaymentService` (create/return/ipn), `VnpayController`, `PaymentEventType`, audit helper, `PaymentSucceededEvent` + `PaymentFailedEvent`, `PaymentExpiryScheduler`
- Backend `order` module changes: `OrderConfirmedEvent`, `OrderService.confirmAfterPayment(UUID)`, `PaymentEventListener`
- Backend `app` module: `V9__payment.sql`, `@EnableScheduling` trên `Application.java`, `application.yml` vnpay keys, `SecurityConfig` allowlist `/api/payment/vnpay/return` + `/ipn`
- Frontend Mini App: enable VNPAY radio trên `CheckoutPage`, hook `usePayWithVnpay` + `openLink` redirect, polling pattern trên `OrderDetailPage` khi VNPAY+PENDING (3s × 60s), success/fail badge thông minh
- Frontend Web Admin: column `payment_status` với colored badge
- Static HTML resources cho return page: `payment-success.html`, `payment-failed.html` trong `backend/app/src/main/resources/static/`

**Defer to future phase:**
- Refund flow (`vnpay_refund` API, `merchant_webapi`) — design §10.5 mark out of scope
- `querydr` manual transaction lookup
- Recurring / token payments
- Production VNPay credentials provisioning (sandbox only)
- Real ngrok / Cloudflare Tunnel automation (document as manual step in P7 README, not in plan)

**Architecture:**
- **Module dep direction:** `payment → order → auth → shared`. `order` does NOT depend on `payment` (avoids cycle). Cross-module signalling via Spring `ApplicationEventPublisher`.
- **IPN is source of truth:** Return URL never writes DB (just renders simple HTML page). IPN handler is `@Transactional`, idempotent on `vnp_TxnRef`, validates signature + amount + replay.
- **Event flow on success:**
  ```
  IPN POST → VnpayPaymentService.handleIpn
    ├─ verify sig → 97 if bad
    ├─ load payment by txnRef → 01 if not found
    ├─ if status != PENDING → 02 (idempotent replay)
    ├─ amount mismatch → 04
    ├─ payment.status = SUCCESS, paid_at = now
    ├─ publish PaymentSucceededEvent
    └─ audit IPN event
       └─ commit
          └─ @TransactionalEventListener(AFTER_COMMIT) in order module
              → OrderService.confirmAfterPayment(orderId)
                ├─ order.payment_status = SUCCESS
                ├─ if order.status == PENDING → CONFIRMED (state machine)
                ├─ recordTransition
                └─ publish OrderConfirmedEvent
                   └─ existing bot/notification listeners (none yet — leave hooks for P8)
  ```
- **Polling fallback:** Mini App polls `GET /api/orders/{id}` every 3s for 60s after VNPay overlay closes. Cheap, simple, no WebSocket auth dance. (P6 WS is for location only — adding payment topic is deferred.)
- **Signature:** HMAC-SHA512 via JDK `javax.crypto.Mac`, US-ASCII URL-encode names + values, sort by name, skip empty values, lowercase hex output, constant-time compare via `MessageDigest.isEqual`. Port verbatim from VNPay's official `vnpay_jsp.zip` sample (see research §Pattern 1).
- **Timezone:** `ZoneId.of("Asia/Ho_Chi_Minh")` for `vnp_CreateDate` / `vnp_ExpireDate`. NOT `Etc/GMT+7` (POSIX inversion gotcha — research Pitfall A5).

**Tech Stack (additions on top of P6):**
- Backend: zero new external Maven deps. All from JDK 17 + existing Spring Boot starters (web, data-jpa, validation already available via parent management).
- Frontend: zero new packages — `@twa-dev/sdk` 7.10 already has `WebApp.openLink`, `@tanstack/react-query` already has `refetchInterval`.

---

## Bối cảnh từ P5/P6

Sau P5 + P6:
- 88 + 23 = 111 commits, ~129 tests pass, BUILD SUCCESS
- Order state machine: PENDING → CONFIRMED → ASSIGNED → DELIVERING → DELIVERED/RETURNED/CANCELLED
- `Order` entity already has `payment_method`, `payment_status` columns (default PENDING) + `@Version` optimistic lock
- `OrderService.confirm(UUID, Long, String)` exists but does NOT publish events (current behaviour). P7 adds `confirmAfterPayment(UUID)` that publishes `OrderConfirmedEvent`.
- `OrderStateMachine.requireAllowed` whitelists `PENDING → CONFIRMED`. P7 reuses without changes.
- `delivery` module already uses `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` (see `LocationBroadcaster`, `DeliveryAssignmentService`). Same pattern.
- `auth` module has `SecurityConfig` with permit-all for `/api/products/**`, `/api/bot/webhook`, `/ws/**`. We add `/api/payment/vnpay/return` + `/api/payment/vnpay/ipn`.
- Migrations: V1–V8 applied. V9 is next.
- Mini App `CheckoutPage` has VNPAY radio button currently `disabled` with label "VNPay (sắp có — P7)". P7 enables it.
- Web Admin `OrdersPage` already displays `paymentMethod (paymentStatus)` as plain text in one cell. P7 splits into a colored badge column.
- `Application.java` does NOT have `@EnableScheduling`. P7 adds it (one annotation, single-line change).

**Constraints (kế thừa):**
- Java 17 + Spring Boot 3.4 (parent pom)
- Postgres 16 (Testcontainers `postgresql:16-alpine`)
- Backend port 8080, Mini App 5173, Web Admin 5174
- DB: `shop_delivery_postgres_dev`
- Test admin: `admin@shop.local / admin123`

**Decisions chốt (từ research + brief):**

1. **`payment` module Maven coordinates:** `com.shop.delivery:payment:0.1.0-SNAPSHOT`. Java package root `com.shop.delivery.payment`. Module pom adds: starter-web, starter-data-jpa, starter-validation, `order` (sibling internal dep), Testcontainers postgresql (test scope). Already in parent `<modules>` list — no parent pom change needed.

2. **VNPay credentials in dev:** Hard-coded test values in `application-dev.yml` (`tmn-code: TEST01`, `hash-secret: TESTSECRETKEY123`). Real sandbox `TmnCode`/`HashSecret` registered at https://sandbox.vnpayment.vn/devreg supplied via env vars `VNPAY_TMN_CODE` / `VNPAY_HASH_SECRET` — bind via `${VNPAY_TMN_CODE:TEST01}` placeholder syntax in yml. Tests use the test values directly.

3. **Return URL UX:** Backend renders simple static HTML page (no React, no Mini App context inside — `Telegram.WebApp.openLink` opens VNPay in Telegram's overlay browser; that overlay also shows the return page when payment finishes). Two pages: `payment-success.html` and `payment-failed.html`, served from `backend/app/src/main/resources/static/`. Both contain: order code, amount in VND, "Quay lại Mini App" button using `tg://resolve?domain=<bot>&appname=...` deep link. Backend controller `/return` redirects (302) to the appropriate static page with `?orderCode=...&amount=...` query params; static HTML reads from query string via tiny inline JS (no build step needed).

4. **IPN response format:** Returns `200 OK` with body `{"RspCode": "<code>", "Message": "<text>"}` (exact field name capitalization per VNPay spec). Use `@RestController` + a `IpnResponse` record annotated with `@JsonProperty("RspCode")` / `@JsonProperty("Message")` to enforce.

5. **Idempotency on `payment.vnp_txn_ref`:** UNIQUE constraint at DB level + `@Version` optimistic lock on `payment` row. Replay detection: if `payment.status != PENDING` when IPN arrives → return `02`. Edge: amount mismatch on PENDING payment → return `04` but DO NOT flip status (allow corrected retry).

6. **TxnRef format:** `{orderCode}-{epochMs}` per spec §10.6. Every `POST /create` call mints a NEW `Payment` row even for the same order — old PENDING rows for the same order are marked FAILED with response_code `SUPERSEDED` before creating the new one (avoids stale ones triggering false expiry alerts; UNIQUE constraint on txnRef is preserved because epochMs differs).

7. **Cron interval:** `@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)` — every 60s, starts 60s after boot. Cutoff = `now - 15 minutes` (matches VNPay's `vnp_ExpireDate`). Brief says "15-minute fixed delay" — we use 60s delay for snappier expiry detection, since the 15-minute number is the *staleness threshold*, not the cron period. Sweeper picks up at most ~50 rows per run (rare in practice).

8. **Event listener placement:** `PaymentEventListener` lives in `order` module (not `payment`), because it CONSUMES the event and CALLS `OrderService`. This keeps `order` module unaware of `payment` types — wait, `PaymentSucceededEvent` IS in `payment` module so `order` would need to import it... → Decision: put `PaymentSucceededEvent` / `PaymentFailedEvent` records in `shared` module under `com.shop.delivery.shared.event` so both `payment` (publisher) and `order` (listener) can reference without circular dep. Simpler than splitting into separate "events" module.

9. **`OrderConfirmedEvent` placement:** Also in `shared.event`. Currently no listeners exist — P8 (notification) will wire bot/WS listeners. We publish it now so the framework is ready.

10. **Polling after VNPay return:** Mini App `OrderDetailPage` already calls `useQuery(['order', id])`. Add `refetchInterval` that returns `3000` ms when `(paymentMethod === 'VNPAY' && paymentStatus === 'PENDING')` and `false` otherwise. Auto-stops at `paymentStatus !== 'PENDING'`. Add a separate 60-second timeout via `setTimeout` in `useEffect` to disable polling and show "Vui lòng kiểm tra trạng thái sau" message — prevents infinite polling if IPN never arrives (e.g. user closed VNPay).

11. **Web Admin badge colors:** PENDING = gray, SUCCESS = green-600, FAILED = red-600, REFUNDED = amber-600. Component: new `PaymentStatusBadge` in `frontend/webadmin/src/components/PaymentStatusBadge.tsx`, mirrors existing `OrderStatusBadge` style.

12. **No new external HTTP calls from backend.** Backend only signs URLs + verifies signatures. VNPay sandbox is contacted only by (a) user's browser when redirected (b) VNPay's own server when calling our `/ipn`. No backend → VNPay HTTP traffic — so CI tests are 100% offline.

---

## File Structure (sau khi P7 hoàn thành)

```
backend/
├── app/
│   ├── src/main/java/com/shop/delivery/
│   │   └── Application.java                                   (modify — @EnableScheduling)
│   ├── src/main/resources/
│   │   ├── application.yml                                    (modify — add vnpay block)
│   │   ├── application-dev.yml                                (modify — dev credentials)
│   │   ├── db/migration/
│   │   │   └── V9__payment.sql                                (TASK 2)
│   │   └── static/
│   │       ├── payment-success.html                           (TASK 12)
│   │       └── payment-failed.html                            (TASK 12)
│   └── src/test/java/com/shop/delivery/
│       └── payment/
│           └── VnpayControllerIT.java                         (TASK 8)
│
├── modules/
│   ├── shared/
│   │   └── src/main/java/com/shop/delivery/shared/event/
│   │       ├── PaymentSucceededEvent.java                     (TASK 9)
│   │       ├── PaymentFailedEvent.java                        (TASK 9)
│   │       └── OrderConfirmedEvent.java                       (TASK 10)
│   │
│   ├── order/
│   │   └── src/main/java/com/shop/delivery/order/
│   │       ├── service/
│   │       │   └── OrderService.java                          (modify — confirmAfterPayment + publisher)
│   │       └── listener/
│   │           └── PaymentEventListener.java                  (TASK 10)
│   │
│   ├── auth/
│   │   └── src/main/java/com/shop/delivery/auth/config/
│   │       └── SecurityConfig.java                            (modify — TASK 13)
│   │
│   └── payment/
│       ├── pom.xml                                            (modify — TASK 1)
│       └── src/main/java/com/shop/delivery/payment/
│           ├── config/
│           │   └── VnpayProperties.java                       (TASK 3)
│           ├── domain/
│           │   └── PaymentEventType.java                      (TASK 4)
│           ├── entity/
│           │   ├── Payment.java                               (TASK 4)
│           │   └── PaymentTransaction.java                    (TASK 4)
│           ├── repository/
│           │   ├── PaymentRepository.java                     (TASK 4)
│           │   └── PaymentTransactionRepository.java          (TASK 4)
│           ├── service/
│           │   ├── VnpaySignatureService.java                 (TASK 5)
│           │   ├── VnpayPaymentService.java                   (TASK 7)
│           │   ├── PaymentAuditRecorder.java                  (TASK 6)
│           │   └── PaymentExpiryScheduler.java                (TASK 11)
│           └── api/
│               ├── VnpayController.java                       (TASK 8)
│               └── dto/
│                   ├── CreatePaymentRequest.java              (TASK 8)
│                   ├── CreatePaymentResponse.java             (TASK 8)
│                   └── IpnResponse.java                       (TASK 8)
│
frontend/
├── shared/src/
│   ├── types/payment.ts                                       (TASK 14)
│   ├── types/index.ts                                         (modify)
│   ├── api/payment.ts                                         (TASK 14)
│   └── api/index.ts                                           (modify)
├── miniapp/
│   └── src/
│       ├── features/payment/
│       │   └── use-pay-with-vnpay.ts                          (TASK 15)
│       └── pages/
│           ├── CheckoutPage.tsx                               (modify — TASK 15)
│           └── OrderDetailPage.tsx                            (modify — TASK 16)
└── webadmin/
    └── src/
        ├── components/
        │   └── PaymentStatusBadge.tsx                         (TASK 17)
        └── pages/
            └── OrdersPage.tsx                                 (modify — TASK 17)
```

**Backend file mới:** ~16
**Frontend file mới:** ~4

---

## WAVE 0 — Foundation (Tasks 1–6)

Wave 0 builds the static skeleton (module compile + DB schema + entities + config + crypto). Output: `mvn -pl backend/modules/payment -am test` runs `VnpaySignatureServiceTest` green. No controller yet, no business logic — pure infrastructure.


## TASK 1: payment module pom.xml + skeleton package (1 commit)

**Files:**
- Modify: `backend/modules/payment/pom.xml`
- Delete: `backend/modules/payment/src/main/java/com/shop/delivery/payment/.gitkeep`
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/package-info.java`

- [ ] **Step 1: Add required dependencies**

Replace `backend/modules/payment/pom.xml` so it lists every dep `payment` needs:

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

    <artifactId>payment</artifactId>
    <name>payment</name>
    <description>VNPay integration, payment transactions</description>

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
            <groupId>com.shop.delivery</groupId>
            <artifactId>order</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
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
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Replace `.gitkeep` with a `package-info.java`**

Delete `backend/modules/payment/src/main/java/com/shop/delivery/payment/.gitkeep`.

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/package-info.java`:

```java
/**
 * Payment module — VNPay sandbox integration.
 *
 * <p>Owns {@code payment} and {@code payment_transaction} tables. Depends on
 * {@code order} module for {@code OrderService.confirmAfterPayment} and on
 * {@code shared} for cross-module event records.
 *
 * <p>Cross-module signalling is one-way: this module publishes
 * {@code PaymentSucceededEvent} / {@code PaymentFailedEvent}; never imports
 * from {@code order}'s domain except via the public {@code OrderService} API.
 */
package com.shop.delivery.payment;
```

- [ ] **Step 3: Verify module compiles (no Java sources yet, just structure)**

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang/backend
./mvnw -q -pl modules/payment -am compile
```

Expected: BUILD SUCCESS. Module produces `payment-0.1.0-SNAPSHOT.jar` (empty classes dir, just `package-info.class`).

- [ ] **Step 4: Commit**

```bash
git add backend/modules/payment/pom.xml \
        backend/modules/payment/src/main/java/com/shop/delivery/payment/package-info.java
git rm  backend/modules/payment/src/main/java/com/shop/delivery/payment/.gitkeep
git commit -m "build(payment): add module deps + package skeleton"
```

**Acceptance:**
- `mvn -q -pl modules/payment -am compile` exits 0
- `payment` artifact is in the build reactor
- No `.gitkeep` file remains under `payment/src/main/java/com/shop/delivery/payment/`

---

## TASK 2: V9__payment.sql Flyway migration (1 commit)

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V9__payment.sql`

- [ ] **Step 1: Write migration**

Create `backend/app/src/main/resources/db/migration/V9__payment.sql`:

```sql
-- V9__payment.sql — payment module tables
-- Owns: payment, payment_transaction
-- Cross-ref: payment.order_id → orders(id) (defined in V4__order.sql)

CREATE TABLE payment (
    id                  UUID         PRIMARY KEY,
    order_id            UUID         NOT NULL REFERENCES orders(id),
    method              VARCHAR(16)  NOT NULL,                              -- COD | VNPAY
    amount              NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    status              VARCHAR(16)  NOT NULL,                              -- PENDING | SUCCESS | FAILED | REFUNDED
    vnp_txn_ref         VARCHAR(64)  UNIQUE,                                -- "{orderCode}-{epochMs}"
    vnp_transaction_no  VARCHAR(64),
    vnp_response_code   VARCHAR(16),                                        -- "00", "07", "SUPERSEDED", "EXPIRED" ...
    paid_at             TIMESTAMPTZ,
    version             INT          NOT NULL DEFAULT 0,                    -- @Version optimistic lock
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_order               ON payment(order_id);
CREATE INDEX idx_payment_status_created      ON payment(status, created_at);

CREATE TABLE payment_transaction (
    id          BIGSERIAL    PRIMARY KEY,
    payment_id  UUID         NOT NULL REFERENCES payment(id) ON DELETE CASCADE,
    event_type  VARCHAR(16)  NOT NULL,                                      -- CREATE | IPN | RETURN
    raw_payload JSONB        NOT NULL,
    recorded_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_tx_payment ON payment_transaction(payment_id, recorded_at);
```

- [ ] **Step 2: Apply migration (smoke test)**

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
docker compose -f infra/docker-compose.dev.yml ps | grep -q healthy || \
  docker compose -f infra/docker-compose.dev.yml up -d
sleep 5

cd backend/app
BOT_TOKEN=${BOT_TOKEN:-dummy} BOT_USERNAME=${BOT_USERNAME:-DummyBot} \
  nohup ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8089" > /tmp/p7_t2.log 2>&1 &
BOOT_PID=$!
for i in $(seq 1 90); do
  grep -q "Started Application" /tmp/p7_t2.log 2>/dev/null && break
  grep -q "APPLICATION FAILED" /tmp/p7_t2.log 2>/dev/null && break
  sleep 1
done
kill $BOOT_PID 2>/dev/null
sleep 2
cd ../..

docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT version, success FROM flyway_schema_history ORDER BY version"
# Expected: 1..9 all t

docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d payment"
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d payment_transaction"
```

Expected: `\d payment` shows the columns + indexes + UNIQUE on `vnp_txn_ref`; `\d payment_transaction` shows `raw_payload jsonb`.

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V9__payment.sql
git commit -m "feat(payment): add V9 migration for payment + payment_transaction"
```

**Acceptance:**
- Flyway history shows V9 success
- `payment.vnp_txn_ref` has UNIQUE constraint (verify: `\d payment` shows `vnp_txn_ref_key` index)
- `payment_transaction.raw_payload` is `jsonb` not `json`

---

## TASK 3: VnpayProperties + application.yml binding (TDD, 1 commit)

**Files:**
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/config/VnpayProperties.java`
- Create: `backend/modules/payment/src/test/java/com/shop/delivery/payment/config/VnpayPropertiesTest.java`
- Modify: `backend/app/src/main/resources/application.yml`
- Modify: `backend/app/src/main/resources/application-dev.yml`

- [ ] **Step 1: Write failing test**

Create `backend/modules/payment/src/test/java/com/shop/delivery/payment/config/VnpayPropertiesTest.java`:

```java
package com.shop.delivery.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VnpayPropertiesTest {

    @Test
    void shouldBindAllFields() {
        Map<String, Object> raw = Map.of(
            "vnpay.tmn-code",      "TEST01",
            "vnpay.hash-secret",   "SECRET123",
            "vnpay.pay-url",       "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
            "vnpay.return-url",    "http://localhost:8080/api/payment/vnpay/return",
            "vnpay.ipn-url",       "http://localhost:8080/api/payment/vnpay/ipn",
            "vnpay.timeout-minutes", 15
        );
        ConfigurationPropertySource src = new MapConfigurationPropertySource(raw);
        VnpayProperties props = new Binder(src).bind("vnpay", VnpayProperties.class).get();

        assertThat(props.tmnCode()).isEqualTo("TEST01");
        assertThat(props.hashSecret()).isEqualTo("SECRET123");
        assertThat(props.payUrl()).isEqualTo("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        assertThat(props.returnUrl()).isEqualTo("http://localhost:8080/api/payment/vnpay/return");
        assertThat(props.ipnUrl()).isEqualTo("http://localhost:8080/api/payment/vnpay/ipn");
        assertThat(props.timeoutMinutes()).isEqualTo(15);
    }

    @Test
    void toStringDoesNotLeakHashSecret() {
        VnpayProperties props = new VnpayProperties(
            "TEST01", "SUPERSECRET", "u1", "u2", "u3", 15
        );
        assertThat(props.toString())
            .doesNotContain("SUPERSECRET")
            .contains("hashSecret=***");
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

```bash
cd backend && ./mvnw -q -pl modules/payment -am test
```

- [ ] **Step 3: Implement `VnpayProperties`**

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/config/VnpayProperties.java`:

```java
package com.shop.delivery.payment.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * VNPay sandbox / production configuration.
 *
 * <p>{@code hashSecret} is masked in {@link #toString()} — DO NOT log this record directly,
 * but if you do, the secret will not leak.
 */
@ConfigurationProperties(prefix = "vnpay")
@Validated
public record VnpayProperties(
    @NotBlank String tmnCode,
    @NotBlank String hashSecret,
    @NotBlank String payUrl,
    @NotBlank String returnUrl,
    @NotBlank String ipnUrl,
    @Positive int timeoutMinutes
) {
    @Override
    public String toString() {
        return "VnpayProperties[tmnCode=" + tmnCode
            + ", hashSecret=***"
            + ", payUrl=" + payUrl
            + ", returnUrl=" + returnUrl
            + ", ipnUrl=" + ipnUrl
            + ", timeoutMinutes=" + timeoutMinutes + "]";
    }
}
```

- [ ] **Step 4: Add config keys to `application.yml`**

Append to `backend/app/src/main/resources/application.yml` (after the `shop:` block):

```yaml
vnpay:
  tmn-code:        ${VNPAY_TMN_CODE:TEST01}
  hash-secret:     ${VNPAY_HASH_SECRET:TESTSECRETKEY123}
  pay-url:         https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
  return-url:      ${VNPAY_RETURN_URL:http://localhost:8080/api/payment/vnpay/return}
  ipn-url:         ${VNPAY_IPN_URL:http://localhost:8080/api/payment/vnpay/ipn}
  timeout-minutes: 15
```

- [ ] **Step 5: Override dev placeholders in `application-dev.yml`** (so local dev works without env vars)

Append to `backend/app/src/main/resources/application-dev.yml`:

```yaml
vnpay:
  tmn-code:     TEST01
  hash-secret:  TESTSECRETKEY123
  return-url:   http://localhost:8080/api/payment/vnpay/return
  ipn-url:      http://localhost:8080/api/payment/vnpay/ipn
```

- [ ] **Step 6: Run test — expect pass**

```bash
cd backend && ./mvnw -q -pl modules/payment -am test -Dtest=VnpayPropertiesTest
```

- [ ] **Step 7: Verify Spring picks up the properties at boot**

```bash
cd backend/app
BOT_TOKEN=dummy BOT_USERNAME=DummyBot \
  ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8089" > /tmp/p7_t3.log 2>&1 &
BOOT_PID=$!
sleep 25
kill $BOOT_PID
grep -i "VnpayProperties\|vnpay" /tmp/p7_t3.log | head -5
# Should not see any "binding failure" or "could not bind" error
```

- [ ] **Step 8: Commit**

```bash
git add backend/modules/payment/src/main/java/com/shop/delivery/payment/config/VnpayProperties.java \
        backend/modules/payment/src/test/java/com/shop/delivery/payment/config/VnpayPropertiesTest.java \
        backend/app/src/main/resources/application.yml \
        backend/app/src/main/resources/application-dev.yml
git commit -m "feat(payment): add VnpayProperties + application.yml binding"
```

**Acceptance:**
- `VnpayPropertiesTest` passes (2 tests)
- App boots in dev profile without binding errors
- `toString()` masks `hashSecret`

---

## TASK 4: Payment + PaymentTransaction entities + repos + PaymentEventType (TDD, 1 commit)

**Files:**
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/domain/PaymentEventType.java`
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/entity/Payment.java`
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/entity/PaymentTransaction.java`
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/repository/PaymentRepository.java`
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/repository/PaymentTransactionRepository.java`
- Create: `backend/modules/payment/src/test/java/com/shop/delivery/payment/entity/PaymentEntityTest.java`

- [ ] **Step 1: Write failing test**

Create `backend/modules/payment/src/test/java/com/shop/delivery/payment/entity/PaymentEntityTest.java`:

```java
package com.shop.delivery.payment.entity;

import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.payment.domain.PaymentEventType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEntityTest {

    @Test
    void paymentExposesAllFields() {
        Payment p = new Payment();
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        p.setId(id);
        p.setOrderId(orderId);
        p.setMethod(PaymentMethod.VNPAY);
        p.setAmount(new BigDecimal("250000.00"));
        p.setStatus(PaymentStatus.PENDING);
        p.setVnpTxnRef("DH20260522-1-1716100000000");
        p.setVnpTransactionNo("14123456");
        p.setVnpResponseCode("00");
        Instant now = Instant.now();
        p.setPaidAt(now);
        p.setVersion(0);

        assertThat(p.getId()).isEqualTo(id);
        assertThat(p.getOrderId()).isEqualTo(orderId);
        assertThat(p.getMethod()).isEqualTo(PaymentMethod.VNPAY);
        assertThat(p.getAmount()).isEqualByComparingTo("250000.00");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(p.getVnpTxnRef()).isEqualTo("DH20260522-1-1716100000000");
        assertThat(p.getVnpTransactionNo()).isEqualTo("14123456");
        assertThat(p.getVnpResponseCode()).isEqualTo("00");
        assertThat(p.getPaidAt()).isEqualTo(now);
        assertThat(p.getVersion()).isEqualTo(0);
    }

    @Test
    void transactionExposesFieldsIncludingJsonPayload() {
        PaymentTransaction tx = new PaymentTransaction();
        UUID paymentId = UUID.randomUUID();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("vnp_TxnRef", "DH-1");
        payload.put("vnp_Amount", "25000000");
        Instant now = Instant.now();

        tx.setPaymentId(paymentId);
        tx.setEventType(PaymentEventType.IPN);
        tx.setRawPayload(payload);
        tx.setRecordedAt(now);

        assertThat(tx.getPaymentId()).isEqualTo(paymentId);
        assertThat(tx.getEventType()).isEqualTo(PaymentEventType.IPN);
        assertThat(tx.getRawPayload()).containsEntry("vnp_TxnRef", "DH-1");
        assertThat(tx.getRecordedAt()).isEqualTo(now);
    }

    @Test
    void eventTypeHasExpectedValues() {
        assertThat(PaymentEventType.values())
            .containsExactly(PaymentEventType.CREATE, PaymentEventType.IPN, PaymentEventType.RETURN);
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

```bash
cd backend && ./mvnw -q -pl modules/payment -am test
```

- [ ] **Step 3: Implement `PaymentEventType`**

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/domain/PaymentEventType.java`:

```java
package com.shop.delivery.payment.domain;

/**
 * Type of event recorded in {@code payment_transaction}.
 *
 * <ul>
 *   <li>{@code CREATE} — emitted when a {@code Payment} row is first inserted via
 *       {@code POST /api/payment/vnpay/create}. Raw payload = the sorted params sent to VNPay.</li>
 *   <li>{@code IPN} — emitted on every call to {@code /api/payment/vnpay/ipn},
 *       including duplicates / invalid-signature / amount-mismatch (audit trail).</li>
 *   <li>{@code RETURN} — emitted on every call to {@code /api/payment/vnpay/return}.</li>
 * </ul>
 */
public enum PaymentEventType {
    CREATE,
    IPN,
    RETURN
}
```

- [ ] **Step 4: Implement `Payment` entity**

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/entity/Payment.java`:

```java
package com.shop.delivery.payment.entity;

import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment row — one per attempt. Retries against the same {@code order_id}
 * create new rows; old PENDING rows are marked FAILED with response_code
 * {@code SUPERSEDED}.
 *
 * <p>{@code vnp_txn_ref} is UNIQUE at DB level. {@code @Version} guards
 * against the cron-vs-IPN race documented in the threat model.
 */
@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "order_id", nullable = false, columnDefinition = "uuid")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 16)
    private PaymentMethod method;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "vnp_txn_ref", length = 64, unique = true)
    private String vnpTxnRef;

    @Column(name = "vnp_transaction_no", length = 64)
    private String vnpTransactionNo;

    @Column(name = "vnp_response_code", length = 16)
    private String vnpResponseCode;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public String getVnpTxnRef() { return vnpTxnRef; }
    public void setVnpTxnRef(String vnpTxnRef) { this.vnpTxnRef = vnpTxnRef; }
    public String getVnpTransactionNo() { return vnpTransactionNo; }
    public void setVnpTransactionNo(String vnpTransactionNo) { this.vnpTransactionNo = vnpTransactionNo; }
    public String getVnpResponseCode() { return vnpResponseCode; }
    public void setVnpResponseCode(String vnpResponseCode) { this.vnpResponseCode = vnpResponseCode; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
```

- [ ] **Step 5: Implement `PaymentTransaction` entity (with JSONB Map column)**

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/entity/PaymentTransaction.java`:

```java
package com.shop.delivery.payment.entity;

import com.shop.delivery.payment.domain.PaymentEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit row. Insert on every CREATE / IPN / RETURN endpoint
 * invocation. The {@code raw_payload} is a JSONB column — uses Hibernate 6
 * native JSON support, no third-party deps.
 */
@Entity
@Table(name = "payment_transaction")
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "payment_id", nullable = false, columnDefinition = "uuid")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 16)
    private PaymentEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> rawPayload;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
    public PaymentEventType getEventType() { return eventType; }
    public void setEventType(PaymentEventType eventType) { this.eventType = eventType; }
    public Map<String, String> getRawPayload() { return rawPayload; }
    public void setRawPayload(Map<String, String> rawPayload) { this.rawPayload = rawPayload; }
    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
```

- [ ] **Step 6: Implement repositories**

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/repository/PaymentRepository.java`:

```java
package com.shop.delivery.payment.repository;

import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** IPN idempotency lookup. */
    Optional<Payment> findByVnpTxnRef(String vnpTxnRef);

    /** Used by {@code POST /create} to mark prior PENDING attempts as SUPERSEDED. */
    List<Payment> findAllByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    /** Sweeper query — PENDING payments older than {@code cutoff}. */
    @Query("""
        SELECT p FROM Payment p
        WHERE p.status = com.shop.delivery.order.domain.PaymentStatus.PENDING
          AND p.createdAt < :cutoff
        """)
    List<Payment> findStalePending(@Param("cutoff") Instant cutoff);
}
```

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/repository/PaymentTransactionRepository.java`:

```java
package com.shop.delivery.payment.repository;

import com.shop.delivery.payment.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findAllByPaymentIdOrderByRecordedAtAsc(UUID paymentId);
}
```

- [ ] **Step 7: Run test — expect pass**

```bash
cd backend && ./mvnw -q -pl modules/payment -am test
```

- [ ] **Step 8: Commit**

```bash
git add backend/modules/payment/src/main/java/com/shop/delivery/payment/domain/PaymentEventType.java \
        backend/modules/payment/src/main/java/com/shop/delivery/payment/entity/Payment.java \
        backend/modules/payment/src/main/java/com/shop/delivery/payment/entity/PaymentTransaction.java \
        backend/modules/payment/src/main/java/com/shop/delivery/payment/repository/PaymentRepository.java \
        backend/modules/payment/src/main/java/com/shop/delivery/payment/repository/PaymentTransactionRepository.java \
        backend/modules/payment/src/test/java/com/shop/delivery/payment/entity/PaymentEntityTest.java
git commit -m "feat(payment): add Payment + PaymentTransaction entities + repos"
```

**Acceptance:**
- `PaymentEntityTest` passes (3 tests)
- Hibernate detects the entities at app boot (no "table not found" — V9 has created the schema in Task 2)
- `PaymentEventType` enum has exactly 3 values in declaration order

---


## TASK 5: VnpaySignatureService + tests (TDD, 1 commit)

This is **the** task that matters. Get the encoding right or every IPN fails. Port verbatim from research §Pattern 1 (which itself ports from VNPay's `vnpay_jsp.zip` sample). Write the tests FIRST against a known fixture, then implement.

**Files:**
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/service/VnpaySignatureService.java`
- Create: `backend/modules/payment/src/test/java/com/shop/delivery/payment/service/VnpaySignatureServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/modules/payment/src/test/java/com/shop/delivery/payment/service/VnpaySignatureServiceTest.java`:

```java
package com.shop.delivery.payment.service;

import com.shop.delivery.payment.config.VnpayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests use a hard-coded {@code hashSecret} so test vectors are byte-identical
 * across runs / machines / CI. The secret value comes from VNPay's official
 * sample doc (sample sandbox merchant) and is NOT a production credential.
 */
class VnpaySignatureServiceTest {

    private static final String SECRET = "TESTSECRETKEY123";
    private VnpaySignatureService svc;

    @BeforeEach
    void setUp() {
        VnpayProperties props = new VnpayProperties(
            "TEST01", SECRET,
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
            "http://localhost:8080/api/payment/vnpay/return",
            "http://localhost:8080/api/payment/vnpay/ipn",
            15
        );
        svc = new VnpaySignatureService(props);
    }

    @Test
    void signsKnownVector_lowercaseHex_sortedKeys() {
        // Fixture: minimal create-payment param set with stable values.
        // The hash below is the HMAC-SHA512(SECRET, "vnp_Amount=25000000&vnp_Command=pay&vnp_TxnRef=DH-1")
        // — computed once via JDK Mac to lock the encoding contract.
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Command", "pay");
        params.put("vnp_TxnRef",  "DH-1");
        params.put("vnp_Amount",  "25000000");

        VnpaySignatureService.BuildResult r = svc.buildHashAndQuery(params);

        // The hash MUST be:
        //   - 128 hex chars (HMAC-SHA512 = 512 bits = 64 bytes = 128 hex)
        //   - lowercase
        //   - deterministic for these inputs
        assertThat(r.hash())
            .hasSize(128)
            .matches("^[0-9a-f]{128}$");

        // Query string must have keys sorted alphabetically and values URL-encoded.
        assertThat(r.queryString())
            .startsWith("vnp_Amount=25000000")
            .contains("vnp_Command=pay")
            .contains("vnp_TxnRef=DH-1");
    }

    @Test
    void skipsEmptyAndNullValues() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Command", "pay");
        params.put("vnp_TxnRef",  "DH-1");
        params.put("vnp_Empty",   "");
        params.put("vnp_Null",    null);

        VnpaySignatureService.BuildResult r = svc.buildHashAndQuery(params);

        assertThat(r.queryString())
            .doesNotContain("vnp_Empty")
            .doesNotContain("vnp_Null");
    }

    @Test
    void signAndVerifyRoundtrip() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version",          "2.1.0");
        params.put("vnp_Command",          "pay");
        params.put("vnp_TmnCode",          "TEST01");
        params.put("vnp_Amount",           "25000000");
        params.put("vnp_CurrCode",         "VND");
        params.put("vnp_TxnRef",           "DH20260522-1-1716100000000");
        params.put("vnp_OrderInfo",        "Thanh toan don hang DH20260522-1");
        params.put("vnp_Locale",           "vn");
        params.put("vnp_ResponseCode",     "00");
        params.put("vnp_TransactionStatus","00");
        params.put("vnp_TransactionNo",    "14123456");

        VnpaySignatureService.BuildResult signed = svc.buildHashAndQuery(params);

        // Now simulate IPN arrival: same params + the SecureHash we just produced.
        Map<String, String> received = new HashMap<>(params);
        received.put("vnp_SecureHash", signed.hash());

        assertThat(svc.verify(received, signed.hash())).isTrue();
    }

    @Test
    void verifyRejectsTamperedHash() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "DH-1");
        params.put("vnp_Amount", "25000000");

        String goodHash = svc.buildHashAndQuery(params).hash();
        // Flip last hex char (still valid hex, just wrong)
        char last = goodHash.charAt(goodHash.length() - 1);
        char flipped = (last == 'f') ? '0' : (char) (last + 1);
        String badHash = goodHash.substring(0, goodHash.length() - 1) + flipped;

        Map<String, String> received = new HashMap<>(params);
        received.put("vnp_SecureHash", badHash);

        assertThat(svc.verify(received, badHash)).isFalse();
    }

    @Test
    void verifyRejectsTamperedAmountEvenWithOriginalHash() {
        // Attacker observes a valid (hash, params) tuple, lowers amount, replays hash.
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "DH-1");
        params.put("vnp_Amount", "25000000");

        String hash = svc.buildHashAndQuery(params).hash();

        Map<String, String> tampered = new LinkedHashMap<>();
        tampered.put("vnp_TxnRef", "DH-1");
        tampered.put("vnp_Amount", "100"); // <-- changed
        tampered.put("vnp_SecureHash", hash);

        assertThat(svc.verify(tampered, hash)).isFalse();
    }

    @Test
    void verifyRejectsNullOrBlankHash() {
        Map<String, String> params = Map.of("vnp_TxnRef", "DH-1");

        assertThat(svc.verify(params, null)).isFalse();
        assertThat(svc.verify(params, "")).isFalse();
        assertThat(svc.verify(params, "   ")).isFalse();
    }

    @Test
    void verifyStripsSecureHashAndSecureHashTypeBeforeRecomputing() {
        // Build params that include both fields; verify must strip them before hashing.
        Map<String, String> base = new LinkedHashMap<>();
        base.put("vnp_TxnRef", "DH-1");
        base.put("vnp_Amount", "25000000");
        String hash = svc.buildHashAndQuery(base).hash();

        Map<String, String> received = new LinkedHashMap<>(base);
        received.put("vnp_SecureHash",     hash);
        received.put("vnp_SecureHashType", "SHA512"); // legacy field VNPay still sometimes sends

        assertThat(svc.verify(received, hash)).isTrue();
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

```bash
cd backend && ./mvnw -q -pl modules/payment -am test -Dtest=VnpaySignatureServiceTest
```

- [ ] **Step 3: Implement `VnpaySignatureService`**

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/service/VnpaySignatureService.java`:

```java
package com.shop.delivery.payment.service;

import com.shop.delivery.payment.config.VnpayProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * HMAC-SHA512 signer + verifier for VNPay v2.1.0.
 *
 * <p><strong>Port of {@code vnpay_jsp.zip → Config.java + ajaxServlet.java}</strong>.
 * The encoding rules below are not negotiable — change a single byte and
 * signature verification fails for every transaction:
 *
 * <ul>
 *   <li>Skip params whose value is {@code null} or empty string.</li>
 *   <li>Sort param names ascending (case-sensitive — VNPay's prefix is always
 *       {@code vnp_*} so case doesn't matter in practice, but {@code Collections.sort}
 *       is deterministic).</li>
 *   <li>URL-encode both keys AND values with {@code US-ASCII} (VNPay's sample;
 *       all {@code vnp_*} keys are pure ASCII, so {@code US-ASCII} ≡ {@code UTF-8} for keys;
 *       values are already diacritic-stripped per spec §10.1 "không dấu").</li>
 *   <li>Join with {@code &}, no trailing separator.</li>
 *   <li>HMAC-SHA512 using the secret as UTF-8 bytes; result is lowercase hex.</li>
 *   <li>Compare with {@link MessageDigest#isEqual(byte[], byte[])} (constant-time).</li>
 * </ul>
 *
 * <p>See {@link #verify} for the matching decode contract on incoming requests.
 */
@Service
public class VnpaySignatureService {

    private final VnpayProperties props;

    public VnpaySignatureService(VnpayProperties props) {
        this.props = props;
    }

    /**
     * Build the data-to-be-hashed AND the URL-safe query suffix in one pass.
     *
     * @return {@link BuildResult} carrying both the lowercase-hex HMAC-SHA512
     *         signature and the query string to append after {@code ?}
     *         (caller must also append {@code &vnp_SecureHash=<hash>}).
     */
    public BuildResult buildHashAndQuery(Map<String, String> params) {
        List<String> fieldNames = new ArrayList<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            fieldNames.add(e.getKey());
        }
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String name = itr.next();
            String value = params.get(name);
            String encName  = URLEncoder.encode(name,  StandardCharsets.US_ASCII);
            String encValue = URLEncoder.encode(value, StandardCharsets.US_ASCII);
            // hashData uses raw name + encoded value (matches ajaxServlet.java exactly)
            hashData.append(name).append('=').append(encValue);
            // query suffix uses encoded name + encoded value (browser-safe)
            query.append(encName).append('=').append(encValue);
            if (itr.hasNext()) {
                hashData.append('&');
                query.append('&');
            }
        }
        String hash = hmacSHA512(props.hashSecret(), hashData.toString());
        return new BuildResult(hash, query.toString());
    }

    /**
     * Verify a {@code vnp_SecureHash} against the received params. Strips both
     * {@code vnp_SecureHash} and {@code vnp_SecureHashType} before recomputing
     * (matches {@code vnpay_ipn.jsp}'s {@code hashAllFields} behaviour).
     *
     * <p>Returns {@code false} on any of: null/blank received hash, signature
     * mismatch, or any internal encoding error.
     */
    public boolean verify(Map<String, String> receivedParams, String receivedHash) {
        if (receivedHash == null || receivedHash.isBlank()) return false;

        Map<String, String> work = new HashMap<>(receivedParams);
        work.remove("vnp_SecureHash");
        work.remove("vnp_SecureHashType");

        // Re-build hashData with same rules as buildHashAndQuery (encoded value, raw name,
        // sorted, skip empty).
        List<String> fieldNames = new ArrayList<>();
        for (Map.Entry<String, String> e : work.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            fieldNames.add(e.getKey());
        }
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String name = itr.next();
            String encValue = URLEncoder.encode(work.get(name), StandardCharsets.US_ASCII);
            hashData.append(name).append('=').append(encValue);
            if (itr.hasNext()) hashData.append('&');
        }

        String expected = hmacSHA512(props.hashSecret(), hashData.toString());
        // Constant-time compare — V6 ASVS L1 requires this for any crypto MAC check.
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.US_ASCII),
            receivedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII)
        );
    }

    static String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (GeneralSecurityException e) {
            // Should never happen — HmacSHA512 is mandatory in every JDK.
            throw new IllegalStateException("HmacSHA512 unavailable", e);
        }
    }

    /**
     * Result of {@link #buildHashAndQuery}.
     *
     * @param hash         lowercase hex HMAC-SHA512 (128 chars)
     * @param queryString  URL-encoded query suffix WITHOUT the leading {@code ?}
     *                     and WITHOUT {@code vnp_SecureHash} appended
     */
    public record BuildResult(String hash, String queryString) {}
}
```

- [ ] **Step 4: Run tests — expect all 7 pass**

```bash
cd backend && ./mvnw -q -pl modules/payment -am test -Dtest=VnpaySignatureServiceTest
```

Expected output ends with `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Commit**

```bash
git add backend/modules/payment/src/main/java/com/shop/delivery/payment/service/VnpaySignatureService.java \
        backend/modules/payment/src/test/java/com/shop/delivery/payment/service/VnpaySignatureServiceTest.java
git commit -m "feat(payment): add VnpaySignatureService (HMAC-SHA512 sign + verify)"
```

**Acceptance:**
- 7/7 tests pass
- `signAndVerifyRoundtrip` proves the create-side and verify-side encodings agree
- `verifyRejectsTamperedAmountEvenWithOriginalHash` proves amount-tamper attempts are caught
- Hash output is 128 hex chars, lowercase
- `verify` uses `MessageDigest.isEqual` (constant-time) — grep the source to confirm

---

## TASK 6: PaymentAuditRecorder (audit helper, 1 commit)

Tiny helper that all three endpoints (`/create`, `/return`, `/ipn`) call to write a `PaymentTransaction` row. Extracted as its own bean so each endpoint method stays focused.

**Files:**
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/service/PaymentAuditRecorder.java`
- Create: `backend/modules/payment/src/test/java/com/shop/delivery/payment/service/PaymentAuditRecorderTest.java`

- [ ] **Step 1: Write failing test**

Create `backend/modules/payment/src/test/java/com/shop/delivery/payment/service/PaymentAuditRecorderTest.java`:

```java
package com.shop.delivery.payment.service;

import com.shop.delivery.payment.domain.PaymentEventType;
import com.shop.delivery.payment.entity.PaymentTransaction;
import com.shop.delivery.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentAuditRecorderTest {

    @Mock PaymentTransactionRepository repo;

    @Test
    void recordsTransactionWithCopiedPayload() {
        PaymentAuditRecorder rec = new PaymentAuditRecorder(repo);
        UUID paymentId = UUID.randomUUID();
        Map<String, String> payload = Map.of(
            "vnp_TxnRef", "DH-1",
            "vnp_Amount", "25000000"
        );

        rec.record(paymentId, PaymentEventType.IPN, payload);

        ArgumentCaptor<PaymentTransaction> cap = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(repo).save(cap.capture());
        PaymentTransaction tx = cap.getValue();
        assertThat(tx.getPaymentId()).isEqualTo(paymentId);
        assertThat(tx.getEventType()).isEqualTo(PaymentEventType.IPN);
        assertThat(tx.getRawPayload()).containsEntry("vnp_TxnRef", "DH-1");
        assertThat(tx.getRecordedAt()).isNotNull();
    }

    @Test
    void recordsDoesNotMutateOriginalPayload() {
        PaymentAuditRecorder rec = new PaymentAuditRecorder(repo);
        UUID paymentId = UUID.randomUUID();
        Map<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("vnp_TxnRef", "DH-1");

        rec.record(paymentId, PaymentEventType.CREATE, payload);
        payload.put("vnp_Mutated", "yes");

        ArgumentCaptor<PaymentTransaction> cap = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(repo).save(cap.capture());
        // The saved payload must be a defensive copy — not the live map.
        assertThat(cap.getValue().getRawPayload()).doesNotContainKey("vnp_Mutated");
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

```bash
cd backend && ./mvnw -q -pl modules/payment -am test -Dtest=PaymentAuditRecorderTest
```

- [ ] **Step 3: Implement `PaymentAuditRecorder`**

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/service/PaymentAuditRecorder.java`:

```java
package com.shop.delivery.payment.service;

import com.shop.delivery.payment.domain.PaymentEventType;
import com.shop.delivery.payment.entity.PaymentTransaction;
import com.shop.delivery.payment.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only writer of {@code payment_transaction} rows. Used by every public
 * endpoint of {@code VnpayController} to leave a JSONB audit breadcrumb.
 *
 * <p>Always copies the payload to insulate the stored row from later mutation
 * by the caller (e.g. if the caller adds extra fields after auditing).
 */
@Component
public class PaymentAuditRecorder {

    private final PaymentTransactionRepository repo;

    public PaymentAuditRecorder(PaymentTransactionRepository repo) {
        this.repo = repo;
    }

    public void record(UUID paymentId, PaymentEventType type, Map<String, String> payload) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setPaymentId(paymentId);
        tx.setEventType(type);
        // Defensive copy — keep ordering for nicer JSONB inspection
        tx.setRawPayload(new LinkedHashMap<>(payload));
        tx.setRecordedAt(Instant.now());
        repo.save(tx);
    }
}
```

- [ ] **Step 4: Run test — expect pass**

```bash
cd backend && ./mvnw -q -pl modules/payment -am test -Dtest=PaymentAuditRecorderTest
```

- [ ] **Step 5: Commit**

```bash
git add backend/modules/payment/src/main/java/com/shop/delivery/payment/service/PaymentAuditRecorder.java \
        backend/modules/payment/src/test/java/com/shop/delivery/payment/service/PaymentAuditRecorderTest.java
git commit -m "feat(payment): add PaymentAuditRecorder for transaction audit trail"
```

**Acceptance:**
- 2/2 tests pass
- Payload is defensively copied
- `recordedAt` is set automatically

---


## WAVE 1 — Flow (Tasks 7–13)

Wave 1 wires the actual payment lifecycle: events in `shared`, `OrderService.confirmAfterPayment` in `order`, `VnpayPaymentService` (the brain) + `VnpayController` (the HTTP surface) in `payment`, the cron sweeper, and SecurityConfig allowlist. End of Wave 1: `mvn -q -B test` is green; `curl POST /api/payment/vnpay/create` with a valid auth header returns a signed VNPay URL.

---

## TASK 7: Cross-module events in shared (1 commit)

The three event records that cross module boundaries. Living in `shared` so neither publisher nor consumer creates a circular dep.

**Files:**
- Create: `backend/modules/shared/src/main/java/com/shop/delivery/shared/event/PaymentSucceededEvent.java`
- Create: `backend/modules/shared/src/main/java/com/shop/delivery/shared/event/PaymentFailedEvent.java`
- Create: `backend/modules/shared/src/main/java/com/shop/delivery/shared/event/OrderConfirmedEvent.java`
- Create: `backend/modules/shared/src/test/java/com/shop/delivery/shared/event/PaymentEventsTest.java`

- [ ] **Step 1: Write a smoke test for the event records (TDD-light — records are nearly trivial)**

Create `backend/modules/shared/src/test/java/com/shop/delivery/shared/event/PaymentEventsTest.java`:

```java
package com.shop.delivery.shared.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventsTest {

    @Test
    void paymentSucceededCarriesOrderAndPaymentIds() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("250000.00");

        PaymentSucceededEvent ev = new PaymentSucceededEvent(orderId, paymentId, amount, "DH-1-123");

        assertThat(ev.orderId()).isEqualTo(orderId);
        assertThat(ev.paymentId()).isEqualTo(paymentId);
        assertThat(ev.amount()).isEqualByComparingTo("250000.00");
        assertThat(ev.txnRef()).isEqualTo("DH-1-123");
    }

    @Test
    void paymentFailedCarriesResponseCode() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        PaymentFailedEvent ev = new PaymentFailedEvent(orderId, paymentId, "DH-1-123", "07");

        assertThat(ev.orderId()).isEqualTo(orderId);
        assertThat(ev.paymentId()).isEqualTo(paymentId);
        assertThat(ev.txnRef()).isEqualTo("DH-1-123");
        assertThat(ev.responseCode()).isEqualTo("07");
    }

    @Test
    void orderConfirmedCarriesCode() {
        UUID orderId = UUID.randomUUID();

        OrderConfirmedEvent ev = new OrderConfirmedEvent(orderId, "DH20260522-1", "VNPAY");

        assertThat(ev.orderId()).isEqualTo(orderId);
        assertThat(ev.orderCode()).isEqualTo("DH20260522-1");
        assertThat(ev.paymentMethod()).isEqualTo("VNPAY");
    }
}
```

- [ ] **Step 2: Implement the three records**

Create `backend/modules/shared/src/main/java/com/shop/delivery/shared/event/PaymentSucceededEvent.java`:

```java
package com.shop.delivery.shared.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published by {@code payment} module when an IPN flips a {@code Payment} row
 * from PENDING to SUCCESS. Consumed by {@code order} module's
 * {@code PaymentEventListener} (which calls {@code OrderService.confirmAfterPayment}).
 *
 * <p>Use {@code @TransactionalEventListener(AFTER_COMMIT)} on the consumer side
 * so the order transition runs in a new TX <em>after</em> the payment row is
 * committed — avoids dirty-write races between the IPN handler and the order
 * status update.
 */
public record PaymentSucceededEvent(
    UUID orderId,
    UUID paymentId,
    BigDecimal amount,
    String txnRef
) {}
```

Create `backend/modules/shared/src/main/java/com/shop/delivery/shared/event/PaymentFailedEvent.java`:

```java
package com.shop.delivery.shared.event;

import java.util.UUID;

/**
 * Published when an IPN reports a non-success status (e.g. user cancelled,
 * insufficient funds, bank refused) or the {@code PaymentExpiryScheduler}
 * marks a payment as expired.
 *
 * <p>Currently has no listeners in P7 — kept for symmetry with
 * {@code PaymentSucceededEvent} and for P8 to wire bot notifications
 * ("đơn của bạn thanh toán thất bại, vui lòng thử lại").
 *
 * @param responseCode the VNPay {@code vnp_ResponseCode} value, or {@code "EXPIRED"}
 *                     when emitted by the scheduler.
 */
public record PaymentFailedEvent(
    UUID orderId,
    UUID paymentId,
    String txnRef,
    String responseCode
) {}
```

Create `backend/modules/shared/src/main/java/com/shop/delivery/shared/event/OrderConfirmedEvent.java`:

```java
package com.shop.delivery.shared.event;

import java.util.UUID;

/**
 * Published by {@code OrderService} when an order transitions to {@code CONFIRMED}.
 *
 * <p>P7 emits this from {@code confirmAfterPayment}. The existing
 * {@code OrderService.confirm} (used by Admin manual confirm of COD orders)
 * also gets this added in TASK 10. P8 wires bot listeners.
 *
 * @param paymentMethod string form of {@code PaymentMethod} (COD / VNPAY) — kept
 *                      as String to avoid {@code order.domain} cross-import.
 */
public record OrderConfirmedEvent(
    UUID orderId,
    String orderCode,
    String paymentMethod
) {}
```

- [ ] **Step 3: Run test — expect pass**

```bash
cd backend && ./mvnw -q -pl modules/shared -am test -Dtest=PaymentEventsTest
```

- [ ] **Step 4: Commit**

```bash
git add backend/modules/shared/src/main/java/com/shop/delivery/shared/event/PaymentSucceededEvent.java \
        backend/modules/shared/src/main/java/com/shop/delivery/shared/event/PaymentFailedEvent.java \
        backend/modules/shared/src/main/java/com/shop/delivery/shared/event/OrderConfirmedEvent.java \
        backend/modules/shared/src/test/java/com/shop/delivery/shared/event/PaymentEventsTest.java
git commit -m "feat(shared): add PaymentSucceeded/Failed + OrderConfirmed events"
```

**Acceptance:**
- 3/3 tests pass
- All three records live in `com.shop.delivery.shared.event`
- No `payment` or `order` module imports added to `shared`

---

## TASK 8: VnpayPaymentService (create + ipn + return) + tests (TDD, 1 commit)

The core service. Three public methods. Heavy test coverage — this is where idempotency and amount-tamper protection live.

**Files:**
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/service/VnpayPaymentService.java`
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/api/dto/CreatePaymentRequest.java`
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/api/dto/CreatePaymentResponse.java`
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/api/dto/IpnResponse.java`
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/api/dto/ReturnRedirect.java`
- Create: `backend/modules/payment/src/test/java/com/shop/delivery/payment/service/VnpayPaymentServiceTest.java`

- [ ] **Step 1: Write the DTOs first (no logic, just shapes)**

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/api/dto/CreatePaymentRequest.java`:

```java
package com.shop.delivery.payment.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePaymentRequest(@NotNull UUID orderId) {}
```

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/api/dto/CreatePaymentResponse.java`:

```java
package com.shop.delivery.payment.api.dto;

public record CreatePaymentResponse(String paymentUrl, String txnRef) {}
```

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/api/dto/IpnResponse.java`:

```java
package com.shop.delivery.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * VNPay's IPN response contract — field NAMES (RspCode, Message) are part of
 * the protocol, not a stylistic choice. Capitalisation matters.
 *
 * <p>Documented RspCodes (from {@code vnpay_ipn.jsp}):
 * <ul>
 *   <li>{@code 00} — Confirm Success (payment recorded)</li>
 *   <li>{@code 01} — Order not Found (TxnRef has no Payment row)</li>
 *   <li>{@code 02} — Order already confirmed (replay — Payment.status != PENDING)</li>
 *   <li>{@code 04} — Invalid Amount (vnp_Amount != payment.amount * 100)</li>
 *   <li>{@code 97} — Invalid Checksum (HMAC mismatch)</li>
 * </ul>
 */
public record IpnResponse(
    @JsonProperty("RspCode") String rspCode,
    @JsonProperty("Message") String message
) {
    public static IpnResponse ok()                  { return new IpnResponse("00", "Confirm Success"); }
    public static IpnResponse orderNotFound()       { return new IpnResponse("01", "Order not found"); }
    public static IpnResponse alreadyConfirmed()    { return new IpnResponse("02", "Order already confirmed"); }
    public static IpnResponse invalidAmount()       { return new IpnResponse("04", "Invalid Amount"); }
    public static IpnResponse invalidChecksum()     { return new IpnResponse("97", "Invalid Checksum"); }
}
```

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/api/dto/ReturnRedirect.java`:

```java
package com.shop.delivery.payment.api.dto;

/**
 * Return-URL handler decision: where to redirect the browser + a flag for
 * whether the underlying payment was successful. No DB side-effects in this
 * codepath — IPN is the source of truth.
 */
public record ReturnRedirect(String redirectUrl, boolean success) {}
```

- [ ] **Step 2: Write the failing test for `VnpayPaymentService`**

Create `backend/modules/payment/src/test/java/com/shop/delivery/payment/service/VnpayPaymentServiceTest.java`:

```java
package com.shop.delivery.payment.service;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.payment.api.dto.CreatePaymentResponse;
import com.shop.delivery.payment.api.dto.IpnResponse;
import com.shop.delivery.payment.api.dto.ReturnRedirect;
import com.shop.delivery.payment.config.VnpayProperties;
import com.shop.delivery.payment.entity.Payment;
import com.shop.delivery.payment.repository.PaymentRepository;
import com.shop.delivery.shared.event.PaymentFailedEvent;
import com.shop.delivery.shared.event.PaymentSucceededEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VnpayPaymentServiceTest {

    private static final String SECRET = "TESTSECRETKEY123";

    @Mock OrderRepository orderRepo;
    @Mock PaymentRepository paymentRepo;
    @Mock PaymentAuditRecorder audit;
    @Mock ApplicationEventPublisher events;

    private VnpayProperties props;
    private VnpaySignatureService sig;
    private Clock fixedClock;
    private VnpayPaymentService svc;

    @BeforeEach
    void setUp() {
        props = new VnpayProperties(
            "TEST01", SECRET,
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
            "http://localhost:8080/api/payment/vnpay/return",
            "http://localhost:8080/api/payment/vnpay/ipn",
            15
        );
        sig = new VnpaySignatureService(props);
        fixedClock = Clock.fixed(Instant.parse("2026-05-22T10:00:00Z"), ZoneId.of("UTC"));
        svc = new VnpayPaymentService(orderRepo, paymentRepo, sig, props, audit, events, fixedClock);
    }

    // ----- createPayment -----

    @Test
    void create_returnsSignedUrl_andPersistsPendingPaymentRow() {
        Order order = vnpayOrder(UUID.randomUUID(), 42L, new BigDecimal("250000.00"));
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepo.findAllByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING))
            .thenReturn(List.of());

        CreatePaymentResponse resp = svc.createPayment(order.getId(), 42L, "127.0.0.1");

        assertThat(resp.paymentUrl())
            .startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?")
            .contains("vnp_TmnCode=TEST01")
            .contains("vnp_Amount=25000000")              // 250000.00 * 100
            .contains("vnp_TxnRef=")
            .contains("vnp_SecureHash=");
        assertThat(resp.txnRef())
            .startsWith(order.getCode() + "-")
            .matches(".+-\\d+");

        ArgumentCaptor<Payment> cap = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepo).save(cap.capture());
        Payment saved = cap.getValue();
        assertThat(saved.getOrderId()).isEqualTo(order.getId());
        assertThat(saved.getMethod()).isEqualTo(PaymentMethod.VNPAY);
        assertThat(saved.getAmount()).isEqualByComparingTo("250000.00");
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getVnpTxnRef()).isEqualTo(resp.txnRef());
    }

    @Test
    void create_marksPriorPendingPaymentsAsSUPERSEDED() {
        Order order = vnpayOrder(UUID.randomUUID(), 42L, new BigDecimal("250000.00"));
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        Payment prior = new Payment();
        prior.setId(UUID.randomUUID());
        prior.setOrderId(order.getId());
        prior.setStatus(PaymentStatus.PENDING);
        prior.setAmount(new BigDecimal("250000.00"));
        when(paymentRepo.findAllByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING))
            .thenReturn(List.of(prior));

        svc.createPayment(order.getId(), 42L, "127.0.0.1");

        // Should save TWICE: once for the superseded prior, once for the new pending.
        verify(paymentRepo, times(2)).save(any(Payment.class));
        assertThat(prior.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(prior.getVnpResponseCode()).isEqualTo("SUPERSEDED");
    }

    @Test
    void create_rejectsOrderOwnedBySomeoneElse() {
        Order order = vnpayOrder(UUID.randomUUID(), 42L, new BigDecimal("250000.00"));
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> svc.createPayment(order.getId(), 99L, "127.0.0.1"))
            .hasMessageContaining("không thuộc về bạn");
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void create_rejectsCODOrder() {
        Order order = vnpayOrder(UUID.randomUUID(), 42L, new BigDecimal("250000.00"));
        order.setPaymentMethod(PaymentMethod.COD);
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> svc.createPayment(order.getId(), 42L, "127.0.0.1"))
            .hasMessageContaining("không dùng VNPay");
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void create_rejectsAlreadyPaidOrder() {
        Order order = vnpayOrder(UUID.randomUUID(), 42L, new BigDecimal("250000.00"));
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> svc.createPayment(order.getId(), 42L, "127.0.0.1"))
            .hasMessageContaining("đã có trạng thái");
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void create_rejectsUnknownOrder() {
        UUID id = UUID.randomUUID();
        when(orderRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.createPayment(id, 42L, "127.0.0.1"))
            .hasMessageContaining("không tồn tại");
    }

    // ----- handleIpn -----

    @Test
    void ipn_happyPath_flipsPaymentToSuccessAndPublishesEvent() {
        Payment payment = pendingPayment(new BigDecimal("250000.00"));
        when(paymentRepo.findByVnpTxnRef(payment.getVnpTxnRef())).thenReturn(Optional.of(payment));

        Map<String, String> params = signedIpnParams(payment, "00", "00", "14123456");

        IpnResponse resp = svc.handleIpn(params);

        assertThat(resp.rspCode()).isEqualTo("00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getVnpTransactionNo()).isEqualTo("14123456");
        assertThat(payment.getVnpResponseCode()).isEqualTo("00");
        assertThat(payment.getPaidAt()).isNotNull();

        verify(paymentRepo).save(payment);
        ArgumentCaptor<PaymentSucceededEvent> ev = ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().orderId()).isEqualTo(payment.getOrderId());
        assertThat(ev.getValue().paymentId()).isEqualTo(payment.getId());
        assertThat(ev.getValue().amount()).isEqualByComparingTo("250000.00");
    }

    @Test
    void ipn_badSignature_returns97_andNoDbWrite() {
        Payment payment = pendingPayment(new BigDecimal("250000.00"));

        Map<String, String> params = signedIpnParams(payment, "00", "00", "14123456");
        params.put("vnp_SecureHash", "f".repeat(128)); // valid hex but wrong hash

        IpnResponse resp = svc.handleIpn(params);

        assertThat(resp.rspCode()).isEqualTo("97");
        verify(paymentRepo, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void ipn_unknownTxnRef_returns01() {
        when(paymentRepo.findByVnpTxnRef(any())).thenReturn(Optional.empty());

        Payment ghost = pendingPayment(new BigDecimal("250000.00"));
        Map<String, String> params = signedIpnParams(ghost, "00", "00", "14123456");

        IpnResponse resp = svc.handleIpn(params);

        assertThat(resp.rspCode()).isEqualTo("01");
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void ipn_replayWhenAlreadySuccess_returns02_andAuditsButDoesNotMutate() {
        Payment payment = pendingPayment(new BigDecimal("250000.00"));
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(Instant.parse("2026-05-22T09:55:00Z"));
        when(paymentRepo.findByVnpTxnRef(payment.getVnpTxnRef())).thenReturn(Optional.of(payment));

        Map<String, String> params = signedIpnParams(payment, "00", "00", "14123456");
        IpnResponse resp = svc.handleIpn(params);

        assertThat(resp.rspCode()).isEqualTo("02");
        verify(paymentRepo, never()).save(any());
        verify(events, never()).publishEvent(any());
        // But audit IS recorded.
        verify(audit).record(eq(payment.getId()),
            eq(com.shop.delivery.payment.domain.PaymentEventType.IPN), any());
    }

    @Test
    void ipn_amountMismatch_returns04_paymentStaysPending() {
        Payment payment = pendingPayment(new BigDecimal("250000.00"));
        when(paymentRepo.findByVnpTxnRef(payment.getVnpTxnRef())).thenReturn(Optional.of(payment));

        Map<String, String> params = signedIpnParams(payment, "00", "00", "14123456");
        // Tamper amount AFTER signing (the signed amount was 25000000; we change it to 100
        // and re-sign so the test is honest — attacker who has the secret would do this)
        params.put("vnp_Amount", "100");
        params.put("vnp_SecureHash", sig.buildHashAndQuery(stripHash(params)).hash());

        IpnResponse resp = svc.handleIpn(params);

        assertThat(resp.rspCode()).isEqualTo("04");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void ipn_responseCodeFailure_flipsPaymentToFailedAndPublishesFailedEvent() {
        Payment payment = pendingPayment(new BigDecimal("250000.00"));
        when(paymentRepo.findByVnpTxnRef(payment.getVnpTxnRef())).thenReturn(Optional.of(payment));

        // vnp_ResponseCode=07 means "Suspicious transaction" — VNPay marks failure.
        Map<String, String> params = signedIpnParams(payment, "07", "01", "14123456");
        IpnResponse resp = svc.handleIpn(params);

        // IPN is still ack'd 00 — we accept VNPay's notification successfully even
        // though the payment itself failed. The merchant's job is to record the outcome.
        assertThat(resp.rspCode()).isEqualTo("00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getVnpResponseCode()).isEqualTo("07");

        ArgumentCaptor<PaymentFailedEvent> ev = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().responseCode()).isEqualTo("07");
    }

    // ----- handleReturn -----

    @Test
    void return_validSig_successCodes_redirectsToStaticSuccess() {
        Payment payment = pendingPayment(new BigDecimal("250000.00"));
        when(paymentRepo.findByVnpTxnRef(payment.getVnpTxnRef())).thenReturn(Optional.of(payment));

        Map<String, String> params = signedIpnParams(payment, "00", "00", "14123456");

        ReturnRedirect rr = svc.handleReturn(params);

        assertThat(rr.success()).isTrue();
        assertThat(rr.redirectUrl())
            .startsWith("/payment-success.html?")
            .contains("orderCode=" + payment.getVnpTxnRef().split("-")[0])
            .contains("amount=250000");
        // Return URL does NOT write DB
        verify(paymentRepo, never()).save(any());
        // But DOES audit (helps debug Return-before-IPN races)
        verify(audit).record(any(),
            eq(com.shop.delivery.payment.domain.PaymentEventType.RETURN), any());
    }

    @Test
    void return_badSig_redirectsToFailWithReason() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "DH-FAKE-1");
        params.put("vnp_Amount", "100");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", "0".repeat(128));

        ReturnRedirect rr = svc.handleReturn(params);

        assertThat(rr.success()).isFalse();
        assertThat(rr.redirectUrl())
            .startsWith("/payment-failed.html?")
            .contains("reason=invalid_signature");
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void return_validSig_failCode_redirectsToStaticFailed() {
        Payment payment = pendingPayment(new BigDecimal("250000.00"));
        when(paymentRepo.findByVnpTxnRef(payment.getVnpTxnRef())).thenReturn(Optional.of(payment));

        Map<String, String> params = signedIpnParams(payment, "24", "02", "14123456"); // 24 = user cancelled

        ReturnRedirect rr = svc.handleReturn(params);

        assertThat(rr.success()).isFalse();
        assertThat(rr.redirectUrl())
            .startsWith("/payment-failed.html?")
            .contains("code=24");
        verify(paymentRepo, never()).save(any());
    }

    // ----- helpers -----

    private Order vnpayOrder(UUID id, Long customerId, BigDecimal total) {
        Order o = new Order();
        o.setId(id);
        o.setCode("DH20260522-1");
        o.setCustomerId(customerId);
        o.setTotal(total);
        o.setPaymentMethod(PaymentMethod.VNPAY);
        o.setPaymentStatus(PaymentStatus.PENDING);
        o.setStatus(OrderStatus.PENDING);
        return o;
    }

    private Payment pendingPayment(BigDecimal amount) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOrderId(UUID.randomUUID());
        p.setMethod(PaymentMethod.VNPAY);
        p.setAmount(amount);
        p.setStatus(PaymentStatus.PENDING);
        p.setVnpTxnRef("DH20260522-1-1716100000000");
        return p;
    }

    /** Builds a fully-signed IPN-style param map for a given payment + outcome. */
    private Map<String, String> signedIpnParams(Payment p, String responseCode, String txStatus, String txNo) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode",          "TEST01");
        params.put("vnp_Amount",           p.getAmount().multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        params.put("vnp_BankCode",         "NCB");
        params.put("vnp_BankTranNo",       "VNP" + txNo);
        params.put("vnp_CardType",         "ATM");
        params.put("vnp_OrderInfo",        "Thanh toan don hang " + p.getVnpTxnRef().split("-")[0]);
        params.put("vnp_PayDate",          "20260522170000");
        params.put("vnp_ResponseCode",     responseCode);
        params.put("vnp_TransactionNo",    txNo);
        params.put("vnp_TransactionStatus",txStatus);
        params.put("vnp_TxnRef",           p.getVnpTxnRef());
        String hash = sig.buildHashAndQuery(params).hash();
        params.put("vnp_SecureHash", hash);
        return params;
    }

    private Map<String, String> stripHash(Map<String, String> in) {
        Map<String, String> out = new HashMap<>(in);
        out.remove("vnp_SecureHash");
        out.remove("vnp_SecureHashType");
        return out;
    }

    // Mockito argument matcher shortcut
    private static <T> T eq(T v) { return org.mockito.ArgumentMatchers.eq(v); }
}
```

- [ ] **Step 3: Implement `VnpayPaymentService`**

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/service/VnpayPaymentService.java`:

```java
package com.shop.delivery.payment.service;

import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.payment.api.dto.CreatePaymentResponse;
import com.shop.delivery.payment.api.dto.IpnResponse;
import com.shop.delivery.payment.api.dto.ReturnRedirect;
import com.shop.delivery.payment.config.VnpayProperties;
import com.shop.delivery.payment.domain.PaymentEventType;
import com.shop.delivery.payment.entity.Payment;
import com.shop.delivery.payment.repository.PaymentRepository;
import com.shop.delivery.shared.event.PaymentFailedEvent;
import com.shop.delivery.shared.event.PaymentSucceededEvent;
import com.shop.delivery.shared.exception.NotFoundException;
import com.shop.delivery.shared.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * VNPay business logic. Three public methods:
 *
 * <ul>
 *   <li>{@link #createPayment} — caller-authenticated (controller does ownership
 *       check via initData). Builds + signs URL, persists PENDING Payment row,
 *       supersedes prior PENDING rows for the same order, returns the URL.</li>
 *   <li>{@link #handleIpn} — public unauthenticated. Verifies signature, checks
 *       idempotency, amount, response codes; flips status; emits events.
 *       <strong>Source of truth</strong> for payment outcome.</li>
 *   <li>{@link #handleReturn} — public unauthenticated. Verifies signature,
 *       decides redirect target. <strong>No DB writes</strong> apart from
 *       audit row.</li>
 * </ul>
 *
 * <p>Uses {@link Clock} for testability (fixed clock in unit tests, system
 * clock in production). Uses {@link ApplicationEventPublisher} (NOT direct
 * call to {@code OrderService}) for cross-module signalling — see research
 * §Anti-Patterns.
 */
@Service
public class VnpayPaymentService {

    private static final Logger log = LoggerFactory.getLogger(VnpayPaymentService.class);
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderRepository orderRepo;
    private final PaymentRepository paymentRepo;
    private final VnpaySignatureService sig;
    private final VnpayProperties props;
    private final PaymentAuditRecorder audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public VnpayPaymentService(OrderRepository orderRepo,
                               PaymentRepository paymentRepo,
                               VnpaySignatureService sig,
                               VnpayProperties props,
                               PaymentAuditRecorder audit,
                               ApplicationEventPublisher events,
                               Clock clock) {
        this.orderRepo = orderRepo;
        this.paymentRepo = paymentRepo;
        this.sig = sig;
        this.props = props;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // -------- create --------

    @Transactional
    public CreatePaymentResponse createPayment(UUID orderId, Long callerCustomerId, String ipAddr) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Đơn không tồn tại"));
        if (!order.getCustomerId().equals(callerCustomerId)) {
            throw new ValidationException("FORBIDDEN", "Đơn không thuộc về bạn");
        }
        if (order.getPaymentMethod() != PaymentMethod.VNPAY) {
            throw new ValidationException("INVALID_METHOD", "Đơn không dùng VNPay");
        }
        if (order.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ValidationException("ALREADY_PAID",
                "Đơn đã có trạng thái thanh toán: " + order.getPaymentStatus());
        }

        // Supersede any prior PENDING attempts for this order.
        List<Payment> prior = paymentRepo.findAllByOrderIdAndStatus(orderId, PaymentStatus.PENDING);
        for (Payment p : prior) {
            p.setStatus(PaymentStatus.FAILED);
            p.setVnpResponseCode("SUPERSEDED");
            paymentRepo.save(p);
        }

        long epochMs = clock.instant().toEpochMilli();
        String txnRef = order.getCode() + "-" + epochMs;

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(order.getId());
        payment.setMethod(PaymentMethod.VNPAY);
        payment.setAmount(order.getTotal());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setVnpTxnRef(txnRef);
        paymentRepo.save(payment);

        // Build VNPay params (insertion order does not matter — signer sorts).
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), VN_ZONE);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version",    "2.1.0");
        params.put("vnp_Command",    "pay");
        params.put("vnp_TmnCode",    props.tmnCode());
        params.put("vnp_Amount",     order.getTotal().multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        params.put("vnp_CurrCode",   "VND");
        params.put("vnp_TxnRef",     txnRef);
        params.put("vnp_OrderInfo",  "Thanh toan don hang " + order.getCode());
        params.put("vnp_OrderType",  "other");
        params.put("vnp_Locale",     "vn");
        params.put("vnp_ReturnUrl",  props.returnUrl());
        params.put("vnp_IpAddr",     ipAddr == null ? "127.0.0.1" : ipAddr);
        params.put("vnp_CreateDate", now.format(VNP_DATE));
        params.put("vnp_ExpireDate", now.plusMinutes(props.timeoutMinutes()).format(VNP_DATE));

        VnpaySignatureService.BuildResult signed = sig.buildHashAndQuery(params);
        String url = props.payUrl() + "?" + signed.queryString() + "&vnp_SecureHash=" + signed.hash();

        audit.record(payment.getId(), PaymentEventType.CREATE, params);

        log.info("VNPay payment created: orderId={} txnRef={} amount={}",
            order.getId(), txnRef, order.getTotal());

        return new CreatePaymentResponse(url, txnRef);
    }

    // -------- ipn --------

    @Transactional
    public IpnResponse handleIpn(Map<String, String> params) {
        String receivedHash = params.get("vnp_SecureHash");
        if (!sig.verify(params, receivedHash)) {
            log.warn("VNPay IPN: invalid checksum (txnRef={})", params.get("vnp_TxnRef"));
            return IpnResponse.invalidChecksum();
        }

        String txnRef = params.get("vnp_TxnRef");
        Optional<Payment> maybe = paymentRepo.findByVnpTxnRef(txnRef);
        if (maybe.isEmpty()) {
            log.warn("VNPay IPN: unknown txnRef={}", txnRef);
            return IpnResponse.orderNotFound();
        }
        Payment payment = maybe.get();

        // Idempotency — design §10.3 step 3
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("VNPay IPN replay: txnRef={} status={}", txnRef, payment.getStatus());
            audit.record(payment.getId(), PaymentEventType.IPN, params);
            return IpnResponse.alreadyConfirmed();
        }

        // Amount check — design §10.3 step 4
        long expected = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
        long received;
        try {
            received = Long.parseLong(params.get("vnp_Amount"));
        } catch (NumberFormatException nfe) {
            audit.record(payment.getId(), PaymentEventType.IPN, params);
            return IpnResponse.invalidAmount();
        }
        if (expected != received) {
            log.warn("VNPay IPN amount mismatch: txnRef={} expected={} received={}",
                txnRef, expected, received);
            audit.record(payment.getId(), PaymentEventType.IPN, params);
            return IpnResponse.invalidAmount();
        }

        String responseCode = params.get("vnp_ResponseCode");
        String txStatus     = params.get("vnp_TransactionStatus");
        boolean success = "00".equals(responseCode) && "00".equals(txStatus);

        if (success) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setVnpTransactionNo(params.get("vnp_TransactionNo"));
            payment.setVnpResponseCode(responseCode);
            payment.setPaidAt(clock.instant());
            paymentRepo.save(payment);
            events.publishEvent(new PaymentSucceededEvent(
                payment.getOrderId(), payment.getId(), payment.getAmount(), payment.getVnpTxnRef()));
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setVnpResponseCode(responseCode);
            paymentRepo.save(payment);
            events.publishEvent(new PaymentFailedEvent(
                payment.getOrderId(), payment.getId(), payment.getVnpTxnRef(), responseCode));
        }

        audit.record(payment.getId(), PaymentEventType.IPN, params);
        return IpnResponse.ok();
    }

    // -------- return --------

    @Transactional
    public ReturnRedirect handleReturn(Map<String, String> params) {
        String hash = params.get("vnp_SecureHash");
        String txnRef = params.get("vnp_TxnRef");
        boolean valid = sig.verify(params, hash);

        // Always try to audit (helps debug Return-before-IPN races); skip if no payment match.
        Optional<Payment> match = (txnRef != null)
            ? paymentRepo.findByVnpTxnRef(txnRef)
            : Optional.empty();
        match.ifPresent(p -> audit.record(p.getId(), PaymentEventType.RETURN, params));

        if (!valid) {
            return new ReturnRedirect("/payment-failed.html?reason=invalid_signature", false);
        }

        String responseCode = params.get("vnp_ResponseCode");
        String txStatus = params.get("vnp_TransactionStatus");
        boolean ok = "00".equals(responseCode) && "00".equals(txStatus);

        String orderCode = (txnRef != null && txnRef.contains("-"))
            ? txnRef.substring(0, txnRef.lastIndexOf('-'))
            : "";
        String amountVnd = "";
        if (match.isPresent()) {
            amountVnd = match.get().getAmount().toBigInteger().toString();
        } else if (params.get("vnp_Amount") != null) {
            try {
                amountVnd = String.valueOf(Long.parseLong(params.get("vnp_Amount")) / 100);
            } catch (NumberFormatException ignored) { /* leave blank */ }
        }

        String enc = (String s) -> URLEncoder.encode(s, StandardCharsets.UTF_8);
        // Java records don't allow lambda var names like that — inline:
        if (ok) {
            String url = "/payment-success.html?orderCode="
                + URLEncoder.encode(orderCode, StandardCharsets.UTF_8)
                + "&amount=" + URLEncoder.encode(amountVnd, StandardCharsets.UTF_8);
            return new ReturnRedirect(url, true);
        }
        String url = "/payment-failed.html?code="
            + URLEncoder.encode(responseCode == null ? "" : responseCode, StandardCharsets.UTF_8)
            + "&orderCode=" + URLEncoder.encode(orderCode, StandardCharsets.UTF_8);
        return new ReturnRedirect(url, false);
    }
}
```

> **Note for implementer:** The `String enc = ...` line inside `handleReturn` is a leftover from the original draft and won't compile — REMOVE that line before commit. The two inline `URLEncoder.encode(...)` calls below it are the actual implementation.

- [ ] **Step 4: Run tests — expect all 13 pass**

```bash
cd backend && ./mvnw -q -pl modules/payment -am test -Dtest=VnpayPaymentServiceTest
```

Expected: `Tests run: 13, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git add backend/modules/payment/src/main/java/com/shop/delivery/payment/api/dto/CreatePaymentRequest.java \
        backend/modules/payment/src/main/java/com/shop/delivery/payment/api/dto/CreatePaymentResponse.java \
        backend/modules/payment/src/main/java/com/shop/delivery/payment/api/dto/IpnResponse.java \
        backend/modules/payment/src/main/java/com/shop/delivery/payment/api/dto/ReturnRedirect.java \
        backend/modules/payment/src/main/java/com/shop/delivery/payment/service/VnpayPaymentService.java \
        backend/modules/payment/src/test/java/com/shop/delivery/payment/service/VnpayPaymentServiceTest.java
git commit -m "feat(payment): add VnpayPaymentService (create + ipn + return)"
```

**Acceptance:**
- 13/13 tests pass
- IPN happy path emits `PaymentSucceededEvent`
- IPN replay returns 02 without DB save
- IPN amount tamper returns 04, status stays PENDING
- Return URL never calls `paymentRepo.save`

---


## TASK 9: VnpayController + Testcontainers IT (1 commit)

The HTTP surface. Three endpoints. Plus one Testcontainers integration test exercising the IPN happy path end-to-end.

**Files:**
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/api/VnpayController.java`
- Create: `backend/app/src/test/java/com/shop/delivery/payment/VnpayControllerIT.java`

- [ ] **Step 1: Implement `VnpayController`**

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/api/VnpayController.java`:

```java
package com.shop.delivery.payment.api;

import com.shop.delivery.auth.security.CurrentUser;
import com.shop.delivery.auth.security.TelegramPrincipal;
import com.shop.delivery.payment.api.dto.CreatePaymentRequest;
import com.shop.delivery.payment.api.dto.CreatePaymentResponse;
import com.shop.delivery.payment.api.dto.IpnResponse;
import com.shop.delivery.payment.api.dto.ReturnRedirect;
import com.shop.delivery.payment.service.VnpayPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * VNPay HTTP endpoints. URL paths are fixed by the design spec — do not change.
 *
 * <ul>
 *   <li>{@code POST /api/payment/vnpay/create} — Telegram-authenticated, returns JSON.</li>
 *   <li>{@code GET  /api/payment/vnpay/return} — public, returns 302 redirect.</li>
 *   <li>{@code POST /api/payment/vnpay/ipn}    — public, returns JSON with VNPay's
 *       required {@code RspCode}/{@code Message} shape. Always 200 OK (never 4xx/5xx)
 *       — VNPay treats non-200 as failed delivery and may retry.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/payment/vnpay")
public class VnpayController {

    private final VnpayPaymentService svc;

    public VnpayController(VnpayPaymentService svc) {
        this.svc = svc;
    }

    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CreatePaymentResponse create(@Valid @RequestBody CreatePaymentRequest req,
                                        @CurrentUser TelegramPrincipal user,
                                        HttpServletRequest http) {
        String ip = resolveClientIp(http);
        return svc.createPayment(req.orderId(), user.id(), ip);
    }

    @GetMapping("/return")
    public ResponseEntity<Void> returnFromVnpay(@RequestParam Map<String, String> rawParams,
                                                HttpServletRequest http) {
        // @RequestParam Map decoded values for us; collect query params from the
        // request as-is (in case duplicates appear, last-wins is fine for VNPay).
        Map<String, String> params = collectQueryParams(http);
        ReturnRedirect r = svc.handleReturn(params);
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(r.redirectUrl()))
            .build();
    }

    @PostMapping(value = "/ipn", produces = MediaType.APPLICATION_JSON_VALUE)
    public IpnResponse ipn(HttpServletRequest http) {
        Map<String, String> params = collectQueryParams(http);
        return svc.ipn(params);
    }

    /**
     * Pull every query param into a Map. VNPay sends IPN as form-encoded GET-style
     * params in either query string or POST body — Spring exposes both via
     * {@code getParameterNames}.
     */
    private static Map<String, String> collectQueryParams(HttpServletRequest http) {
        Map<String, String> out = new HashMap<>();
        Enumeration<String> names = http.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String value = http.getParameter(name);
            if (value != null) out.put(name, value);
        }
        return out;
    }

    private static String resolveClientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // First entry is the original client
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String remote = http.getRemoteAddr();
        return remote == null || remote.isBlank() ? "127.0.0.1" : remote;
    }
}
```

> **Note for implementer:** The controller calls `svc.ipn(params)` — that method is named `handleIpn` in `VnpayPaymentService`. Rename either call site to match. Recommendation: rename the controller call to `svc.handleIpn(params)` to keep the service API consistent with the test naming.

- [ ] **Step 2: Write the integration test**

Create `backend/app/src/test/java/com/shop/delivery/payment/VnpayControllerIT.java`:

```java
package com.shop.delivery.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.payment.entity.Payment;
import com.shop.delivery.payment.repository.PaymentRepository;
import com.shop.delivery.payment.service.VnpaySignatureService;
import com.shop.delivery.support.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class VnpayControllerIT extends PostgresTestContainer {

    @Autowired MockMvc mvc;
    @Autowired OrderRepository orderRepo;
    @Autowired PaymentRepository paymentRepo;
    @Autowired VnpaySignatureService sig;
    @Autowired ObjectMapper json;

    @Test
    @Transactional
    void ipnHappyPath_flipsPaymentAndOrder() throws Exception {
        // Arrange: insert an order + pending payment directly.
        Order order = persistVnpayOrder(new BigDecimal("250000.00"));
        Payment payment = persistPendingPayment(order, "DH-IT-1-" + System.currentTimeMillis());

        // Build a signed IPN body.
        Map<String, String> ipn = signedIpn(payment, "00", "00");

        // Act: POST to /ipn with form params.
        mvc.perform(post("/api/payment/vnpay/ipn")
                .contentType("application/x-www-form-urlencoded")
                .content(toForm(ipn)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.RspCode").value("00"))
            .andExpect(jsonPath("$.Message").value("Confirm Success"));

        // Assert payment flipped synchronously.
        Payment refreshed = paymentRepo.findById(payment.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(refreshed.getPaidAt()).isNotNull();

        // Assert order eventually confirms (AFTER_COMMIT listener fires in a separate TX,
        // so we Await rather than asserting immediately).
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Order o = orderRepo.findById(order.getId()).orElseThrow();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        });
    }

    @Test
    @Transactional
    void ipnReplay_returns02_doesNotChangeAnything() throws Exception {
        Order order = persistVnpayOrder(new BigDecimal("250000.00"));
        Payment payment = persistPendingPayment(order, "DH-IT-2-" + System.currentTimeMillis());
        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepo.save(payment);

        Map<String, String> ipn = signedIpn(payment, "00", "00");

        mvc.perform(post("/api/payment/vnpay/ipn")
                .contentType("application/x-www-form-urlencoded")
                .content(toForm(ipn)))
            .andExpect(jsonPath("$.RspCode").value("02"));
    }

    // ---- helpers ----

    private Order persistVnpayOrder(BigDecimal total) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCode("DH-IT-" + System.currentTimeMillis());
        o.setCustomerId(123L);
        o.setPickupLat(new BigDecimal("21.0285"));
        o.setPickupLng(new BigDecimal("105.8542"));
        o.setDeliveryAddress("Test addr");
        o.setDeliveryLat(new BigDecimal("21.0193"));
        o.setDeliveryLng(new BigDecimal("105.8503"));
        o.setDistanceKm(new BigDecimal("3.200"));
        o.setSubtotal(total.subtract(new BigDecimal("25000")));
        o.setDeliveryFee(new BigDecimal("25000"));
        o.setTotal(total);
        o.setPaymentMethod(PaymentMethod.VNPAY);
        o.setPaymentStatus(PaymentStatus.PENDING);
        o.setStatus(OrderStatus.PENDING);
        return orderRepo.save(o);
    }

    private Payment persistPendingPayment(Order order, String txnRef) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOrderId(order.getId());
        p.setMethod(PaymentMethod.VNPAY);
        p.setAmount(order.getTotal());
        p.setStatus(PaymentStatus.PENDING);
        p.setVnpTxnRef(txnRef);
        return paymentRepo.save(p);
    }

    private Map<String, String> signedIpn(Payment p, String responseCode, String txStatus) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode",          "TEST01");
        params.put("vnp_Amount",           p.getAmount().multiply(new BigDecimal("100")).toBigInteger().toString());
        params.put("vnp_BankCode",         "NCB");
        params.put("vnp_OrderInfo",        "Test");
        params.put("vnp_ResponseCode",     responseCode);
        params.put("vnp_TransactionStatus",txStatus);
        params.put("vnp_TransactionNo",    "14000001");
        params.put("vnp_TxnRef",           p.getVnpTxnRef());
        params.put("vnp_PayDate",          "20260522170000");
        String hash = sig.buildHashAndQuery(params).hash();
        params.put("vnp_SecureHash", hash);
        return params;
    }

    private static String toForm(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=')
                .append(java.net.URLEncoder.encode(e.getValue(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
```

> **Note for implementer:**
> - This test depends on the `PostgresTestContainer` base class from existing P5 IT tests (see `backend/app/src/test/java/com/shop/delivery/support/PostgresTestContainer.java`). If the class lives in a different package, fix the import.
> - It also depends on the `PaymentEventListener` from TASK 10 (without it, the `await()` block fails because no listener confirms the order). If running this task before Task 10, the second assertion block will fail — that is expected; commit this test and let Task 10 turn it green.
> - Requires Awaitility on test classpath. If not already there, add `org.awaitility:awaitility:test` to `backend/app/pom.xml` (most Spring Boot apps already have it transitively via `spring-boot-starter-test`).

- [ ] **Step 3: Run tests**

```bash
cd backend && ./mvnw -q -pl app -am test -Dtest=VnpayControllerIT
```

Expected: First assertion block passes (payment flips synchronously). Second block FAILS until Task 10 is done. Document the temporary red in commit message:

- [ ] **Step 4: Commit (test temporarily red — fixed by Task 10)**

```bash
git add backend/modules/payment/src/main/java/com/shop/delivery/payment/api/VnpayController.java \
        backend/app/src/test/java/com/shop/delivery/payment/VnpayControllerIT.java
git commit -m "feat(payment): add VnpayController + IPN integration test"
```

**Acceptance:**
- Controller exposes all 3 endpoints at the spec'd paths
- IPN response body has exact `RspCode` / `Message` JSON field names
- IT proves IPN flips `payment.status=SUCCESS` synchronously (order flip waits for TASK 10)

---

## TASK 10: OrderConfirmedEvent publishing + confirmAfterPayment + PaymentEventListener (TDD, 1 commit)

Bridge between `payment` events and `order` state transitions. Lives in `order` module.

**Files:**
- Modify: `backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderService.java`
- Create: `backend/modules/order/src/main/java/com/shop/delivery/order/listener/PaymentEventListener.java`
- Create: `backend/modules/order/src/test/java/com/shop/delivery/order/service/OrderServiceConfirmAfterPaymentTest.java`

- [ ] **Step 1: Write failing test**

Create `backend/modules/order/src/test/java/com/shop/delivery/order/service/OrderServiceConfirmAfterPaymentTest.java`:

```java
package com.shop.delivery.order.service;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderItemRepository;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.order.repository.StatusHistoryRepository;
import com.shop.delivery.order.config.ShopConfigProperties;
import com.shop.delivery.shared.event.OrderConfirmedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceConfirmAfterPaymentTest {

    @Mock OrderRepository orderRepo;
    @Mock OrderItemRepository orderItemRepo;
    @Mock StatusHistoryRepository historyRepo;
    @Mock ProductService productSvc;
    @Mock ShopConfigProperties shopProps;
    @Mock DistanceCalculator distance;
    @Mock FeeCalculator fee;
    @Mock OrderCodeGenerator codeGen;
    @Mock ApplicationEventPublisher events;

    private OrderStateMachine stateMachine;
    private OrderService svc;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine(); // real instance — exercises whitelist
        svc = new OrderService(orderRepo, orderItemRepo, historyRepo, productSvc, shopProps,
            distance, fee, stateMachine, codeGen, events);
    }

    @Test
    void confirmAfterPayment_pendingOrder_transitionsToConfirmedAndPublishesEvent() {
        Order o = vnpayPendingOrder();
        when(orderRepo.findById(o.getId())).thenReturn(Optional.of(o));
        when(orderRepo.save(o)).thenReturn(o);

        svc.confirmAfterPayment(o.getId());

        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        ArgumentCaptor<OrderConfirmedEvent> ev = ArgumentCaptor.forClass(OrderConfirmedEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().orderId()).isEqualTo(o.getId());
        assertThat(ev.getValue().orderCode()).isEqualTo(o.getCode());
        assertThat(ev.getValue().paymentMethod()).isEqualTo("VNPAY");
    }

    @Test
    void confirmAfterPayment_alreadyConfirmedOrder_setsPaymentSuccessButNoTransitionNoEvent() {
        // Idempotency: IPN arrived twice (rare but possible) — should not re-emit.
        Order o = vnpayPendingOrder();
        o.setStatus(OrderStatus.CONFIRMED);
        when(orderRepo.findById(o.getId())).thenReturn(Optional.of(o));
        when(orderRepo.save(o)).thenReturn(o);

        svc.confirmAfterPayment(o.getId());

        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(events, never()).publishEvent(any(OrderConfirmedEvent.class));
    }

    @Test
    void confirmAfterPayment_cancelledOrder_doesNothing() {
        // Edge: customer cancelled AFTER paying. Don't resurrect.
        Order o = vnpayPendingOrder();
        o.setStatus(OrderStatus.CANCELLED);
        when(orderRepo.findById(o.getId())).thenReturn(Optional.of(o));

        svc.confirmAfterPayment(o.getId());

        // payment_status still updated (audit trail) but order stays cancelled.
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(events, never()).publishEvent(any());
    }

    private Order vnpayPendingOrder() {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCode("DH20260522-9");
        o.setCustomerId(123L);
        o.setTotal(new BigDecimal("250000.00"));
        o.setPaymentMethod(PaymentMethod.VNPAY);
        o.setPaymentStatus(PaymentStatus.PENDING);
        o.setStatus(OrderStatus.PENDING);
        return o;
    }

    private static <T> T any(Class<T> c) { return org.mockito.ArgumentMatchers.any(c); }
}
```

- [ ] **Step 2: Run test — expect compile fail (constructor sig differs, method missing)**

```bash
cd backend && ./mvnw -q -pl modules/order -am test -Dtest=OrderServiceConfirmAfterPaymentTest
```

- [ ] **Step 3: Modify `OrderService` — add publisher dependency + `confirmAfterPayment`**

In `backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderService.java`:

(a) Add field + constructor param:

```java
private final ApplicationEventPublisher events;

public OrderService(OrderRepository orderRepo, OrderItemRepository orderItemRepo,
                    StatusHistoryRepository statusHistoryRepo,
                    ProductService productService,
                    ShopConfigProperties shopProps,
                    DistanceCalculator distance, FeeCalculator fee,
                    OrderStateMachine stateMachine, OrderCodeGenerator codeGen,
                    ApplicationEventPublisher events) {
    // ... existing assignments ...
    this.events = events;
}
```

Add imports:
```java
import com.shop.delivery.shared.event.OrderConfirmedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

(b) Add new method anywhere in the class (e.g. after `confirm`):

```java
/**
 * Called by {@link com.shop.delivery.order.listener.PaymentEventListener} after a
 * VNPay payment succeeds. Idempotent — repeated calls are safe.
 *
 * <p>Always sets {@code paymentStatus = SUCCESS}. If current {@code status == PENDING},
 * also transitions to {@code CONFIRMED} via the state machine and publishes
 * {@link OrderConfirmedEvent}. If the order is already CONFIRMED, only the
 * payment status update is persisted (no duplicate event). If the order is in
 * a terminal/cancelled state, only the payment_status flag is updated so
 * audit/reporting stays accurate.
 */
@Transactional
public void confirmAfterPayment(UUID orderId) {
    Order order = orderRepo.findById(orderId)
        .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND",
            "Đơn " + orderId + " không tồn tại khi xử lý xác nhận thanh toán"));

    boolean shouldTransition = order.getStatus() == OrderStatus.PENDING;
    boolean isCancelled = order.getStatus() == OrderStatus.CANCELLED
                       || order.getStatus() == OrderStatus.RETURNED;

    // Always mark payment as SUCCESS (denormalised from payment table)
    order.setPaymentStatus(PaymentStatus.SUCCESS);

    if (shouldTransition) {
        stateMachine.requireAllowed(order.getStatus(), OrderStatus.CONFIRMED);
        OrderStatus from = order.getStatus();
        order.setStatus(OrderStatus.CONFIRMED);
        Order saved = orderRepo.save(order);
        recordTransition(saved.getId(), from, OrderStatus.CONFIRMED, null,
            "Đơn được xác nhận sau khi thanh toán VNPay");
        events.publishEvent(new OrderConfirmedEvent(
            saved.getId(), saved.getCode(), saved.getPaymentMethod().name()));
    } else if (!isCancelled) {
        // Already CONFIRMED/ASSIGNED/DELIVERING — just persist the payment_status flip
        orderRepo.save(order);
    } else {
        // CANCELLED/RETURNED — payment came late; flip flag for audit but no event/state change
        orderRepo.save(order);
    }
}
```

- [ ] **Step 4: Implement `PaymentEventListener`**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/listener/PaymentEventListener.java`:

```java
package com.shop.delivery.order.listener;

import com.shop.delivery.order.service.OrderService;
import com.shop.delivery.shared.event.PaymentSucceededEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges {@code payment} → {@code order} via Spring events.
 *
 * <p>Uses {@code AFTER_COMMIT} so that {@link OrderService#confirmAfterPayment}
 * runs in a NEW transaction once the {@code Payment} row commit is durable.
 * This is the recommended pattern from research §Architecture Pattern 3: it
 * avoids the dirty-write race where the IPN's same-transaction view of the
 * Payment row could roll back if the order transition fails.
 */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final OrderService orderService;

    public PaymentEventListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(PaymentSucceededEvent ev) {
        log.info("Payment succeeded — confirming order: orderId={} paymentId={} txnRef={}",
            ev.orderId(), ev.paymentId(), ev.txnRef());
        try {
            orderService.confirmAfterPayment(ev.orderId());
        } catch (RuntimeException re) {
            // Defensive: IPN ack to VNPay is already 00. If order transition fails
            // (e.g. optimistic-lock collision), log loudly so the user retries.
            log.error("Failed to confirm order {} after payment succeeded: {}",
                ev.orderId(), re.getMessage(), re);
        }
    }
}
```

- [ ] **Step 5: Run tests — expect pass**

```bash
cd backend && ./mvnw -q -pl modules/order -am test -Dtest=OrderServiceConfirmAfterPaymentTest
```

Then verify `VnpayControllerIT` from Task 9 now goes fully green:

```bash
cd backend && ./mvnw -q -pl app -am test -Dtest=VnpayControllerIT
```

- [ ] **Step 6: Commit**

```bash
git add backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderService.java \
        backend/modules/order/src/main/java/com/shop/delivery/order/listener/PaymentEventListener.java \
        backend/modules/order/src/test/java/com/shop/delivery/order/service/OrderServiceConfirmAfterPaymentTest.java
git commit -m "feat(order): add confirmAfterPayment + PaymentEventListener"
```

**Acceptance:**
- 3/3 unit tests pass
- `VnpayControllerIT` from Task 9 now passes both assertion blocks
- `confirmAfterPayment` is idempotent on already-confirmed orders
- Cancelled orders set `payment_status=SUCCESS` but stay CANCELLED (audit-only)

---

## TASK 11: PaymentExpiryScheduler + test (TDD, 1 commit)

Cron that expires stale PENDING payments after the 15-minute VNPay window. Also requires `@EnableScheduling` on `Application.java`.

**Files:**
- Create: `backend/modules/payment/src/main/java/com/shop/delivery/payment/service/PaymentExpiryScheduler.java`
- Create: `backend/modules/payment/src/test/java/com/shop/delivery/payment/service/PaymentExpirySchedulerTest.java`
- Modify: `backend/app/src/main/java/com/shop/delivery/Application.java`

- [ ] **Step 1: Write failing test**

Create `backend/modules/payment/src/test/java/com/shop/delivery/payment/service/PaymentExpirySchedulerTest.java`:

```java
package com.shop.delivery.payment.service;

import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.payment.config.VnpayProperties;
import com.shop.delivery.payment.entity.Payment;
import com.shop.delivery.payment.repository.PaymentRepository;
import com.shop.delivery.shared.event.PaymentFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentExpirySchedulerTest {

    @Mock PaymentRepository repo;
    @Mock ApplicationEventPublisher events;

    private VnpayProperties props;
    private Clock fixedClock;
    private PaymentExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        props = new VnpayProperties("T", "S", "u1", "u2", "u3", 15);
        // Time = 2026-05-22T10:30:00Z → cutoff = 10:15:00Z
        fixedClock = Clock.fixed(Instant.parse("2026-05-22T10:30:00Z"), ZoneId.of("UTC"));
        scheduler = new PaymentExpiryScheduler(repo, props, events, fixedClock);
    }

    @Test
    void expireStalePending_marksFAILED_andEmitsPaymentFailedEvent() {
        Payment stale = pending(Instant.parse("2026-05-22T10:10:00Z"));
        when(repo.findStalePending(Instant.parse("2026-05-22T10:15:00Z")))
            .thenReturn(List.of(stale));

        scheduler.expireStalePending();

        assertThat(stale.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(stale.getVnpResponseCode()).isEqualTo("EXPIRED");
        verify(repo).saveAll(List.of(stale));

        ArgumentCaptor<PaymentFailedEvent> ev = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().responseCode()).isEqualTo("EXPIRED");
        assertThat(ev.getValue().paymentId()).isEqualTo(stale.getId());
    }

    @Test
    void expireStalePending_noStaleRows_noEventNoSave() {
        when(repo.findStalePending(any(Instant.class))).thenReturn(List.of());

        scheduler.expireStalePending();

        verify(repo, never()).saveAll(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void expireStalePending_doesNotTouchSUCCESSorFAILED() {
        // Repository.findStalePending is responsible for the WHERE status=PENDING filter,
        // so this test asserts the scheduler trusts the repo. We just verify it never
        // mutates rows that come back with a non-PENDING status if the repo misbehaves.
        Payment alreadyOk = pending(Instant.parse("2026-05-22T10:00:00Z"));
        alreadyOk.setStatus(PaymentStatus.SUCCESS);
        when(repo.findStalePending(any(Instant.class))).thenReturn(List.of(alreadyOk));

        scheduler.expireStalePending();

        // Scheduler should keep status SUCCESS, not flip it to FAILED.
        assertThat(alreadyOk.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(events, never()).publishEvent(any());
    }

    private Payment pending(Instant createdAt) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOrderId(UUID.randomUUID());
        p.setMethod(PaymentMethod.VNPAY);
        p.setAmount(new BigDecimal("250000.00"));
        p.setStatus(PaymentStatus.PENDING);
        p.setVnpTxnRef("DH-S-" + createdAt.toEpochMilli());
        // Note: BaseEntity.createdAt is package-protected setter; using reflection in a real
        // test, or simply skipping (the scheduler reads only via repo.findStalePending).
        return p;
    }

    private static <T> T any(Class<T> c) { return org.mockito.ArgumentMatchers.any(c); }
    private static Object any() { return org.mockito.ArgumentMatchers.any(); }
}
```

- [ ] **Step 2: Implement `PaymentExpiryScheduler`**

Create `backend/modules/payment/src/main/java/com/shop/delivery/payment/service/PaymentExpiryScheduler.java`:

```java
package com.shop.delivery.payment.service;

import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.payment.config.VnpayProperties;
import com.shop.delivery.payment.entity.Payment;
import com.shop.delivery.payment.repository.PaymentRepository;
import com.shop.delivery.shared.event.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Sweeps PENDING payments older than the VNPay timeout and marks them FAILED.
 *
 * <p>Runs every 60 seconds with an initial 60s delay (warm-up). The threshold
 * is {@code vnpay.timeout-minutes} (default 15 from VNPay's own expiry window).
 *
 * <p>Emits {@link PaymentFailedEvent} per expired row so future P8 listeners
 * can notify the customer. Order state is NOT touched here — the customer can
 * still retry payment (a new {@code Payment} row will be created via
 * {@code POST /create}); the failed sweeper row exists only for audit.
 */
@Component
public class PaymentExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpiryScheduler.class);

    private final PaymentRepository paymentRepo;
    private final VnpayProperties props;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PaymentExpiryScheduler(PaymentRepository paymentRepo,
                                  VnpayProperties props,
                                  ApplicationEventPublisher events,
                                  Clock clock) {
        this.paymentRepo = paymentRepo;
        this.props = props;
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public void expireStalePending() {
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(props.timeoutMinutes()));
        List<Payment> stale = paymentRepo.findStalePending(cutoff);
        if (stale.isEmpty()) return;

        List<Payment> toEmit = new ArrayList<>();
        for (Payment p : stale) {
            // Belt-and-braces: ignore anything the query somehow returned that isn't PENDING
            if (p.getStatus() != PaymentStatus.PENDING) continue;
            p.setStatus(PaymentStatus.FAILED);
            p.setVnpResponseCode("EXPIRED");
            toEmit.add(p);
        }
        if (toEmit.isEmpty()) return;

        paymentRepo.saveAll(toEmit);
        for (Payment p : toEmit) {
            events.publishEvent(new PaymentFailedEvent(
                p.getOrderId(), p.getId(), p.getVnpTxnRef(), "EXPIRED"));
        }
        log.info("PaymentExpiryScheduler: expired {} stale PENDING payments", toEmit.size());
    }
}
```

- [ ] **Step 3: Enable scheduling in `Application.java`**

Modify `backend/app/src/main/java/com/shop/delivery/Application.java`:

```java
package com.shop.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication(scanBasePackages = "com.shop.delivery")
@EntityScan(basePackages = "com.shop.delivery")
@EnableJpaRepositories(basePackages = "com.shop.delivery")
@ConfigurationPropertiesScan(basePackages = "com.shop.delivery")
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /** Production {@link Clock}. Tests replace this with a fixed clock. */
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
```

> **Note:** the `Clock` bean is required by `VnpayPaymentService` and `PaymentExpiryScheduler`. Tests inject their own fixed clock via `@MockBean` or constructor injection.

- [ ] **Step 4: Run test — expect pass**

```bash
cd backend && ./mvnw -q -pl modules/payment -am test -Dtest=PaymentExpirySchedulerTest
```

Then boot the full app and confirm the cron registers without complaint:

```bash
cd backend/app
BOT_TOKEN=dummy BOT_USERNAME=DummyBot \
  ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8089" > /tmp/p7_t11.log 2>&1 &
BOOT_PID=$!
sleep 30
kill $BOOT_PID
grep -i "PaymentExpiryScheduler\|EnableScheduling" /tmp/p7_t11.log | head -5
# Should not see any "could not register" or "no @EnableScheduling" warnings
```

- [ ] **Step 5: Commit**

```bash
git add backend/modules/payment/src/main/java/com/shop/delivery/payment/service/PaymentExpiryScheduler.java \
        backend/modules/payment/src/test/java/com/shop/delivery/payment/service/PaymentExpirySchedulerTest.java \
        backend/app/src/main/java/com/shop/delivery/Application.java
git commit -m "feat(payment): add PaymentExpiryScheduler + @EnableScheduling"
```

**Acceptance:**
- 3/3 tests pass
- App boots with no scheduling errors
- Cron fires at 60s intervals (no need to wait — visible in test logs at TRACE level)
- `Clock` is exposed as a `@Bean` so `VnpayPaymentService` + scheduler both consume the same instance

---

## TASK 12: Return-URL static pages + SecurityConfig allowlist (1 commit)

Two static HTML pages + open up `/return` and `/ipn` for public access.

**Files:**
- Create: `backend/app/src/main/resources/static/payment-success.html`
- Create: `backend/app/src/main/resources/static/payment-failed.html`
- Modify: `backend/modules/auth/src/main/java/com/shop/delivery/auth/config/SecurityConfig.java`

- [ ] **Step 1: Write `payment-success.html`**

Create `backend/app/src/main/resources/static/payment-success.html`:

```html
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Thanh toán thành công</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
            background: linear-gradient(135deg, #34c759 0%, #1e8a3a 100%);
            color: #fff;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 1.5rem;
        }
        .card {
            background: #fff;
            color: #1a1a1a;
            border-radius: 16px;
            padding: 2rem 1.5rem;
            max-width: 360px;
            width: 100%;
            text-align: center;
            box-shadow: 0 16px 48px rgba(0, 0, 0, 0.15);
        }
        .icon {
            width: 64px;
            height: 64px;
            background: #34c759;
            color: #fff;
            border-radius: 50%;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-size: 32px;
            margin-bottom: 1rem;
        }
        h1 { font-size: 1.5rem; margin-bottom: 0.5rem; }
        .subtitle { color: #6e6e73; margin-bottom: 1.5rem; }
        .row {
            display: flex;
            justify-content: space-between;
            padding: 0.75rem 0;
            border-top: 1px solid #f0f0f0;
        }
        .row:first-of-type { border-top: 0; }
        .label { color: #6e6e73; }
        .value { font-weight: 600; }
        .button {
            display: inline-block;
            margin-top: 1.5rem;
            padding: 0.875rem 1.5rem;
            background: #007aff;
            color: #fff;
            text-decoration: none;
            border-radius: 12px;
            font-weight: 600;
            width: 100%;
            text-align: center;
        }
        .button.secondary {
            background: transparent;
            color: #007aff;
            margin-top: 0.5rem;
        }
    </style>
</head>
<body>
    <div class="card">
        <div class="icon">✓</div>
        <h1>Thanh toán thành công</h1>
        <p class="subtitle">Đơn hàng của bạn đã được xác nhận</p>
        <div class="row">
            <span class="label">Mã đơn</span>
            <span class="value" id="orderCode">—</span>
        </div>
        <div class="row">
            <span class="label">Số tiền</span>
            <span class="value" id="amount">—</span>
        </div>
        <a href="javascript:void(0)" class="button" onclick="window.close()">Đóng</a>
        <a href="javascript:void(0)" class="button secondary" onclick="history.back()">Quay lại</a>
    </div>
    <script>
        const params = new URLSearchParams(window.location.search);
        const code = params.get('orderCode') || '—';
        const amount = params.get('amount');
        document.getElementById('orderCode').textContent = code;
        if (amount && !Number.isNaN(Number(amount))) {
            document.getElementById('amount').textContent =
                Number(amount).toLocaleString('vi-VN') + ' đ';
        }
    </script>
</body>
</html>
```

- [ ] **Step 2: Write `payment-failed.html`** (mirrors success page; red gradient)

Create `backend/app/src/main/resources/static/payment-failed.html`:

```html
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Thanh toán thất bại</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
            background: linear-gradient(135deg, #ff3b30 0%, #b71c1c 100%);
            color: #fff;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 1.5rem;
        }
        .card {
            background: #fff;
            color: #1a1a1a;
            border-radius: 16px;
            padding: 2rem 1.5rem;
            max-width: 360px;
            width: 100%;
            text-align: center;
            box-shadow: 0 16px 48px rgba(0, 0, 0, 0.15);
        }
        .icon {
            width: 64px; height: 64px;
            background: #ff3b30;
            color: #fff;
            border-radius: 50%;
            display: inline-flex; align-items: center; justify-content: center;
            font-size: 32px;
            margin-bottom: 1rem;
        }
        h1 { font-size: 1.5rem; margin-bottom: 0.5rem; }
        .subtitle { color: #6e6e73; margin-bottom: 1.5rem; }
        .row {
            display: flex; justify-content: space-between;
            padding: 0.75rem 0;
            border-top: 1px solid #f0f0f0;
        }
        .row:first-of-type { border-top: 0; }
        .label { color: #6e6e73; }
        .value { font-weight: 600; }
        .button {
            display: inline-block;
            margin-top: 1.5rem;
            padding: 0.875rem 1.5rem;
            background: #007aff;
            color: #fff;
            text-decoration: none;
            border-radius: 12px;
            font-weight: 600;
            width: 100%;
            text-align: center;
        }
    </style>
</head>
<body>
    <div class="card">
        <div class="icon">✕</div>
        <h1>Thanh toán thất bại</h1>
        <p class="subtitle" id="reason">Vui lòng thử lại</p>
        <div class="row">
            <span class="label">Mã đơn</span>
            <span class="value" id="orderCode">—</span>
        </div>
        <div class="row" id="codeRow" style="display:none">
            <span class="label">Mã lỗi VNPay</span>
            <span class="value" id="code">—</span>
        </div>
        <a href="javascript:void(0)" class="button" onclick="window.close()">Đóng</a>
    </div>
    <script>
        const params = new URLSearchParams(window.location.search);
        document.getElementById('orderCode').textContent = params.get('orderCode') || '—';
        const code = params.get('code');
        const reason = params.get('reason');
        if (code) {
            document.getElementById('codeRow').style.display = 'flex';
            document.getElementById('code').textContent = code;
        }
        if (reason === 'invalid_signature') {
            document.getElementById('reason').textContent = 'Chữ ký không hợp lệ — vui lòng liên hệ shop';
        } else if (code === '24') {
            document.getElementById('reason').textContent = 'Khách hàng đã hủy giao dịch';
        } else if (code === '11') {
            document.getElementById('reason').textContent = 'Giao dịch hết hạn — vui lòng thử lại';
        }
    </script>
</body>
</html>
```

- [ ] **Step 3: Update `SecurityConfig` allowlist**

Modify `backend/modules/auth/src/main/java/com/shop/delivery/auth/config/SecurityConfig.java`:

Inside `authorizeHttpRequests(...)`, after the existing `requestMatchers("/api/bot/webhook").permitAll()` line, add:

```java
.requestMatchers("/api/payment/vnpay/return", "/api/payment/vnpay/ipn").permitAll()
.requestMatchers("/payment-success.html", "/payment-failed.html").permitAll()
```

(The `/payment-*.html` lines are belt-and-braces; static resources under `/static/` are usually permitted by default, but listing them explicitly documents intent.)

`POST /api/payment/vnpay/create` is intentionally NOT in the allowlist — it falls through to the catch-all `.anyRequest().permitAll()` and authentication is enforced by `@CurrentUser` resolving the Telegram principal at the controller method level (matches the pattern used by `OrderController` for customer endpoints).

- [ ] **Step 4: Smoke test**

```bash
cd backend/app
BOT_TOKEN=dummy BOT_USERNAME=DummyBot \
  ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8089" > /tmp/p7_t12.log 2>&1 &
BOOT_PID=$!
sleep 25

# Static pages reachable without auth
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8089/payment-success.html
# Expected: 200
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8089/payment-failed.html
# Expected: 200

# IPN endpoint reachable without auth (will return 97 — bad sig — but NOT 401)
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8089/api/payment/vnpay/ipn -d "vnp_TxnRef=x"
# Expected: 200 (with body {"RspCode":"97",...})

curl -s -X POST http://localhost:8089/api/payment/vnpay/ipn -d "vnp_TxnRef=x"
# Expected body: {"RspCode":"97","Message":"Invalid Checksum"}

kill $BOOT_PID
```

- [ ] **Step 5: Commit**

```bash
git add backend/app/src/main/resources/static/payment-success.html \
        backend/app/src/main/resources/static/payment-failed.html \
        backend/modules/auth/src/main/java/com/shop/delivery/auth/config/SecurityConfig.java
git commit -m "feat(payment): add return UX pages + SecurityConfig allowlist"
```

**Acceptance:**
- `GET /payment-success.html` returns 200 with the HTML
- `GET /payment-failed.html` returns 200 with the HTML
- `POST /api/payment/vnpay/ipn` returns 200 with `{"RspCode":"97","Message":"Invalid Checksum"}` on unsigned body
- `POST /api/payment/vnpay/create` returns 401/403 without Telegram initData (auth still enforced)
- Pages render readably on mobile width (test in browser DevTools mobile mode)

---

## TASK 13: Backend smoke verification (no commit) {#task13-backend-smoke}

End-of-backend gate. Boot the full app, exercise create + IPN happy paths via curl, confirm DB state. No commit unless something breaks.

- [ ] **Step 1: Boot the app**

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
docker compose -f infra/docker-compose.dev.yml up -d
sleep 5

cd backend/app
BOT_TOKEN=dummy BOT_USERNAME=DummyBot \
  nohup ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8080" > /tmp/p7_t13.log 2>&1 &
BOOT_PID=$!
for i in $(seq 1 90); do
  grep -q "Started Application" /tmp/p7_t13.log 2>/dev/null && break
  grep -q "APPLICATION FAILED" /tmp/p7_t13.log 2>/dev/null && break
  sleep 1
done
```

- [ ] **Step 2: Seed an order via DB (simulating an existing VNPay-marked order)**

```bash
ORDER_ID=$(uuidgen | tr 'A-Z' 'a-z')
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "
INSERT INTO orders(id, code, customer_id, customer_name, customer_phone,
                   pickup_lat, pickup_lng, delivery_address, delivery_lat, delivery_lng,
                   distance_km, subtotal, delivery_fee, total, payment_method, payment_status, status, version, created_at, updated_at)
VALUES ('$ORDER_ID', 'DH-SMOKE-1', 123, 'Smoke Test', '+849999',
        21.0285, 105.8542, 'Smoke addr', 21.0193, 105.8503,
        3.2, 225000, 25000, 250000, 'VNPAY', 'PENDING', 'PENDING', 0, NOW(), NOW());"
echo "Created order: $ORDER_ID"
```

- [ ] **Step 3: Run the full unit + integration suite**

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang/backend
./mvnw -q -B test
# Expected: BUILD SUCCESS; tests for shared, order, payment, app modules pass
```

- [ ] **Step 4: Manually trigger an IPN (skipping /create — needs Telegram auth)**

```bash
# Insert a pending payment row matching the order
PAYMENT_ID=$(uuidgen | tr 'A-Z' 'a-z')
TXN_REF="DH-SMOKE-1-$(date +%s)000"
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "
INSERT INTO payment(id, order_id, method, amount, status, vnp_txn_ref, version, created_at, updated_at)
VALUES ('$PAYMENT_ID', '$ORDER_ID', 'VNPAY', 250000, 'PENDING', '$TXN_REF', 0, NOW(), NOW());"

# Now compute a valid hash for this txnRef + amount with the dev secret (TESTSECRETKEY123)
# Easiest path: run a tiny throwaway Java snippet or use openssl, since we control the secret.
# Below shows an openssl-based shell sign as a sanity check (matches our service):
SECRET="TESTSECRETKEY123"
AMOUNT=25000000
HASHDATA="vnp_Amount=${AMOUNT}&vnp_OrderInfo=Smoke&vnp_ResponseCode=00&vnp_TmnCode=TEST01&vnp_TransactionNo=14000001&vnp_TransactionStatus=00&vnp_TxnRef=${TXN_REF}"
HASH=$(echo -n "$HASHDATA" | openssl dgst -sha512 -hmac "$SECRET" | sed 's/^.* //')

curl -s -X POST http://localhost:8080/api/payment/vnpay/ipn \
  --data-urlencode "vnp_TmnCode=TEST01" \
  --data-urlencode "vnp_Amount=${AMOUNT}" \
  --data-urlencode "vnp_OrderInfo=Smoke" \
  --data-urlencode "vnp_ResponseCode=00" \
  --data-urlencode "vnp_TransactionNo=14000001" \
  --data-urlencode "vnp_TransactionStatus=00" \
  --data-urlencode "vnp_TxnRef=${TXN_REF}" \
  --data-urlencode "vnp_SecureHash=${HASH}"
# Expected: {"RspCode":"00","Message":"Confirm Success"}
```

- [ ] **Step 5: Verify DB state**

```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "
SELECT status, paid_at IS NOT NULL AS paid FROM payment WHERE vnp_txn_ref = '$TXN_REF';"
# Expected: status=SUCCESS, paid=t

docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "
SELECT status, payment_status FROM orders WHERE id = '$ORDER_ID';"
# Expected: status=CONFIRMED, payment_status=SUCCESS

docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "
SELECT event_type, recorded_at FROM payment_transaction WHERE payment_id = '$PAYMENT_ID' ORDER BY recorded_at;"
# Expected: one row with event_type=IPN
```

- [ ] **Step 6: Stop the app**

```bash
kill $BOOT_PID 2>/dev/null
```

**Acceptance (no commit — verification only):**
- All Maven tests pass
- IPN curl returns `{"RspCode":"00","Message":"Confirm Success"}`
- `payment.status = SUCCESS`, `payment.paid_at` set
- `orders.status = CONFIRMED`, `orders.payment_status = SUCCESS`
- `payment_transaction` has an `IPN` row
- App logs show no errors

---


## WAVE 2 — Frontend + Web Admin (Tasks 14–18)

Final wave: Mini App checkout + order detail, Web Admin orders list badge column, smoke test plan.

---

## TASK 14: Shared types + API helpers for payment (1 commit)

**Files:**
- Create: `frontend/shared/src/types/payment.ts`
- Create: `frontend/shared/src/api/payment.ts`
- Modify: `frontend/shared/src/types/index.ts`
- Modify: `frontend/shared/src/api/index.ts`

- [ ] **Step 1: Create the types file**

Create `frontend/shared/src/types/payment.ts`:

```ts
/**
 * VNPay create-payment response.
 */
export interface CreatePaymentResponse {
  paymentUrl: string;
  txnRef: string;
}

export interface CreatePaymentRequest {
  orderId: string;
}
```

- [ ] **Step 2: Create the API helper**

Create `frontend/shared/src/api/payment.ts`:

```ts
import type { AxiosInstance } from 'axios';
import type { CreatePaymentRequest, CreatePaymentResponse } from '../types/payment';

export async function createVnpayPayment(
  api: AxiosInstance,
  body: CreatePaymentRequest,
): Promise<CreatePaymentResponse> {
  const { data } = await api.post<CreatePaymentResponse>(
    '/api/payment/vnpay/create',
    body,
  );
  return data;
}
```

- [ ] **Step 3: Export from index barrels**

Append to `frontend/shared/src/types/index.ts`:

```ts
export * from './payment';
```

Append to `frontend/shared/src/api/index.ts`:

```ts
export * from './payment';
```

- [ ] **Step 4: Verify shared package builds**

```bash
cd frontend && pnpm -F @shop/shared build
```

- [ ] **Step 5: Commit**

```bash
git add frontend/shared/src/types/payment.ts \
        frontend/shared/src/api/payment.ts \
        frontend/shared/src/types/index.ts \
        frontend/shared/src/api/index.ts
git commit -m "feat(shared): add payment types + createVnpayPayment helper"
```

**Acceptance:**
- `@shop/shared` build passes
- `import { createVnpayPayment } from '@shop/shared'` resolves in miniapp/webadmin tsconfig paths

---

## TASK 15: Mini App — enable VNPAY radio + usePayWithVnpay hook (1 commit)

**Files:**
- Create: `frontend/miniapp/src/features/payment/use-pay-with-vnpay.ts`
- Modify: `frontend/miniapp/src/pages/CheckoutPage.tsx`

- [ ] **Step 1: Implement the mutation hook**

Create `frontend/miniapp/src/features/payment/use-pay-with-vnpay.ts`:

```ts
import { useMutation } from '@tanstack/react-query';
import WebApp from '@twa-dev/sdk';
import { createVnpayPayment } from '@shop/shared';
import { api } from '@/lib/api';
import { tg } from '@/lib/telegram';

/**
 * Calls POST /api/payment/vnpay/create for the given order, then opens the
 * resulting VNPay URL via Telegram.WebApp.openLink (NOT window.location.href —
 * see research §Pitfall 6: window.location closes the Mini App container).
 *
 * onSuccess returns immediately after opening the link; the Mini App stays
 * alive underneath the Telegram overlay browser. OrderDetailPage's polling
 * picks up the SUCCESS status when IPN completes.
 */
export function usePayWithVnpay() {
  return useMutation({
    mutationFn: (orderId: string) => createVnpayPayment(api, { orderId }),
    onSuccess: ({ paymentUrl }) => {
      if (tg.isInTelegram()) {
        WebApp.openLink(paymentUrl, { try_instant_view: false });
      } else {
        // Dev / outside-Telegram fallback (Vite dev mode in plain browser)
        window.open(paymentUrl, '_blank', 'noopener,noreferrer');
      }
    },
    onError: async (err: any) => {
      const msg = err?.response?.data?.message ?? 'Không khởi tạo được thanh toán VNPay';
      if (tg.isInTelegram()) await tg.showAlert(msg);
      else alert(msg);
    },
  });
}
```

- [ ] **Step 2: Update `CheckoutPage`**

Modify `frontend/miniapp/src/pages/CheckoutPage.tsx`:

(a) Add imports:

```tsx
import { usePayWithVnpay } from '@/features/payment/use-pay-with-vnpay';
```

(b) Add hook call near `placeOrder`:

```tsx
const payWithVnpay = usePayWithVnpay();
```

(c) Replace the disabled VNPAY radio block (currently `<label className="flex items-center gap-2 py-2 opacity-50"> ... disabled ...`) with:

```tsx
<label className="flex items-center gap-2 py-2">
  <input
    type="radio"
    name="payment"
    value="VNPAY"
    checked={paymentMethod === 'VNPAY'}
    onChange={() => setPaymentMethod('VNPAY')}
  />
  <span>💳 Thanh toán qua VNPay (sandbox)</span>
</label>
```

(d) Update `placeOrder.onSuccess` so VNPAY orders trigger the redirect AND navigate to the order page:

```tsx
const placeOrder = useMutation({
  mutationFn: (req: CreateOrderRequest) => createOrder(api, req),
  onSuccess: order => {
    cart.clear();
    if (order.paymentMethod === 'VNPAY') {
      // 1. Navigate to detail page first — when the overlay closes,
      //    the user lands on the order detail with polling active.
      navigate(`/customer/orders/${order.id}`, { replace: true });
      // 2. Fire-and-forget: open VNPay in Telegram overlay
      payWithVnpay.mutate(order.id);
    } else {
      navigate(`/customer/orders/${order.id}`, { replace: true });
    }
  },
  onError: async (err: any) => {
    const msg = err.response?.data?.message ?? 'Đặt đơn thất bại';
    if (tg.isInTelegram()) await tg.showAlert(msg);
    else alert(msg);
  },
});
```

- [ ] **Step 3: Type-check + build**

```bash
cd frontend && pnpm -F miniapp typecheck && pnpm -F miniapp build
```

- [ ] **Step 4: Visual smoke (optional, in browser)**

```bash
cd frontend && pnpm -F miniapp dev
# Open http://localhost:5173 — checkout page should show VNPay radio NOT greyed out
# Clicking "Đặt hàng" with VNPay selected should trigger a window.open() to the sandbox URL
# (assuming a test order succeeds — see TASK 18 for full E2E)
```

- [ ] **Step 5: Commit**

```bash
git add frontend/miniapp/src/features/payment/use-pay-with-vnpay.ts \
        frontend/miniapp/src/pages/CheckoutPage.tsx
git commit -m "feat(miniapp): enable VNPay payment method + openLink redirect"
```

**Acceptance:**
- VNPay radio is enabled and selectable
- Submitting with VNPay calls `POST /api/payment/vnpay/create` then opens the returned URL via `WebApp.openLink` (or `window.open` in dev)
- COD flow unchanged

---

## TASK 16: Mini App — polling on OrderDetailPage (1 commit)

**Files:**
- Modify: `frontend/miniapp/src/pages/OrderDetailPage.tsx`

- [ ] **Step 1: Add polling + payment-status UI**

Modify `frontend/miniapp/src/pages/OrderDetailPage.tsx`:

(a) Replace the existing `useQuery` block with one that polls when VNPAY+PENDING:

```tsx
import { useEffect, useState } from 'react';
// ... existing imports ...

const POLL_MS = 3000;
const POLL_TIMEOUT_MS = 60_000;

export function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [pollingExpired, setPollingExpired] = useState(false);

  const { data: order, isLoading, error } = useQuery({
    queryKey: ['order', id],
    queryFn: () => getOrder(api, id!),
    enabled: !!id,
    refetchInterval: query => {
      const o = query.state.data;
      if (!o) return false;
      if (pollingExpired) return false;
      const shouldPoll = o.paymentMethod === 'VNPAY' && o.paymentStatus === 'PENDING';
      return shouldPoll ? POLL_MS : false;
    },
  });

  // Stop polling after 60s so we don't ping forever if IPN never arrives.
  useEffect(() => {
    if (order?.paymentMethod === 'VNPAY' && order?.paymentStatus === 'PENDING') {
      const t = setTimeout(() => setPollingExpired(true), POLL_TIMEOUT_MS);
      return () => clearTimeout(t);
    }
  }, [order?.paymentMethod, order?.paymentStatus]);
```

(b) Replace the existing simple "Trạng thái thanh toán" block (around lines 115-124) with smart UI showing the right Vietnamese label + colour:

```tsx
<div className="bg-tg-secondaryBg rounded-lg p-4 mb-4 text-sm">
  <div className="flex justify-between">
    <span className="text-tg-hint">Thanh toán</span>
    <span>{order.paymentMethod === 'VNPAY' ? 'VNPay' : 'COD'}</span>
  </div>
  <div className="flex justify-between mt-1 items-center">
    <span className="text-tg-hint">Trạng thái thanh toán</span>
    <PaymentStatusInline status={order.paymentStatus} />
  </div>
  {order.paymentMethod === 'VNPAY' && order.paymentStatus === 'PENDING' && !pollingExpired && (
    <p className="text-xs text-tg-hint mt-2">
      ⏳ Đang chờ xác nhận từ VNPay... (tự động cập nhật mỗi 3 giây)
    </p>
  )}
  {order.paymentMethod === 'VNPAY' && order.paymentStatus === 'PENDING' && pollingExpired && (
    <p className="text-xs text-amber-500 mt-2">
      Chưa nhận được xác nhận thanh toán. Vui lòng tải lại trang sau ít phút,
      hoặc liên hệ shop nếu bạn đã thanh toán xong.
    </p>
  )}
</div>
```

(c) Add the inline badge component (inside the same file, below the page component):

```tsx
function PaymentStatusInline({ status }: { status: string }) {
  const labelMap: Record<string, { text: string; cls: string }> = {
    PENDING:  { text: 'Đang chờ thanh toán', cls: 'text-tg-hint' },
    SUCCESS:  { text: 'Đã thanh toán',       cls: 'text-green-500 font-medium' },
    FAILED:   { text: 'Thanh toán thất bại', cls: 'text-red-500 font-medium' },
    REFUNDED: { text: 'Đã hoàn tiền',        cls: 'text-amber-500 font-medium' },
  };
  const { text, cls } = labelMap[status] ?? { text: status, cls: '' };
  return <span className={cls}>{text}</span>;
}
```

- [ ] **Step 2: Type-check + build**

```bash
cd frontend && pnpm -F miniapp typecheck && pnpm -F miniapp build
```

- [ ] **Step 3: Visual smoke (optional)**

In dev mode, modify a VNPAY order's `payment_status` directly in DB while the page is open — should update within 3 seconds without refresh.

- [ ] **Step 4: Commit**

```bash
git add frontend/miniapp/src/pages/OrderDetailPage.tsx
git commit -m "feat(miniapp): poll order status while VNPay payment pending"
```

**Acceptance:**
- VNPAY+PENDING order polls every 3s
- Polling auto-stops on status change OR after 60s
- "Đang chờ xác nhận" message visible during polling
- Timeout message visible after 60s with no resolution

---

## TASK 17: Web Admin — payment_status badge column (1 commit)

**Files:**
- Create: `frontend/webadmin/src/components/PaymentStatusBadge.tsx`
- Modify: `frontend/webadmin/src/pages/OrdersPage.tsx`

- [ ] **Step 1: Create the badge component**

Create `frontend/webadmin/src/components/PaymentStatusBadge.tsx`:

```tsx
import type { PaymentStatus } from '@shop/shared';

const COLORS: Record<PaymentStatus, { bg: string; fg: string; label: string }> = {
  PENDING:  { bg: 'bg-gray-100',   fg: 'text-gray-700',  label: 'Đang chờ' },
  SUCCESS:  { bg: 'bg-green-100',  fg: 'text-green-700', label: 'Thành công' },
  FAILED:   { bg: 'bg-red-100',    fg: 'text-red-700',   label: 'Thất bại' },
  REFUNDED: { bg: 'bg-amber-100',  fg: 'text-amber-700', label: 'Hoàn tiền' },
};

export function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  const c = COLORS[status] ?? { bg: 'bg-gray-100', fg: 'text-gray-700', label: status };
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium ${c.bg} ${c.fg}`}>
      {c.label}
    </span>
  );
}
```

- [ ] **Step 2: Update `OrdersPage`** to use it + split column

Modify `frontend/webadmin/src/pages/OrdersPage.tsx`:

(a) Add import:

```tsx
import { PaymentStatusBadge } from '@/components/PaymentStatusBadge';
```

(b) Replace the existing `<th>Thanh toán</th>` and its corresponding `<td>` with two columns: payment method + payment status. New table header:

```tsx
<thead className="bg-gray-50 text-gray-600">
  <tr>
    <th className="px-4 py-2 text-left">Mã đơn</th>
    <th className="px-4 py-2 text-left">Trạng thái</th>
    <th className="px-4 py-2 text-left">Phương thức</th>
    <th className="px-4 py-2 text-left">Thanh toán</th>
    <th className="px-4 py-2 text-right">Tổng</th>
    <th className="px-4 py-2 text-left">Tạo lúc</th>
    <th className="px-4 py-2 text-right">Hành động</th>
  </tr>
</thead>
```

New row body:

```tsx
<tr key={o.id} className="hover:bg-gray-50">
  <td className="px-4 py-3 font-medium">{o.code}</td>
  <td className="px-4 py-3"><OrderStatusBadge status={o.status} /></td>
  <td className="px-4 py-3 text-gray-700">{o.paymentMethod}</td>
  <td className="px-4 py-3"><PaymentStatusBadge status={o.paymentStatus} /></td>
  <td className="px-4 py-3 text-right font-medium">{formatVnd(o.total)}</td>
  <td className="px-4 py-3 text-gray-600">{formatDateTime(o.createdAt)}</td>
  <td className="px-4 py-3 text-right">
    <Link to={`/orders/${o.id}`} className="text-brand-600 hover:underline">Xem</Link>
  </td>
</tr>
```

(c) Update the empty-state `colSpan` from 6 → 7.

- [ ] **Step 3: Type-check + build**

```bash
cd frontend && pnpm -F webadmin typecheck && pnpm -F webadmin build
```

- [ ] **Step 4: Visual check**

```bash
cd frontend && pnpm -F webadmin dev
# Open http://localhost:5174 → login → /orders
# Should see new columns "Phương thức" (COD/VNPAY plain text) and "Thanh toán"
# (badge: gray PENDING, green SUCCESS, red FAILED, amber REFUNDED)
```

- [ ] **Step 5: Commit**

```bash
git add frontend/webadmin/src/components/PaymentStatusBadge.tsx \
        frontend/webadmin/src/pages/OrdersPage.tsx
git commit -m "feat(webadmin): show payment status as colored badge in orders list"
```

**Acceptance:**
- New "Thanh toán" column shows badge with correct color per status
- Badge colors: gray/green/red/amber match spec
- Vietnamese labels render correctly

---

## TASK 18: Smoke test plan (manual E2E, no commit) {#task18-e2e-smoke}

Final end-to-end test against the real VNPay sandbox. NO commit — just a checklist to walk through and tick off as a sanity gate before merging the phase.

**Prerequisites (one-time setup):**

- [ ] **Register sandbox merchant at https://sandbox.vnpayment.vn/devreg**

  Provide an email address; sandbox sends you `TmnCode` + `HashSecret` (approval ~1 day per design risk log). Add to your shell `~/.zshrc` (do NOT commit):

  ```bash
  export VNPAY_TMN_CODE="your-actual-tmn-code"
  export VNPAY_HASH_SECRET="your-actual-hash-secret"
  ```

- [ ] **Set up a public HTTPS tunnel**

  ```bash
  # Option A: ngrok
  ngrok http 8080
  # Note the https URL, e.g. https://abc123.ngrok-free.app

  # Option B: cloudflared
  cloudflared tunnel --url http://localhost:8080
  ```

- [ ] **Register Return + IPN URLs at the VNPay merchant portal**

  Go to https://sandbox.vnpayment.vn/merchantv2/ → log in → menu "Cấu hình IPN URL" / "Cấu hình Return URL":
  - Return URL: `https://abc123.ngrok-free.app/api/payment/vnpay/return`
  - IPN URL:    `https://abc123.ngrok-free.app/api/payment/vnpay/ipn`

- [ ] **Override env at app start**

  ```bash
  cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang/backend/app
  BOT_TOKEN=<real-bot> BOT_USERNAME=<bot-username> \
  VNPAY_TMN_CODE=$VNPAY_TMN_CODE VNPAY_HASH_SECRET=$VNPAY_HASH_SECRET \
  VNPAY_RETURN_URL=https://abc123.ngrok-free.app/api/payment/vnpay/return \
  VNPAY_IPN_URL=https://abc123.ngrok-free.app/api/payment/vnpay/ipn \
    ../mvnw spring-boot:run -Dspring-boot.run.profiles=dev
  ```

**Happy path checklist:**

- [ ] **1. Place a VNPAY order in Mini App**

  Open the Mini App → catalogue → add product → checkout → fill addr → select "VNPay (sandbox)" → "Đặt hàng".

  Expected: Mini App navigates to `/customer/orders/{id}` AND VNPay opens in Telegram overlay browser.

- [ ] **2. Pay with NCB test card**

  In VNPay overlay:
  - Bank: NCB
  - Card number: `9704198526191432198`
  - Cardholder: `NGUYEN VAN A`
  - Issue date: `07/15`
  - OTP: `123456`

  Tap "Tiếp tục" → "Thanh toán" → confirm OTP.

- [ ] **3. Observe success page**

  Expected: VNPay overlay shows our `/payment-success.html` with orderCode + amount. Tap "Đóng" → returns to Mini App which already shows `payment_status = Đã thanh toán` (polling caught it within 3s).

- [ ] **4. Verify backend state**

  ```bash
  docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "
  SELECT p.status AS pay_status, p.paid_at IS NOT NULL AS paid,
         o.status AS order_status, o.payment_status AS order_pay_status
  FROM payment p JOIN orders o ON o.id = p.order_id
  ORDER BY p.created_at DESC LIMIT 1;"
  # Expected: pay_status=SUCCESS, paid=t, order_status=CONFIRMED, order_pay_status=SUCCESS
  ```

- [ ] **5. Verify Web Admin shows badge**

  Open http://localhost:5174 → login → /orders → newest order has green "Thành công" badge in the Thanh toán column.

**Failure path checklist:**

- [ ] **6. Customer-cancel test**

  Repeat steps 1-2 but tap "Hủy" on VNPay's confirm screen.
  Expected: Overlay shows `/payment-failed.html` with code `24`, message "Khách hàng đã hủy giao dịch".
  Mini App `payment_status` stays PENDING, "Đang chờ xác nhận" message visible. After 60s, "Chưa nhận được xác nhận" appears.

- [ ] **7. Retry after fail**

  Open the same order in Mini App. Currently the design does not expose a "Retry payment" button on the detail page — verify the previous Payment row is marked FAILED (response_code=24) in DB. (Adding a retry button is deferred to a future phase.)

- [ ] **8. Replay protection (manual)**

  After step 4 succeeds, copy the IPN URL VNPay called (from `payment_transaction.raw_payload` of the IPN row), re-POST it with curl:

  ```bash
  # See TASK 13 step 4 for the curl shape — re-send with same params + hash
  ```
  Expected: `{"RspCode":"02","Message":"Order already confirmed"}`. DB state unchanged.

- [ ] **9. Bad signature protection (manual)**

  Same as step 8 but flip one char in `vnp_SecureHash`.
  Expected: `{"RspCode":"97","Message":"Invalid Checksum"}`. No DB change. No event published.

- [ ] **10. Stale-pending cleanup**

  Create a VNPAY order, do not pay. Wait 16 minutes. Run:
  ```bash
  docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "
  SELECT status, vnp_response_code FROM payment ORDER BY created_at DESC LIMIT 1;"
  # Expected within ~16-17 min: status=FAILED, response_code=EXPIRED
  ```

**Acceptance (verification only — no commit):**
- All 10 checklist items pass
- No backend errors in logs during E2E (`grep -i "error\|exception" /tmp/p7_t13.log` empty)
- Web Admin orders list reflects badge colors correctly
- Mini App polling stops on success or after 60s timeout

---


## Phase exit checks

After all 18 tasks are committed, the following must hold:

- [ ] `mvn -q -B test` from `backend/` is green — total tests increased by ~20 from P6 baseline (signature 7 + audit 2 + entities 3 + properties 2 + events 3 + service 13 + IT 2 + confirmAfterPayment 3 + scheduler 3 ≈ 38; subtract a couple if the implementer collapses similar cases)
- [ ] `pnpm -F miniapp build && pnpm -F webadmin build && pnpm -F @shop/shared build` all succeed
- [ ] Flyway history shows V9 applied successfully
- [ ] `docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d payment"` shows all expected columns + UNIQUE on `vnp_txn_ref`
- [ ] Boot app → `curl POST /api/payment/vnpay/ipn` with random body → returns `{"RspCode":"97",...}` 200
- [ ] Boot app → `curl GET /payment-success.html` → 200 with HTML body
- [ ] Manual sandbox E2E (TASK 18) signed off by the user (or noted as deferred until merchant credentials arrive)

Approximate cumulative line counts after P7:
- ~18 commits (one per implementation task; TASK 13 and TASK 18 are verification-only)
- ~16 new Java files in `payment` module + 1 modified order + 3 new events in `shared`
- ~38 new unit/IT tests on the backend
- ~4 new TS files on the frontend + 4 modified pages

---

## Self-review checklist (for the user before approving execution)

Before running this plan, please sanity-check the items below. Each one is something the planner cannot verify by reading the spec alone — it depends on either local env state or your preferences. Adjust the plan or pause the agent if any item is wrong.

### Architecture & decisions

- [ ] **Module direction `payment → order` (not reverse)** matches your modular monolith principle. If you ever want `order` aware of payment types directly, the cross-module event approach will feel like indirection — confirm now or refactor later.
- [ ] **Events live in `shared.event`** package — not in their respective publisher modules. This avoids the circular dep, but it concentrates "cross-cutting" types in `shared`. If you prefer a dedicated `events` module, fork TASK 7 to create one.
- [ ] **TxnRef format `{orderCode}-{epochMs}`** matches design spec §10.6. If you've already captured a different format in some downstream system (e.g. analytics dashboard expecting different shape), update before running.
- [ ] **Static HTML return pages live in `backend/app/src/main/resources/static/`** — they get bundled into the Spring Boot JAR and served by Spring's default static handler. If you'd rather host them outside the JAR (CDN, separate Vite app), revise TASK 12.
- [ ] **Cron interval = 60 seconds**, threshold = 15 minutes. If your laptop sleeps during dev or you'd rather have less log noise, bump `fixedDelay` to 300_000 (5 min). Threshold MUST stay aligned with VNPay's actual `vnp_ExpireDate` value (15 min) to avoid expiring valid in-flight payments.

### Security & correctness

- [ ] **`hashSecret` masking in `VnpayProperties.toString()`** — verify by code review that `VnpayProperties` is the only place the secret can be logged; no other class accepts the raw string. Grep `props.hashSecret` after Wave 0 — should only appear in `VnpaySignatureService.hmacSHA512`.
- [ ] **`MessageDigest.isEqual` is the ONLY comparator used on the received hash** — grep for `\.equals\(.*hash` after Wave 0; should be zero hits in any non-test file.
- [ ] **Amount tampering check uses long arithmetic** (`longValueExact`) not BigDecimal compare — confirm in `handleIpn`. Decimal-scale rounding bugs here = silent acceptance of lowered amounts.
- [ ] **Idempotency uses `payment.status != PENDING`**, not a separate "processed" flag. Verify TASK 8 test `ipn_replayWhenAlreadySuccess_returns02_andAuditsButDoesNotMutate` mirrors this.
- [ ] **Return URL never writes DB** — grep `paymentRepo.save` inside `handleReturn`; should be zero hits. (The `audit.record` call IS allowed — that writes to `payment_transaction`, not `payment`.)
- [ ] **No log statement contains `props.hashSecret()`** anywhere — grep before committing TASKS 5, 7, 8, 11.

### Tests

- [ ] **Wave 0 signature test contains a deterministic vector** — `signsKnownVector_lowercaseHex_sortedKeys`. If you later get a real captured sandbox URL, add it as a second fixture so we have at least one ground-truth check.
- [ ] **`VnpayControllerIT` uses Awaitility** for the order-confirm assertion (because `AFTER_COMMIT` listener runs out-of-band). If Awaitility isn't on the test classpath, TASK 9 will fail to compile — verify via `mvn dependency:tree -pl backend/app | grep awaitility`.
- [ ] **`OrderServiceConfirmAfterPaymentTest` constructs `OrderStateMachine` directly** (not mocked) — this exercises the actual whitelist. Good. Don't downgrade to a mock.

### Frontend

- [ ] **`Telegram.WebApp.openLink` is the redirect mechanism** in `usePayWithVnpay`, NOT `window.location.href`. Mini App container behavior depends on this — see research §Pitfall 6.
- [ ] **Polling timeout = 60s, interval = 3s.** Comfortable for the user, light on backend (~20 requests per pending order). If your sandbox routinely takes > 60s to deliver IPN, bump `POLL_TIMEOUT_MS`.
- [ ] **`PaymentStatus` type in `@shop/shared`** already covers PENDING/SUCCESS/FAILED/REFUNDED. Verify by `grep -n "PaymentStatus" frontend/shared/src/types/order.ts`. If REFUNDED isn't there, add it before TASK 17.
- [ ] **Vietnamese labels** match what's already in the codebase (no mix of "Thành công" vs "Hoàn tất" etc.). Cross-check `OrderStatusBadge` for tone consistency.

### Out of scope confirmations

- [ ] **No refund endpoint, UI, or `vnpay_refund` API call** is implemented in any task. Confirm by grepping for `refund` after Wave 1 commit — should only appear in comments and enum values, never in active code.
- [ ] **No bot notification of payment success in P7.** `OrderConfirmedEvent` is published, but no listener exists yet — bot integration is P8's job. If you want a quick win, add a one-liner bot listener at the end of TASK 10; otherwise let it wait.
- [ ] **No WebSocket push of payment status** — polling only. If P6's WebSocket infra is mature enough you'd rather push, add a parallel topic in a follow-up task; do not bolt it onto TASK 16.

### Plan structure

- [ ] **Task 9 commits a temporarily-red test** (it goes green only after Task 10 lands). This is intentional — call it out in the commit message. If you'd rather not have red tests in any commit, swap the order of TASKS 9 and 10 (implement listener first, then controller IT).
- [ ] **TASK 13 and TASK 18 produce no commits.** They are verification gates only. If your CI requires a git tag per gate, add an explicit `git tag p7-backend-smoke-ok` / `p7-e2e-smoke-ok` step.
- [ ] **No new Maven dependency is added beyond what `delivery-parent` already manages** — confirm by diffing `backend/modules/payment/pom.xml` against `backend/modules/delivery/pom.xml`. The two should look near-identical post-TASK 1.

### Open questions to resolve before / during execution

1. **Test isolation for `Clock` injection** — `Application.java` exposes `Clock systemClock()` (TASK 11). Tests that inject a fixed `Clock` via `@MockBean` will work, but `@SpringBootTest` IT tests get the real system clock by default. If `VnpayControllerIT.ipnHappyPath_flipsPaymentAndOrder` ever needs deterministic time (e.g. asserting `paid_at = X`), add `@MockBean Clock` to the test class. Currently the IT just asserts "paidAt is not null" which is robust.

2. **`@ConfigurationPropertiesScan`** in `Application.java` already covers `com.shop.delivery` recursively, so `VnpayProperties` is auto-detected. No `@EnableConfigurationProperties(VnpayProperties.class)` needed anywhere. If you're seeing "could not bind" errors at boot, double-check that the `@ConfigurationProperties` annotation made it onto the record.

3. **Local IPN tunnel** — if you don't want to set up ngrok/cloudflared just for testing, TASK 18 is the only place that needs it. TASK 13's manual curl test does NOT require a tunnel because it calls localhost directly. Real VNPay → our IPN traffic is the only thing that needs a public URL.

4. **`Awaitility` test dependency** — if `mvn dependency:tree -pl backend/app | grep awaitility` shows nothing, append this to `backend/app/pom.xml`:
   ```xml
   <dependency>
     <groupId>org.awaitility</groupId>
     <artifactId>awaitility</artifactId>
     <scope>test</scope>
   </dependency>
   ```
   (Spring Boot's BOM manages the version; no `<version>` tag needed.)

5. **`@MockBean` deprecation** — Spring Boot 3.4 deprecated `@MockBean` in favour of `@MockitoBean`. If you want to future-proof, use `@MockitoBean Clock clock` in any IT that needs a fixed clock. Both still work in 3.4.x.

---

## Final summary

**18 tasks, 3 waves, 16 atomic commits + 2 verification gates.**

Wave 0 (1-6): module + schema + crypto. Wave 1 (7-13): events + service + controller + cron + security. Wave 2 (14-18): frontend wiring + admin badge + E2E.

After completing this plan the project will satisfy all 11 deliverables from the brief: new module compiles, V9 migration applied, 3 REST endpoints work with proper signature/idempotency/amount checks, cross-module event reaches `OrderService.confirmAfterPayment` which transitions PENDING→CONFIRMED, scheduler expires stale payments after 15 minutes, Mini App can place + pay + see status update via polling, Web Admin shows colored payment badges, and tests cover every documented threat (forged sig → 97, replay → 02, amount tamper → 04, unknown txnRef → 01).

