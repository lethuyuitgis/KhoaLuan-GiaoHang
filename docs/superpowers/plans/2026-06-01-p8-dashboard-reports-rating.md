# P8 — Dashboard + Reports (Recharts) + Shipper Rating Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chủ shop mở Web Admin thấy Dashboard KPI thực với biểu đồ doanh thu 7 ngày + top 3 shipper. Vào `/reports` chọn khoảng ngày → 3 biểu đồ Recharts: LineChart doanh thu theo ngày/tuần, BarChart top shipper, PieChart lý do huỷ. Khi đơn DELIVERED, bot Telegram gửi keyboard inline 1–5 sao + "Bỏ qua"; khách bấm sao → ghi `rating` row + cập nhật `shipper_profile.rating_avg`/`rating_count` nguyên tử + mở FSM nhập nhận xét tuỳ chọn; bấm "Bỏ qua" → chỉ xoá keyboard, không lưu DB.

**Scope (Tuần 11):**
- Backend `delivery` module: V10 migration `rating`, `Rating` entity + repo, `RatingService` (rate + updateComment), `RatingController` (POST `/api/orders/{id}/rating`), DTOs, `ReportsRepository` (4 native queries), `ReportsQueryService`, `AdminDashboardController`, `AdminReportsController` (3 endpoints) + DTOs, controller-level `groupBy` whitelist + 90-day cap
- Backend `bot` module: `ConversationState` entity + repo + service (FSM scaffolding, JSONB payload, first FSM in project), `RatingCallbackHandler` (RATE: + RATE_SKIP:), `RatingCommentHandler` (FSM state-gated, `/skip` support), `@Order(0)` on FSM handler
- Backend `notification` module: extend `OrderAssignedNotifier.onOrderDelivered` to attach rating inline keyboard via new `RatingPromptBuilder`; idempotent check (skip prompt if already rated)
- Backend `shared` module: no new files (recompute strategy avoids new cross-module event)
- Backend `app` module: `V10__rating.sql` Flyway migration
- Frontend Web Admin: `pnpm add recharts@^3.8.1`, types in `@shop/shared`, rewrite `DashboardPage` (KPI cards + 7-day LineChart + top 3 shipper list, 30s polling), new `ReportsPage` at `/reports` (date-range picker + LineChart + BarChart + PieChart), wire route in `App.tsx`, add nav link in `Sidebar.tsx`
- Tests: `RatingServiceTest` (5 cases), `RatingRepositoryIT` (UNIQUE + CHECK), `RatingControllerTest` (`@WebMvcTest`), `ConversationStateServiceIT` (JSONB round-trip), `ConversationStateServiceTest`, `RatingCallbackHandlerTest`, `RatingCommentHandlerTest`, `OrderAssignedNotifierTest` (new test method), `AdminDashboardControllerTest`, `AdminReportsControllerTest` (param validation), `ReportsRepositoryIT`

**Defer to future phase:**
- WebSocket-pushed dashboard updates (admin JWT for STOMP CONNECT) — research §5 explicit defer
- Rating editing of stars (only comment editable within same FSM session)
- Rating moderation / flagging / low-rating escalation
- Cross-shipper comparison charts on shipper detail page
- CSV / Excel export of reports
- Date ranges > 90 days
- Mini App "My Orders" rate-now button (canonical `POST /api/orders/:id/rating` already exists)
- Vitest setup for frontend (type-check + build + manual smoke is enough for thesis)

**Architecture:**
- **Module direction:** `bot → delivery → order → auth → shared`. `notification` already depends on `bot + delivery + order`. `Rating` entity, `RatingService`, `ReportsRepository`, both admin controllers all live in **`delivery`** (research §2). `ConversationState*` lives in **`bot`** module (research §4.3) since it's bot infrastructure.
- **Atomic rating_avg:** `RatingService.rate(...)` is `@Transactional`. Inside: INSERT rating → recompute `SELECT AVG/COUNT FROM rating WHERE shipper_id = ?` → UPDATE `shipper_profile`. Postgres READ COMMITTED + same-tx semantics gives exactness without `@Version`. (research §1.2)
- **Idempotency:** `rating.order_id UNIQUE` is the DB-level guarantee. `DataIntegrityViolationException` → `ConflictException("ALREADY_RATED", ...)`.
- **Bot flow on DELIVERED:**
  ```
  OrderDeliveredEvent (delivery module, AFTER_COMMIT)
    → OrderAssignedNotifier.onOrderDelivered (notification module)
        ├─ existing: send "đã giao xong" text to shipper
        ├─ NEW: if !ratingRepo.existsByOrderId(orderId)
        │       → send "⭐ đánh giá shipper" + RatingPromptBuilder.build keyboard to customer
        └─ existing: replace customer message text with shipper-rating prompt
  Customer taps star button
    → BotWebhookController → UpdateRouter
    → RatingCallbackHandler (@Order(50), canHandle returns true on RATE:/RATE_SKIP:)
        ├─ RATE_SKIP:{orderId} → removeKeyboard + answer callback + return
        ├─ RATE:{orderId}:{stars} → ratingService.rate(orderId, customerId, stars, null)
        │   ├─ rating row inserted (comment NULL)
        │   ├─ shipper_profile recomputed
        │   ├─ catch ConflictException → "Đơn đã được đánh giá rồi" + removeKeyboard + return
        │   └─ otherwise → removeKeyboard + answer callback "Cảm ơn bạn!"
        └─ conv.put(customerId, "CUSTOMER_RATING_COMMENT", {orderId}) + sendText "Bạn có muốn nhập nhận xét? Gõ tin nhắn hoặc /skip"
  Customer types text OR /skip
    → RatingCommentHandler (@Order(0), canHandle returns true only when FSM state == CUSTOMER_RATING_COMMENT)
        ├─ "/skip" → conv.clear + sendText "Đã ghi nhận đánh giá. Cảm ơn bạn!"
        └─ free text → ratingService.updateComment(orderId, customerId, text) + conv.clear + sendText "Cảm ơn nhận xét của bạn!"
  ```
- **Reports SQL strategy:** native queries via interface projections (research §3 verbatim). `DATE_TRUNC` for bucketing (non-portable in JPQL → must be native). `generate_series` only for the 7-day dashboard mini-chart (sparse-data fill). 90-day range cap enforced at controller; `groupBy` whitelist (`day` | `week`) enforced before binding to `DATE_TRUNC`.
- **Date semantics:** `from`/`to` are inclusive LocalDate boundaries. SQL uses `created_at >= :from AND created_at < :to + INTERVAL '1 day'` (research Pitfall 1). JVM/Postgres default timezone — for thesis, document "today = JVM default TZ"; production should set `TZ=Asia/Ho_Chi_Minh`.
- **Frontend polling:** TanStack Query `refetchInterval: 30000, refetchOnWindowFocus: true`. WebSocket admin auth deferred (research §5). Dashboard polls every 30s; Reports refetches on date change (no interval).
- **Recharts version:** `recharts@^3.8.1` (verified npm view, published 2026-03-25, React 18/19 compat). ResponsiveContainer wrapped in fixed-height div (`h-72`) to avoid 0-px bug (research Pitfall 7).
- **Vietnamese UI:** All labels VI; currency via existing `formatVnd` from `@shop/shared`; date format `dd/MM/yyyy` via `date-fns` (already in webadmin).

**Tech Stack (additions on top of P7):**
- Backend: zero new external Maven deps. `@JdbcTypeCode(SqlTypes.JSON)` for JSONB is native to Hibernate 6 (no `hypersistence-utils`).
- Frontend: `recharts@^3.8.1` (only new dep — research §6.1; ~80 KB gzipped tree-shaken).

---

## Bối cảnh từ P5/P6/P7

Sau P5 + P6 + P7:
- ~138 commits, BUILD SUCCESS, all migrations V1–V9 applied
- Order state machine: PENDING → CONFIRMED → ASSIGNED → DELIVERING → DELIVERED/RETURNED/CANCELLED
- `OrderDeliveredEvent(assignmentId, orderId, orderCode, shipperId, customerId)` exists in `com.shop.delivery.delivery.service.event` ✓ verified
- `OrderAssignedNotifier.onOrderDelivered(OrderDeliveredEvent)` exists in `notification` module — currently sends 2 plain text messages. P8 extends this method to attach rating inline keyboard to the customer message.
- `ShipperProfile` (in `delivery`) has `rating_avg NUMERIC(3,2)` + `rating_count INT` columns since V6 — P8 populates them.
- `DeliveryAssignment` has `shipper_id`, `delivered_at` columns — used by both `RatingService` (to find shipper for an order) and the top-shippers query.
- `conversation_state` table exists in V3 migration: `(telegram_user_id BIGINT PK, state VARCHAR(64), data JSONB, updated_at TIMESTAMPTZ)`. No Java code yet — P8 introduces it.
- `UpdateRouter` in `bot/router/UpdateRouter.java` injects `List<UpdateHandler>` and iterates `canHandle` → `handle`. Spring's `OrderUtils` honors `@Order` on injected lists — handlers register with `@Order(0)` for FSM-bound first, `@Order(50)` for callback-data handlers, `@Order(100)` for commands.
- `BotSender` already wraps `DefaultAbsSender.execute(...)` with try/catch + log (swallows `TelegramApiException`) — RatingCallbackHandler/RatingCommentHandler use this.
- `DomainException` subtypes available in `shared`: `NotFoundException`, `ValidationException`, `BusinessRuleException`, `ConflictException`. Throwing these is the convention.
- `SecurityConfig` (in `auth` module) already routes `/api/admin/**` → `hasRole("SHOP_OWNER")`. New admin endpoints inherit the protection. Class-level `@PreAuthorize("hasRole('SHOP_OWNER')")` for defence-in-depth.
- Web Admin `DashboardPage.tsx` is the placeholder that says "📊 Biểu đồ 7 ngày + Top shipper sẽ ở P9 (Reports)" — P8 rewrites it entirely.
- Sidebar nav (`Sidebar.tsx`) lists Dashboard / Đơn hàng / Sản phẩm / Shipper — P8 adds "Báo cáo" → `/reports`.
- `@shop/shared` exposes `formatVnd`, `formatDateTime` via `frontend/shared/src/index.ts` → reused.
- `OrderTestcontainerBase` exists in `order` module — P8 copies the pattern into `delivery` module as `DeliveryTestcontainerBase` (research §7.3).

**Constraints (kế thừa):**
- Java 17 + Spring Boot 3.4 (Hibernate 6.6) — `@JdbcTypeCode(SqlTypes.JSON)` works out of the box for `Map<String,Object>` ↔ jsonb
- Postgres 16 (`postgres:16-alpine`)
- pnpm 9 workspaces; webadmin on :5174; backend on :8080
- DB: `shop_delivery_postgres_dev`
- Test admin: `admin@shop.local / admin123`
- 138 → ~155 commits expected at end of P8 (one commit per task)

**Decisions chốt (từ research + brief):**

1. **Rating module placement:** `delivery` module (research §2.1). `delivery` already depends on `order + auth`, owns `ShipperProfile + DeliveryAssignment`. New module is overkill for one entity.

2. **Reports module placement:** also `delivery` (research §2.2). Two new controllers: `AdminDashboardController` + `AdminReportsController`. One repository: `ReportsRepository extends JpaRepository<Order, UUID>` (Order is the most-referenced entity in the queries).

3. **Rating avg strategy:** **recompute** via `SELECT AVG, COUNT FROM rating WHERE shipper_id = ?` after each insert (research §1.2 Option B). Avoids `@Version` on `ShipperProfile` and floating-point drift. Performance fine at thesis scale.

4. **Rating immutability:** `stars` immutable after `rate(...)`. `comment` editable once via `updateComment(...)` within the same FSM session (no UI to edit later). If the customer wants to "fix" the rating later → re-rate not possible (UNIQUE catches it); product owner can manually update via SQL if needed.

5. **RATE_SKIP behavior:** No DB write. Just `removeKeyboard` + answer callback. The shipper's `rating_count` reflects only actual ratings — what we want (research §19 Q2).

6. **FSM state name:** `CUSTOMER_RATING_COMMENT`. Data payload: `{"orderId": "<uuid>"}` (a `Map<String,Object>` serialized to JSONB). FSM cleared on `/skip`, after comment saved, OR by future scheduled cleanup (>30 min stale).

7. **Idempotent rating prompt:** Before sending the keyboard in `onOrderDelivered`, `ratingRepo.existsByOrderId(orderId)` check skips it if already rated. Prevents double-prompts if `OrderDeliveredEvent` ever fires twice (it shouldn't, but defence-in-depth).

8. **Handler ordering:** `@Order(0)` on `RatingCommentHandler` (FSM-bound text catcher), `@Order(50)` on `RatingCallbackHandler` (callback-data catcher — order doesn't matter much since callback-data prefix is unique, but explicit), implicit `@Order(LOWEST)` on commands. Verified Spring honors `@Order` on injected `List<T>` via `OrderUtils`.

9. **Callback data format:** `RATE:<orderId>:<stars>` (43 bytes) and `RATE_SKIP:<orderId>` (46 bytes) — both well under Telegram's 64-byte limit (research §4.5).

10. **Reports SQL strategy:** native `@Query(nativeQuery=true)` + interface projections (research §3, §14 Pattern 2). Match existing `PaymentRepository` style. `DATE_TRUNC(:bucket, x)` with `bucket` whitelisted to `'day'|'week'` in controller.

11. **Date range cap:** 90 days. Enforced in controller (`AdminReportsController.requireRange(from, to)`). Throws `ValidationException("DATE_RANGE_TOO_LARGE", "Phạm vi tối đa 90 ngày")` → 400.

12. **Dashboard 7-day fill:** Use `generate_series(CURRENT_DATE - 6, CURRENT_DATE, '1 day')` for guaranteed 7 rows (research §3.1). Reports endpoints do NOT use `generate_series` (variable range; sparse rows acceptable).

13. **Currency:** `formatVnd` from `@shop/shared` — already rounds to integer VND. Recharts tooltip uses `formatVnd(value)`.

14. **Date picker:** native `<input type="date" min={...} max={...}>`, no new dep (research §6.4). `date-fns` already in webadmin.

15. **Polling:** `useQuery({ refetchInterval: 30000, refetchOnWindowFocus: true, staleTime: 10000 })` for Dashboard. Reports refetches on date-range change only.

16. **Recharts chart heights:** Wrap every `<ResponsiveContainer>` in `<div className="h-72 w-full">` (288 px) to avoid the 0-px-height bug (research Pitfall 7).

17. **Sidebar nav:** Add `"/reports"` between `"/orders"` and `"/products"` with `📈` icon and label "Báo cáo" — matches existing Vietnamese style.

---

## File Structure (sau khi P8 hoàn thành)

```
backend/
├── app/
│   └── src/main/resources/db/migration/
│       └── V10__rating.sql                                                  (TASK 1)
│
├── modules/
│   ├── delivery/
│   │   ├── src/main/java/com/shop/delivery/delivery/
│   │   │   ├── entity/
│   │   │   │   └── Rating.java                                              (TASK 2)
│   │   │   ├── repository/
│   │   │   │   ├── RatingRepository.java                                    (TASK 2)
│   │   │   │   └── ReportsRepository.java                                   (TASK 9)
│   │   │   ├── service/
│   │   │   │   ├── RatingService.java                                       (TASK 3)
│   │   │   │   └── ReportsQueryService.java                                 (TASK 10)
│   │   │   └── api/
│   │   │       ├── customer/
│   │   │       │   ├── RatingController.java                                (TASK 4)
│   │   │       │   └── dto/RateOrderRequest.java                            (TASK 4)
│   │   │       └── admin/
│   │   │           ├── AdminDashboardController.java                        (TASK 10)
│   │   │           ├── AdminReportsController.java                          (TASK 10)
│   │   │           └── dto/
│   │   │               ├── DashboardSummary.java                            (TASK 10)
│   │   │               ├── RevenuePoint.java                                (TASK 10)
│   │   │               ├── TopShipperRow.java                               (TASK 10)
│   │   │               └── CancellationReport.java                          (TASK 10)
│   │   └── src/test/java/com/shop/delivery/delivery/
│   │       ├── support/
│   │       │   └── DeliveryTestcontainerBase.java                           (TASK 2)
│   │       ├── repository/
│   │       │   ├── RatingRepositoryIT.java                                  (TASK 2)
│   │       │   └── ReportsRepositoryIT.java                                 (TASK 11)
│   │       ├── service/
│   │       │   └── RatingServiceTest.java                                   (TASK 3)
│   │       └── api/
│   │           ├── customer/RatingControllerTest.java                       (TASK 4)
│   │           └── admin/
│   │               ├── AdminDashboardControllerTest.java                    (TASK 11)
│   │               └── AdminReportsControllerTest.java                      (TASK 11)
│   │
│   ├── bot/
│   │   ├── src/main/java/com/shop/delivery/bot/
│   │   │   ├── fsm/
│   │   │   │   ├── ConversationState.java                                   (TASK 5)
│   │   │   │   ├── ConversationStateRepository.java                         (TASK 5)
│   │   │   │   └── ConversationStateService.java                            (TASK 5)
│   │   │   └── handler/customer/
│   │   │       ├── RatingCallbackHandler.java                               (TASK 7)
│   │   │       └── RatingCommentHandler.java                                (TASK 8)
│   │   └── src/test/java/com/shop/delivery/bot/
│   │       ├── fsm/
│   │       │   ├── ConversationStateServiceTest.java                        (TASK 5)
│   │       │   └── ConversationStateServiceIT.java                          (TASK 5)
│   │       └── handler/customer/
│   │           ├── RatingCallbackHandlerTest.java                           (TASK 7)
│   │           └── RatingCommentHandlerTest.java                            (TASK 8)
│   │
│   └── notification/
│       ├── src/main/java/com/shop/delivery/notification/
│       │   ├── OrderAssignedNotifier.java                                   (modify — TASK 6)
│       │   └── RatingPromptBuilder.java                                     (TASK 6)
│       └── src/test/java/com/shop/delivery/notification/
│           ├── OrderAssignedNotifierTest.java                               (modify — TASK 6)
│           └── RatingPromptBuilderTest.java                                 (TASK 6)
│
frontend/
├── shared/src/
│   ├── types/reports.ts                                                     (TASK 14)
│   ├── types/index.ts                                                       (modify)
│   ├── api/reports.ts                                                       (TASK 14)
│   └── api/index.ts                                                         (modify)
└── webadmin/
    ├── package.json                                                         (modify — recharts dep)
    └── src/
        ├── App.tsx                                                          (modify — TASK 17)
        ├── components/
        │   ├── Sidebar.tsx                                                  (modify — TASK 17)
        │   └── charts/
        │       ├── RevenueChart.tsx                                         (TASK 16)
        │       ├── TopShippersChart.tsx                                     (TASK 16)
        │       ├── CancellationChart.tsx                                    (TASK 16)
        │       └── RevenueMiniChart.tsx                                     (TASK 15)
        └── pages/
            ├── DashboardPage.tsx                                            (rewrite — TASK 15)
            └── ReportsPage.tsx                                              (TASK 16)
```

**Backend file mới:** ~21
**Frontend file mới:** ~7 (+ 3 modify)

---

## WAVE 0 — Backend rating + FSM foundation (Tasks 1–6)

Wave 0 builds the data + service + notification scaffolding. Output: `./mvnw -pl modules/delivery test -Dtest=RatingServiceTest` green; `./mvnw -pl modules/bot test -Dtest=ConversationStateServiceTest` green; `RatingPromptBuilderTest` green. No HTTP / bot handler routing yet.


## TASK 1: V10__rating.sql Flyway migration (1 commit)

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V10__rating.sql`

- [ ] **Step 1: Write migration**

Create `backend/app/src/main/resources/db/migration/V10__rating.sql`:

```sql
-- V10__rating.sql — customer rating of shipper after DELIVERED (Should #10, P8)
-- Owns: rating
-- Cross-ref:
--   rating.order_id    → orders(id)         (V4__order.sql)
--   rating.customer_id → telegram_user(id)  (V2__auth.sql)
--   rating.shipper_id  → telegram_user(id)  (V2__auth.sql)

CREATE TABLE rating (
    id           BIGSERIAL    PRIMARY KEY,
    order_id     UUID         NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    customer_id  BIGINT       NOT NULL REFERENCES telegram_user(id),
    shipper_id   BIGINT       NOT NULL REFERENCES telegram_user(id),
    stars        SMALLINT     NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment      TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Index for "rating history per shipper" (admin shipper detail page, top-shippers report).
CREATE INDEX idx_rating_shipper_created ON rating(shipper_id, created_at DESC);
-- order_id UNIQUE auto-creates an index — no extra needed.
-- customer_id is read rarely (only "did this customer rate?") — skip the index.
```

**Design notes (do not paste into migration):**
- `order_id UNIQUE` — DB-level "one rating per order"; double-tap race throws `DataIntegrityViolationException` which `RatingService.rate` translates to `ConflictException("ALREADY_RATED", ...)`.
- `ON DELETE CASCADE` on `order_id` matches `order_item.order_id` pattern.
- `customer_id` / `shipper_id` no cascade — ratings outlive hypothetical user-row deletion (we never delete telegram_user in practice).
- `stars SMALLINT CHECK (stars BETWEEN 1 AND 5)` — DB-enforced; Java validates too (defence-in-depth).
- `created_at TIMESTAMPTZ DEFAULT NOW()` — matches V4/V6/V9 convention.
- No `updated_at` — comment may be updated once via `RatingService.updateComment`, but we don't need an audit column for the thesis (commit message in the rating-comment edit will show "via FSM" if reviewer asks).

- [ ] **Step 2: Apply migration (smoke test)**

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
docker compose -f infra/docker-compose.dev.yml ps | grep -q healthy || \
  docker compose -f infra/docker-compose.dev.yml up -d
sleep 5

cd backend/app
BOT_TOKEN=${BOT_TOKEN:-dummy} BOT_USERNAME=${BOT_USERNAME:-DummyBot} \
  nohup ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8089" > /tmp/p8_t1.log 2>&1 &
BOOT_PID=$!
for i in $(seq 1 90); do
  grep -q "Started Application" /tmp/p8_t1.log 2>/dev/null && break
  grep -q "APPLICATION FAILED" /tmp/p8_t1.log 2>/dev/null && break
  sleep 1
done
kill $BOOT_PID 2>/dev/null
sleep 2
cd ../..

docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT version, success FROM flyway_schema_history ORDER BY version"
# Expected: V1..V10 all t

docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d rating"
# Expected: id BIGSERIAL PK, order_id UUID UNIQUE NOT NULL, customer_id BIGINT NOT NULL,
#           shipper_id BIGINT NOT NULL, stars SMALLINT (CHECK 1-5), comment TEXT, created_at TIMESTAMPTZ
# Indexes: rating_pkey, rating_order_id_key (UNIQUE), idx_rating_shipper_created
# Check constraints: rating_stars_check
```

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V10__rating.sql
git commit -m "feat(rating): add V10 migration for rating table"
```

**Acceptance:**
- Flyway history shows V10 success
- `\d rating` shows the UNIQUE on `order_id`, the CHECK on stars, and `idx_rating_shipper_created`
- Inserting `stars=0` or `stars=6` via psql throws CHECK violation
- Inserting two rows with same `order_id` throws UNIQUE violation

---

## TASK 2: Rating entity + RatingRepository + DeliveryTestcontainerBase + RatingRepositoryIT (TDD, 1 commit)

**Files:**
- Modify: `backend/modules/delivery/pom.xml`  *(add test-scope deps for ITs in this phase)*
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/entity/Rating.java`
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/repository/RatingRepository.java`
- Create: `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/support/DeliveryTestcontainerBase.java`
- Create: `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/repository/RatingRepositoryIT.java`

- [ ] **Step 0: Add test-scope deps to `backend/modules/delivery/pom.xml`** *(BLOCKER fix from plan-checker)*

The `delivery` module currently only has `spring-boot-starter-test`. P8 introduces Testcontainers ITs and `@WithMockUser` controller tests — those classes are NOT on the test classpath yet. Mirror what `backend/modules/bot/pom.xml` already does. Add inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
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
```

Add `backend/modules/delivery/pom.xml` to the `git add` line in Step 5 (commit) of this task.

- [ ] **Step 1: Write failing IT first (RED)**

Create `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/support/DeliveryTestcontainerBase.java`:

```java
package com.shop.delivery.delivery.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared Testcontainer base for delivery-module repository ITs.
 * Mirrors the {@code OrderTestcontainerBase} pattern from the {@code order} module.
 * Container is reused across tests (Testcontainers reuse must be enabled in ~/.testcontainers.properties).
 */
@Testcontainers
public abstract class DeliveryTestcontainerBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("delivery_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.flyway.enabled", () -> "false");
    }
}
```

Create `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/repository/RatingRepositoryIT.java`:

```java
package com.shop.delivery.delivery.repository;

import com.shop.delivery.delivery.entity.Rating;
import com.shop.delivery.delivery.support.DeliveryTestcontainerBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(DeliveryTestcontainerBase.class)
@ActiveProfiles("test")
class RatingRepositoryIT extends DeliveryTestcontainerBase {

    @Autowired
    RatingRepository ratingRepo;

    @Autowired
    TestEntityManager em;

    @Test
    void shouldRoundtripRating() {
        UUID orderId = UUID.randomUUID();
        Rating r = new Rating();
        r.setOrderId(orderId);
        r.setCustomerId(1001L);
        r.setShipperId(2001L);
        r.setStars((short) 5);
        r.setComment("Great shipper!");

        Rating saved = ratingRepo.saveAndFlush(r);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(ratingRepo.existsByOrderId(orderId)).isTrue();
        assertThat(ratingRepo.findByOrderId(orderId)).isPresent()
            .get().extracting(Rating::getStars).isEqualTo((short) 5);
    }

    @Test
    void shouldRejectDuplicateOrderId() {
        UUID orderId = UUID.randomUUID();
        Rating first = newRating(orderId, 1001L, 2001L, (short) 4);
        ratingRepo.saveAndFlush(first);

        Rating dup = newRating(orderId, 1002L, 2001L, (short) 3);
        assertThatThrownBy(() -> ratingRepo.saveAndFlush(dup))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectStarsBelowOne() {
        Rating r = newRating(UUID.randomUUID(), 1001L, 2001L, (short) 0);
        assertThatThrownBy(() -> ratingRepo.saveAndFlush(r))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectStarsAboveFive() {
        Rating r = newRating(UUID.randomUUID(), 1001L, 2001L, (short) 6);
        assertThatThrownBy(() -> ratingRepo.saveAndFlush(r))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aggregateForShipper_returnsAvgAndCount() {
        Long shipperId = 9999L;
        ratingRepo.saveAndFlush(newRating(UUID.randomUUID(), 1001L, shipperId, (short) 5));
        ratingRepo.saveAndFlush(newRating(UUID.randomUUID(), 1002L, shipperId, (short) 3));
        ratingRepo.saveAndFlush(newRating(UUID.randomUUID(), 1003L, shipperId, (short) 4));

        RatingRepository.RatingStats stats = ratingRepo.aggregateForShipper(shipperId);
        assertThat(stats.getCount()).isEqualTo(3);
        // (5 + 3 + 4) / 3 = 4.00
        assertThat(stats.getAvg()).isEqualByComparingTo(new BigDecimal("4.0000000000000000"));
    }

    @Test
    void aggregateForShipper_returnsZeroForUnknownShipper() {
        RatingRepository.RatingStats stats = ratingRepo.aggregateForShipper(0L);
        assertThat(stats.getCount()).isEqualTo(0);
        assertThat(stats.getAvg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private Rating newRating(UUID orderId, Long customerId, Long shipperId, short stars) {
        Rating r = new Rating();
        r.setOrderId(orderId);
        r.setCustomerId(customerId);
        r.setShipperId(shipperId);
        r.setStars(stars);
        return r;
    }
}
```

- [ ] **Step 2: Run test — expect compile fail (Rating + RatingRepository don't exist)**

```bash
cd backend && ./mvnw -q -pl modules/delivery -am test -Dtest=RatingRepositoryIT
# Expected: COMPILATION ERROR — Rating class not found
```

- [ ] **Step 3: Implement Rating entity (GREEN)**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/entity/Rating.java`:

```java
package com.shop.delivery.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Customer rating of a shipper after order DELIVERED. Append-only — stars are immutable
 * after creation; {@code comment} may be set once via {@code RatingService.updateComment}.
 *
 * <p>The {@code shipper_profile.rating_avg} / {@code rating_count} columns are derived
 * from this table and recomputed by {@code RatingService.rate(...)}. Do not update those
 * columns from anywhere else.
 */
@Entity
@Table(name = "rating")
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "shipper_id", nullable = false)
    private Long shipperId;

    @Column(name = "stars", nullable = false)
    private Short stars;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getShipperId() { return shipperId; }
    public void setShipperId(Long shipperId) { this.shipperId = shipperId; }
    public Short getStars() { return stars; }
    public void setStars(Short stars) { this.stars = stars; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 4: Implement RatingRepository**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/repository/RatingRepository.java`:

```java
package com.shop.delivery.delivery.repository;

import com.shop.delivery.delivery.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    /** Idempotency check (used by notifier and {@code RatingService}). */
    boolean existsByOrderId(UUID orderId);

    /** Used by {@code RatingService.updateComment} and by integration tests. */
    Optional<Rating> findByOrderId(UUID orderId);

    /**
     * Aggregate AVG and COUNT for a shipper. Native query because we want exact
     * Postgres AVG semantics over SMALLINT (returns NUMERIC). COALESCE handles the
     * "no ratings yet" case → AVG = 0.
     *
     * <p>Called by {@code RatingService.rate} after every insert; must be cheap.
     * Index {@code idx_rating_shipper_created (shipper_id, created_at DESC)} backs
     * the {@code WHERE shipper_id = ?} predicate.
     */
    @Query(value = """
        SELECT COALESCE(AVG(stars), 0) AS avg, COUNT(*) AS count
        FROM rating
        WHERE shipper_id = :shipperId
        """, nativeQuery = true)
    RatingStats aggregateForShipper(@Param("shipperId") Long shipperId);

    interface RatingStats {
        BigDecimal getAvg();
        Integer getCount();
    }
}
```

- [ ] **Step 5: Run IT — expect GREEN**

```bash
cd backend && ./mvnw -q -pl modules/delivery -am test -Dtest=RatingRepositoryIT
# Expected: Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```

- [ ] **Step 6: Commit**

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/entity/Rating.java \
        backend/modules/delivery/src/main/java/com/shop/delivery/delivery/repository/RatingRepository.java \
        backend/modules/delivery/src/test/java/com/shop/delivery/delivery/support/DeliveryTestcontainerBase.java \
        backend/modules/delivery/src/test/java/com/shop/delivery/delivery/repository/RatingRepositoryIT.java
git commit -m "feat(rating): add Rating entity + repository with aggregate query + IT"
```

**Acceptance:**
- 6 tests pass in `RatingRepositoryIT`
- UNIQUE on order_id enforced (test 2)
- CHECK on stars enforced both directions (tests 3, 4)
- `aggregateForShipper` returns BigDecimal avg + Integer count (tests 5, 6)
- `DeliveryTestcontainerBase` exists and is reusable for subsequent ITs

---

## TASK 3: RatingService — rate + updateComment (TDD, 1 commit)

**Files:**
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/RatingService.java`
- Create: `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/service/RatingServiceTest.java`

- [ ] **Step 1: Write failing test (RED)**

Create `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/service/RatingServiceTest.java`:

```java
package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.entity.Rating;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.delivery.repository.RatingRepository;
import com.shop.delivery.delivery.repository.ShipperProfileRepository;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.ConflictException;
import com.shop.delivery.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock RatingRepository ratingRepo;
    @Mock OrderRepository orderRepo;
    @Mock DeliveryAssignmentRepository assignmentRepo;
    @Mock ShipperProfileRepository shipperRepo;

    @InjectMocks RatingService service;

    private UUID orderId;
    private Long customerId;
    private Long shipperId;
    private Order order;
    private DeliveryAssignment assignment;
    private ShipperProfile shipper;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        customerId = 1001L;
        shipperId = 2001L;

        order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.DELIVERED);

        assignment = new DeliveryAssignment();
        assignment.setOrderId(orderId);
        assignment.setShipperId(shipperId);

        shipper = new ShipperProfile();
        shipper.setUserId(shipperId);
        shipper.setRatingAvg(BigDecimal.ZERO);
        shipper.setRatingCount(0);
    }

    @Test
    void rate_happyPath_insertsRatingAndRecomputesShipperStats() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(assignmentRepo.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(shipperRepo.findById(shipperId)).thenReturn(Optional.of(shipper));
        RatingRepository.RatingStats stats = mockStats(new BigDecimal("4.50"), 4);
        when(ratingRepo.aggregateForShipper(shipperId)).thenReturn(stats);

        Rating result = service.rate(orderId, customerId, 5, null);

        ArgumentCaptor<Rating> ratingCaptor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepo).saveAndFlush(ratingCaptor.capture());
        assertThat(ratingCaptor.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(ratingCaptor.getValue().getCustomerId()).isEqualTo(customerId);
        assertThat(ratingCaptor.getValue().getShipperId()).isEqualTo(shipperId);
        assertThat(ratingCaptor.getValue().getStars()).isEqualTo((short) 5);
        assertThat(ratingCaptor.getValue().getComment()).isNull();

        ArgumentCaptor<ShipperProfile> shipperCaptor = ArgumentCaptor.forClass(ShipperProfile.class);
        verify(shipperRepo).save(shipperCaptor.capture());
        assertThat(shipperCaptor.getValue().getRatingAvg()).isEqualByComparingTo("4.50");
        assertThat(shipperCaptor.getValue().getRatingCount()).isEqualTo(4);
    }

    @Test
    void rate_orderNotFound_throwsNotFoundException() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rate(orderId, customerId, 5, null))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("ORDER_NOT_FOUND");

        verify(ratingRepo, never()).saveAndFlush(any());
    }

    @Test
    void rate_orderNotDelivered_throwsBusinessRuleException() {
        order.setStatus(OrderStatus.DELIVERING);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.rate(orderId, customerId, 5, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Chỉ đánh giá");

        verify(ratingRepo, never()).saveAndFlush(any());
    }

    @Test
    void rate_wrongCustomer_throwsBusinessRuleException() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.rate(orderId, 9999L, 5, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("NOT_YOUR_ORDER");

        verify(ratingRepo, never()).saveAndFlush(any());
    }

    @Test
    void rate_alreadyRated_translatesIntegrityViolationToConflict() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(assignmentRepo.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(ratingRepo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("UNIQUE"));

        assertThatThrownBy(() -> service.rate(orderId, customerId, 5, null))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("ALREADY_RATED");

        verify(shipperRepo, never()).save(any());
    }

    @Test
    void rate_starsOutOfRange_throwsBusinessRuleException() {
        assertThatThrownBy(() -> service.rate(orderId, customerId, 0, null))
            .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.rate(orderId, customerId, 6, null))
            .isInstanceOf(BusinessRuleException.class);

        verify(orderRepo, never()).findById(any());
    }

    @Test
    void updateComment_happyPath_writesComment() {
        Rating r = new Rating();
        r.setOrderId(orderId);
        r.setCustomerId(customerId);
        r.setShipperId(shipperId);
        r.setStars((short) 4);

        when(ratingRepo.findByOrderId(orderId)).thenReturn(Optional.of(r));

        service.updateComment(orderId, customerId, "Giao nhanh, thái độ tốt");

        ArgumentCaptor<Rating> captor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepo).save(captor.capture());
        assertThat(captor.getValue().getComment()).isEqualTo("Giao nhanh, thái độ tốt");
    }

    @Test
    void updateComment_ratingNotFound_throwsNotFoundException() {
        when(ratingRepo.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateComment(orderId, customerId, "x"))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("RATING_NOT_FOUND");
    }

    @Test
    void updateComment_wrongCustomer_throwsBusinessRuleException() {
        Rating r = new Rating();
        r.setCustomerId(customerId);
        when(ratingRepo.findByOrderId(orderId)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.updateComment(orderId, 9999L, "x"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("NOT_YOUR_RATING");
    }

    private RatingRepository.RatingStats mockStats(BigDecimal avg, int count) {
        return new RatingRepository.RatingStats() {
            @Override public BigDecimal getAvg() { return avg; }
            @Override public Integer getCount() { return count; }
        };
    }
}
```

- [ ] **Step 2: Run test — expect compile fail (RatingService doesn't exist)**

```bash
cd backend && ./mvnw -q -pl modules/delivery -am test -Dtest=RatingServiceTest
# Expected: COMPILATION ERROR
```

- [ ] **Step 3: Implement RatingService (GREEN)**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/RatingService.java`:

```java
package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.entity.Rating;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.delivery.repository.RatingRepository;
import com.shop.delivery.delivery.repository.ShipperProfileRepository;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.ConflictException;
import com.shop.delivery.shared.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Customer-facing rating service. {@link #rate} is invoked from
 * {@code RatingController} (HTTP) and from {@code RatingCallbackHandler} (bot).
 *
 * <p><b>Concurrency:</b> rating insert + shipper-stats recompute happen in a
 * single transaction. The aggregate query sees the freshly-inserted row (same tx,
 * READ COMMITTED). Concurrent inserts for different orders each recompute the
 * full aggregate so the last-write-wins outcome on {@code shipper_profile} still
 * reflects all committed ratings. See research §1.2.
 */
@Service
public class RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);

    private final RatingRepository ratingRepo;
    private final OrderRepository orderRepo;
    private final DeliveryAssignmentRepository assignmentRepo;
    private final ShipperProfileRepository shipperRepo;

    public RatingService(RatingRepository ratingRepo,
                         OrderRepository orderRepo,
                         DeliveryAssignmentRepository assignmentRepo,
                         ShipperProfileRepository shipperRepo) {
        this.ratingRepo = ratingRepo;
        this.orderRepo = orderRepo;
        this.assignmentRepo = assignmentRepo;
        this.shipperRepo = shipperRepo;
    }

    @Transactional
    public Rating rate(UUID orderId, Long customerId, int stars, String comment) {
        if (stars < 1 || stars > 5) {
            throw new BusinessRuleException("STARS_OUT_OF_RANGE", "Số sao phải từ 1 đến 5");
        }

        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND",
                "Không tìm thấy đơn hàng"));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessRuleException("ORDER_NOT_RATEABLE",
                "Chỉ đánh giá được sau khi đơn đã giao");
        }
        if (!order.getCustomerId().equals(customerId)) {
            throw new BusinessRuleException("NOT_YOUR_ORDER",
                "Đây không phải đơn của bạn");
        }

        DeliveryAssignment assignment = assignmentRepo.findByOrderId(orderId)
            .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND",
                "Không tìm thấy phân công cho đơn này"));

        Rating r = new Rating();
        r.setOrderId(orderId);
        r.setCustomerId(customerId);
        r.setShipperId(assignment.getShipperId());
        r.setStars((short) stars);
        r.setComment(comment);

        try {
            ratingRepo.saveAndFlush(r);
        } catch (DataIntegrityViolationException ex) {
            // UNIQUE(order_id) — double-click race
            throw new ConflictException("ALREADY_RATED", "Đơn đã được đánh giá rồi");
        }

        recomputeShipperStats(assignment.getShipperId());
        log.info("Customer {} rated order {} → {} stars", customerId, orderId, stars);
        return r;
    }

    @Transactional
    public void updateComment(UUID orderId, Long customerId, String comment) {
        Rating r = ratingRepo.findByOrderId(orderId)
            .orElseThrow(() -> new NotFoundException("RATING_NOT_FOUND",
                "Chưa có đánh giá cho đơn này"));

        if (!r.getCustomerId().equals(customerId)) {
            throw new BusinessRuleException("NOT_YOUR_RATING",
                "Không phải đánh giá của bạn");
        }
        r.setComment(comment);
        ratingRepo.save(r);
    }

    private void recomputeShipperStats(Long shipperId) {
        RatingRepository.RatingStats stats = ratingRepo.aggregateForShipper(shipperId);
        shipperRepo.findById(shipperId).ifPresent(sp -> {
            BigDecimal avg = stats.getAvg() != null
                ? stats.getAvg().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            sp.setRatingAvg(avg);
            sp.setRatingCount(stats.getCount() != null ? stats.getCount() : 0);
            shipperRepo.save(sp);
        });
    }
}
```

- [ ] **Step 4: Run test — expect GREEN**

```bash
cd backend && ./mvnw -q -pl modules/delivery -am test -Dtest=RatingServiceTest
# Expected: Tests run: 9, Failures: 0
```

- [ ] **Step 5: Commit**

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/RatingService.java \
        backend/modules/delivery/src/test/java/com/shop/delivery/delivery/service/RatingServiceTest.java
git commit -m "feat(rating): add RatingService with rate + updateComment + 9 unit tests"
```

**Acceptance:**
- 9 unit tests pass (5 for `rate`, 3 for `updateComment`, 1 for stars-out-of-range guard)
- `BusinessRuleException` thrown for stars < 1 or > 5 BEFORE any DB call
- `ConflictException("ALREADY_RATED")` raised on DataIntegrityViolationException
- `shipper_profile.rating_avg` rounded to 2 decimal places via `HALF_UP`

---

## TASK 4: RatingController + RateOrderRequest DTO (TDD, 1 commit)

**Files:**
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/customer/RatingController.java`
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/customer/dto/RateOrderRequest.java`
- Create: `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/api/customer/RatingControllerTest.java`

The HTTP endpoint is the canonical API for rating. The bot also uses `RatingService` directly (no HTTP roundtrip), but this endpoint enables future Mini App "Rate Now" UX without rework.

- [ ] **Step 1: Write failing test (RED)**

Create `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/api/customer/RatingControllerTest.java`:

```java
package com.shop.delivery.delivery.api.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.delivery.delivery.api.customer.dto.RateOrderRequest;
import com.shop.delivery.delivery.entity.Rating;
import com.shop.delivery.delivery.service.RatingService;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import com.shop.delivery.auth.entity.TelegramUser;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RatingController.class)
class RatingControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean RatingService ratingService;

    @Test
    void post_validRequest_returns200WithRating() throws Exception {
        UUID orderId = UUID.randomUUID();
        Rating r = new Rating();
        r.setId(42L);
        r.setOrderId(orderId);
        r.setStars((short) 5);
        when(ratingService.rate(eq(orderId), eq(1001L), eq(5), any()))
            .thenReturn(r);

        RateOrderRequest body = new RateOrderRequest(5, "Tốt");
        mvc.perform(post("/api/orders/{id}/rating", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.stars").value(5));
    }

    @Test
    void post_starsOutOfRange_returns400() throws Exception {
        mvc.perform(post("/api/orders/{id}/rating", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"stars": 0, "comment": "x"}
                    """))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/orders/{id}/rating", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"stars": 6, "comment": "x"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void post_orderNotDelivered_returns422() throws Exception {
        when(ratingService.rate(any(), any(), anyInt(), any()))
            .thenThrow(new BusinessRuleException("ORDER_NOT_RATEABLE", "Chỉ đánh giá được sau khi đơn đã giao"));

        mvc.perform(post("/api/orders/{id}/rating", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"stars": 4}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_RATEABLE"));
    }

    @Test
    void post_alreadyRated_returns409() throws Exception {
        when(ratingService.rate(any(), any(), anyInt(), any()))
            .thenThrow(new ConflictException("ALREADY_RATED", "Đơn đã được đánh giá rồi"));

        mvc.perform(post("/api/orders/{id}/rating", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"stars": 5}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ALREADY_RATED"));
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

```bash
cd backend && ./mvnw -q -pl modules/delivery -am test -Dtest=RatingControllerTest
```

- [ ] **Step 3: Implement DTO + Controller (GREEN)**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/customer/dto/RateOrderRequest.java`:

```java
package com.shop.delivery.delivery.api.customer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RateOrderRequest(
    @NotNull @Min(1) @Max(5) Integer stars,
    @Size(max = 1000) String comment
) {}
```

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/customer/RatingController.java`:

```java
package com.shop.delivery.delivery.api.customer;

import com.shop.delivery.auth.api.CurrentUser;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.delivery.api.customer.dto.RateOrderRequest;
import com.shop.delivery.delivery.entity.Rating;
import com.shop.delivery.delivery.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping("/{id}/rating")
    public ResponseEntity<Rating> rate(
        @PathVariable("id") UUID orderId,
        @Valid @RequestBody RateOrderRequest body,
        @CurrentUser TelegramUser user
    ) {
        Rating r = ratingService.rate(orderId, user.getId(), body.stars(), body.comment());
        return ResponseEntity.ok(r);
    }
}
```

> **Note on auth principal:** matches the existing pattern used by `OrderController.create` in the `order` module. `TelegramAuthFilter` sets `currentUser` as a request attribute (a `TelegramUser` entity), NOT a Spring Security `Authentication`. `@AuthenticationPrincipal UserDetails` would resolve to `null` at runtime — even though `@WithMockUser` tests pass. `@CurrentUser` reads the request attribute and throws 401 if missing.
>
> **Test impact:** `RatingControllerTest` (Step 1) must inject the `TelegramUser` via the request attribute, not via `@WithMockUser`. Replace each `mvc.perform(...)` call's `with(user(...))` (if any) with `.requestAttr("currentUser", testUser)`, where `testUser` is a minimal `TelegramUser` with `id = 1001L`. The 200/400/422/409 status assertions stay the same — only the principal-injection mechanism changes. Reference: how `OrderControllerTest` (if present) or the bot module's controller tests handle this.

- [ ] **Step 4: Run test — expect GREEN**

```bash
cd backend && ./mvnw -q -pl modules/delivery -am test -Dtest=RatingControllerTest
```

- [ ] **Step 5: Commit**

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/customer/RatingController.java \
        backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/customer/dto/RateOrderRequest.java \
        backend/modules/delivery/src/test/java/com/shop/delivery/delivery/api/customer/RatingControllerTest.java
git commit -m "feat(rating): add POST /api/orders/{id}/rating endpoint + DTO + WebMvcTest"
```

**Acceptance:**
- 4 tests pass: 200 happy path, 400 on stars out of range (both directions), 422 on ORDER_NOT_RATEABLE, 409 on ALREADY_RATED
- Bean validation (`@Min(1) @Max(5)`) returns 400 BEFORE the controller body executes
- `BusinessRuleException` → 422 (via existing `GlobalExceptionHandler`), `ConflictException` → 409
- Endpoint is gated by `hasRole('CUSTOMER')`

---

## TASK 5: ConversationStateService — FSM foundation (TDD, 1 commit)

**Files:**
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/fsm/ConversationState.java`
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/fsm/ConversationStateRepository.java`
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/fsm/ConversationStateService.java`
- Create: `backend/modules/bot/src/test/java/com/shop/delivery/bot/fsm/ConversationStateServiceTest.java`
- Create: `backend/modules/bot/src/test/java/com/shop/delivery/bot/fsm/ConversationStateServiceIT.java`

This is the project's first FSM. Build minimal and generic — future flows (shipper registration, address book) will reuse the same `put/get/clear` API. The JSONB-round-trip IT is the safety net since this is the first time we exercise Hibernate 6's native `@JdbcTypeCode(SqlTypes.JSON)`.

- [ ] **Step 1: Write failing unit test (RED) — service-level**

Create `backend/modules/bot/src/test/java/com/shop/delivery/bot/fsm/ConversationStateServiceTest.java`:

```java
package com.shop.delivery.bot.fsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationStateServiceTest {

    @Mock ConversationStateRepository repo;
    @InjectMocks ConversationStateService service;

    @Test
    void put_newUser_createsRow() {
        when(repo.findById(1001L)).thenReturn(Optional.empty());

        service.put(1001L, "CUSTOMER_RATING_COMMENT", Map.of("orderId", "abc-123"));

        ArgumentCaptor<ConversationState> captor = ArgumentCaptor.forClass(ConversationState.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getTelegramUserId()).isEqualTo(1001L);
        assertThat(captor.getValue().getState()).isEqualTo("CUSTOMER_RATING_COMMENT");
        assertThat(captor.getValue().getData()).containsEntry("orderId", "abc-123");
    }

    @Test
    void put_existingUser_overwritesState() {
        ConversationState existing = new ConversationState();
        existing.setTelegramUserId(1001L);
        existing.setState("OLD_STATE");
        existing.setData(Map.of("x", 1));
        when(repo.findById(1001L)).thenReturn(Optional.of(existing));

        service.put(1001L, "NEW_STATE", Map.of("y", 2));

        ArgumentCaptor<ConversationState> captor = ArgumentCaptor.forClass(ConversationState.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getState()).isEqualTo("NEW_STATE");
        assertThat(captor.getValue().getData()).containsOnly(Map.entry("y", 2));
    }

    @Test
    void get_returnsEmpty_whenNoRow() {
        when(repo.findById(1001L)).thenReturn(Optional.empty());

        assertThat(service.get(1001L)).isEmpty();
    }

    @Test
    void clear_deletesRow() {
        service.clear(1001L);
        verify(repo).deleteById(1001L);
    }
}
```

- [ ] **Step 2: Write failing IT (RED) — JSONB round-trip**

Create `backend/modules/bot/src/test/java/com/shop/delivery/bot/fsm/ConversationStateServiceIT.java`:

```java
package com.shop.delivery.bot.fsm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ConversationStateService.class)
@ActiveProfiles("test")
@Testcontainers
class ConversationStateServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("bot_fsm_test")
        .withUsername("test").withPassword("test").withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired ConversationStateService service;
    @Autowired ConversationStateRepository repo;

    @Test
    void roundTrip_jsonbMapPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", "11111111-2222-3333-4444-555555555555");
        payload.put("retries", 0);
        payload.put("flag", true);

        service.put(42L, "CUSTOMER_RATING_COMMENT", payload);

        Optional<ConversationState> loaded = service.get(42L);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getState()).isEqualTo("CUSTOMER_RATING_COMMENT");
        assertThat(loaded.get().getData())
            .containsEntry("orderId", "11111111-2222-3333-4444-555555555555")
            .containsEntry("retries", 0)
            .containsEntry("flag", true);
        assertThat(loaded.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void overwrite_replacesPayload() {
        service.put(42L, "S1", Map.of("k", "v1"));
        service.put(42L, "S2", Map.of("k", "v2"));

        ConversationState got = service.get(42L).orElseThrow();
        assertThat(got.getState()).isEqualTo("S2");
        assertThat(got.getData()).containsEntry("k", "v2");
    }

    @Test
    void clear_deletesRow() {
        service.put(42L, "S1", Map.of("k", "v"));
        assertThat(service.get(42L)).isPresent();
        service.clear(42L);
        assertThat(service.get(42L)).isEmpty();
    }

    @Test
    void deleteStaleSince_removesOldRows() {
        service.put(42L, "S1", Map.of("k", "v"));
        // Sleep just enough for Instant.now() in deleteStaleSince to be after the row's updatedAt
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        int deleted = repo.deleteStaleSince(java.time.Instant.now());
        assertThat(deleted).isEqualTo(1);
        assertThat(service.get(42L)).isEmpty();
    }
}
```

- [ ] **Step 3: Implement ConversationState entity + repo + service (GREEN)**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/fsm/ConversationState.java`:

```java
package com.shop.delivery.bot.fsm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Per-user conversation state for multi-turn bot flows (FSM).
 *
 * <p>Backed by the {@code conversation_state} table created in V3__bot.sql.
 * Hibernate 6's {@code @JdbcTypeCode(SqlTypes.JSON)} maps {@code Map<String,Object>}
 * to a Postgres {@code jsonb} column natively — no extra dep needed.
 *
 * <p>P8 introduces the first FSM (rating comment capture). Future flows should
 * reuse this entity and the {@link ConversationStateService} API rather than
 * creating their own state tables.
 */
@Entity
@Table(name = "conversation_state")
public class ConversationState {

    @Id
    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Column(name = "state", nullable = false, length = 64)
    private String state;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb")
    private Map<String, Object> data;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public Long getTelegramUserId() { return telegramUserId; }
    public void setTelegramUserId(Long v) { this.telegramUserId = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> v) { this.data = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
```

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/fsm/ConversationStateRepository.java`:

```java
package com.shop.delivery.bot.fsm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface ConversationStateRepository extends JpaRepository<ConversationState, Long> {

    /**
     * Bulk-delete FSM rows older than {@code cutoff}. Used by a future scheduled
     * cleanup task (TTL ~30 min). For P8 we only call this in the IT; production
     * cleanup is a separate ticket (out of scope here).
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ConversationState s WHERE s.updatedAt < :cutoff")
    int deleteStaleSince(@Param("cutoff") Instant cutoff);
}
```

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/fsm/ConversationStateService.java`:

```java
package com.shop.delivery.bot.fsm;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Thin facade around {@link ConversationStateRepository}. P8 only uses
 * {@link #put}, {@link #get}, {@link #clear} — enough for the rating-comment FSM.
 *
 * <p>Generic on purpose: future flows pass any state name + any payload map.
 */
@Service
public class ConversationStateService {

    private final ConversationStateRepository repo;

    public ConversationStateService(ConversationStateRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Optional<ConversationState> get(Long userId) {
        return repo.findById(userId);
    }

    @Transactional
    public void put(Long userId, String state, Map<String, Object> data) {
        ConversationState cs = repo.findById(userId).orElseGet(() -> {
            ConversationState n = new ConversationState();
            n.setTelegramUserId(userId);
            return n;
        });
        cs.setState(state);
        cs.setData(data);
        repo.save(cs);
    }

    @Transactional
    public void clear(Long userId) {
        if (repo.existsById(userId)) repo.deleteById(userId);
    }
}
```

- [ ] **Step 4: Run tests — expect GREEN**

```bash
cd backend && ./mvnw -q -pl modules/bot -am test -Dtest=ConversationStateServiceTest
cd backend && ./mvnw -q -pl modules/bot -am verify -Dtest=ConversationStateServiceIT -DskipUnitTests
# Expected: 4 unit + 4 IT all pass
```

- [ ] **Step 5: Commit**

```bash
git add backend/modules/bot/src/main/java/com/shop/delivery/bot/fsm/ConversationState.java \
        backend/modules/bot/src/main/java/com/shop/delivery/bot/fsm/ConversationStateRepository.java \
        backend/modules/bot/src/main/java/com/shop/delivery/bot/fsm/ConversationStateService.java \
        backend/modules/bot/src/test/java/com/shop/delivery/bot/fsm/ConversationStateServiceTest.java \
        backend/modules/bot/src/test/java/com/shop/delivery/bot/fsm/ConversationStateServiceIT.java
git commit -m "feat(bot): add ConversationStateService FSM scaffolding + JSONB round-trip IT"
```

**Acceptance:**
- 4 unit tests + 4 IT pass
- JSONB round-trip verified (Map → jsonb → Map preserves String / Integer / Boolean values)
- `put` creates OR overwrites; `clear` is idempotent (no error if row missing)
- `deleteStaleSince` correctly cleans old rows

---

## TASK 6: RatingPromptBuilder + extend OrderAssignedNotifier.onOrderDelivered (TDD, 1 commit)

**Files:**
- Create: `backend/modules/notification/src/main/java/com/shop/delivery/notification/RatingPromptBuilder.java`
- Modify: `backend/modules/notification/src/main/java/com/shop/delivery/notification/OrderAssignedNotifier.java`
- Create: `backend/modules/notification/src/test/java/com/shop/delivery/notification/RatingPromptBuilderTest.java`
- Modify (or create if absent): `backend/modules/notification/src/test/java/com/shop/delivery/notification/OrderAssignedNotifierTest.java`

This extends the existing notifier with an idempotent rating prompt. The shipper-side "đã hoàn thành" stays unchanged. The customer-side message gains an inline keyboard.

- [ ] **Step 1: Write failing RatingPromptBuilderTest (RED)**

Create `backend/modules/notification/src/test/java/com/shop/delivery/notification/RatingPromptBuilderTest.java`:

```java
package com.shop.delivery.notification;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RatingPromptBuilderTest {

    private final RatingPromptBuilder builder = new RatingPromptBuilder();

    @Test
    void build_returnsKeyboardWithFiveStarsAndSkip() {
        UUID orderId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        InlineKeyboardMarkup kb = builder.build(orderId);

        List<List<InlineKeyboardButton>> rows = kb.getKeyboard();
        assertThat(rows).hasSize(2);

        List<InlineKeyboardButton> starRow = rows.get(0);
        assertThat(starRow).hasSize(5);
        for (int i = 0; i < 5; i++) {
            int expectedStars = i + 1;
            assertThat(starRow.get(i).getText()).isEqualTo("⭐".repeat(expectedStars));
            assertThat(starRow.get(i).getCallbackData())
                .isEqualTo("RATE:" + orderId + ":" + expectedStars);
            assertThat(starRow.get(i).getCallbackData().getBytes().length)
                .as("callback_data must be ≤ 64 bytes (Telegram limit)")
                .isLessThanOrEqualTo(64);
        }

        List<InlineKeyboardButton> skipRow = rows.get(1);
        assertThat(skipRow).hasSize(1);
        assertThat(skipRow.get(0).getText()).isEqualTo("Bỏ qua");
        assertThat(skipRow.get(0).getCallbackData()).isEqualTo("RATE_SKIP:" + orderId);
    }
}
```

- [ ] **Step 2: Run — expect compile fail**

```bash
cd backend && ./mvnw -q -pl modules/notification -am test -Dtest=RatingPromptBuilderTest
```

- [ ] **Step 3: Implement RatingPromptBuilder (GREEN)**

Create `backend/modules/notification/src/main/java/com/shop/delivery/notification/RatingPromptBuilder.java`:

```java
package com.shop.delivery.notification;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the inline keyboard sent to the customer after an order is DELIVERED.
 * Layout:
 *   Row 1: [⭐] [⭐⭐] [⭐⭐⭐] [⭐⭐⭐⭐] [⭐⭐⭐⭐⭐]
 *   Row 2: [Bỏ qua]
 *
 * <p>callback_data format:
 *   - {@code RATE:<orderId>:<stars>}  (43 bytes — well under 64-byte Telegram limit)
 *   - {@code RATE_SKIP:<orderId>}     (46 bytes)
 */
@Component
public class RatingPromptBuilder {

    public InlineKeyboardMarkup build(UUID orderId) {
        List<InlineKeyboardButton> starRow = new ArrayList<>(5);
        for (int s = 1; s <= 5; s++) {
            starRow.add(InlineKeyboardButton.builder()
                .text("⭐".repeat(s))
                .callbackData("RATE:" + orderId + ":" + s)
                .build());
        }
        InlineKeyboardButton skip = InlineKeyboardButton.builder()
            .text("Bỏ qua")
            .callbackData("RATE_SKIP:" + orderId)
            .build();

        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        kb.setKeyboard(List.of(starRow, List.of(skip)));
        return kb;
    }
}
```

- [ ] **Step 4: Run — expect GREEN**

```bash
cd backend && ./mvnw -q -pl modules/notification -am test -Dtest=RatingPromptBuilderTest
```

- [ ] **Step 5: Extend OrderAssignedNotifier.onOrderDelivered (RED first)**

Add a test method to `OrderAssignedNotifierTest` (if the file doesn't exist, create it).

Append to (or create) `backend/modules/notification/src/test/java/com/shop/delivery/notification/OrderAssignedNotifierTest.java`:

```java
package com.shop.delivery.notification;

import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.repository.RatingRepository;
import com.shop.delivery.delivery.service.event.OrderDeliveredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAssignedNotifierTest {

    @Mock BotSender bot;
    @Mock RatingPromptBuilder ratingPromptBuilder;
    @Mock RatingRepository ratingRepo;

    @InjectMocks OrderAssignedNotifier notifier;

    @Test
    void onOrderDelivered_notYetRated_sendsKeyboardToCustomer() {
        UUID orderId = UUID.randomUUID();
        OrderDeliveredEvent e = new OrderDeliveredEvent(
            UUID.randomUUID(), orderId, "DH001", 2001L, 1001L);

        when(ratingRepo.existsByOrderId(orderId)).thenReturn(false);
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        when(ratingPromptBuilder.build(orderId)).thenReturn(kb);

        notifier.onOrderDelivered(e);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(captor.capture());
        assertThat(captor.getValue().getChatId()).isEqualTo("1001");
        assertThat(captor.getValue().getText()).contains("DH001").contains("đánh giá");
        assertThat(captor.getValue().getReplyMarkup()).isSameAs(kb);

        // Shipper side: existing thank-you must still fire
        verify(bot).sendText(2001L, "✅ Hoàn thành đơn DH001. Chúc bạn ngày làm việc tốt lành!");
    }

    @Test
    void onOrderDelivered_alreadyRated_skipsKeyboard() {
        UUID orderId = UUID.randomUUID();
        OrderDeliveredEvent e = new OrderDeliveredEvent(
            UUID.randomUUID(), orderId, "DH002", 2001L, 1001L);

        when(ratingRepo.existsByOrderId(orderId)).thenReturn(true);

        notifier.onOrderDelivered(e);

        // No SendMessage to customer
        verify(bot, never()).execute(any(SendMessage.class));
        // But shipper thank-you still happens
        verify(bot).sendText(2001L, "✅ Hoàn thành đơn DH002. Chúc bạn ngày làm việc tốt lành!");
    }
}
```

- [ ] **Step 6: Modify OrderAssignedNotifier.onOrderDelivered (GREEN)**

Replace the existing `onOrderDelivered` method in `backend/modules/notification/src/main/java/com/shop/delivery/notification/OrderAssignedNotifier.java` with the version below. Also update the constructor and field declarations to inject the new dependencies.

```java
package com.shop.delivery.notification;

import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.repository.RatingRepository;
import com.shop.delivery.delivery.service.event.OrderAcceptedEvent;
import com.shop.delivery.delivery.service.event.OrderAssignedEvent;
import com.shop.delivery.delivery.service.event.OrderDeliveredEvent;
import com.shop.delivery.delivery.service.event.OrderRejectedEvent;
import com.shop.delivery.delivery.service.event.OrderStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
public class OrderAssignedNotifier {

    private static final Logger log = LoggerFactory.getLogger(OrderAssignedNotifier.class);

    private final BotSender bot;
    private final RatingPromptBuilder ratingPromptBuilder;
    private final RatingRepository ratingRepo;

    public OrderAssignedNotifier(BotSender bot,
                                 RatingPromptBuilder ratingPromptBuilder,
                                 RatingRepository ratingRepo) {
        this.bot = bot;
        this.ratingPromptBuilder = ratingPromptBuilder;
        this.ratingRepo = ratingRepo;
    }

    // [keep existing onOrderAssigned / onOrderAccepted / onOrderRejected / onOrderStarted unchanged]

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderDelivered(OrderDeliveredEvent e) {
        // 1. Always notify the shipper (unchanged behavior).
        bot.sendText(e.shipperId(), "✅ Hoàn thành đơn " + e.orderCode() + ". Chúc bạn ngày làm việc tốt lành!");

        // 2. Send the customer a rating prompt — but ONLY if not already rated.
        //    Defence-in-depth: AFTER_COMMIT could in theory fire twice (it shouldn't,
        //    but a manual replay or test harness might trigger it). The UNIQUE constraint
        //    on rating.order_id is the ultimate guard; this check just keeps the UX clean.
        if (ratingRepo.existsByOrderId(e.orderId())) {
            log.debug("Order {} already rated — skipping rating prompt", e.orderId());
            return;
        }

        InlineKeyboardMarkup kb = ratingPromptBuilder.build(e.orderId());
        SendMessage msg = SendMessage.builder()
            .chatId(String.valueOf(e.customerId()))
            .text("✅ Đơn " + e.orderCode() + " đã giao xong.\n⭐ Hãy đánh giá shipper:")
            .replyMarkup(kb)
            .build();
        bot.execute(msg);
        log.info("Sent rating prompt to customer {} for order {}", e.customerId(), e.orderCode());
    }
    // [end of file]
}
```

> **Important — keep the other handlers (`onOrderAssigned`, `onOrderAccepted`, `onOrderRejected`, `onOrderStarted`) exactly as they are today.** The constructor change is the only structural modification outside `onOrderDelivered`. Make sure existing tests for those other handlers (if any) still compile and pass.

- [ ] **Step 7: Run tests — GREEN**

```bash
cd backend && ./mvnw -q -pl modules/notification -am test -Dtest=RatingPromptBuilderTest,OrderAssignedNotifierTest
```

- [ ] **Step 8: Commit**

```bash
git add backend/modules/notification/src/main/java/com/shop/delivery/notification/RatingPromptBuilder.java \
        backend/modules/notification/src/main/java/com/shop/delivery/notification/OrderAssignedNotifier.java \
        backend/modules/notification/src/test/java/com/shop/delivery/notification/RatingPromptBuilderTest.java \
        backend/modules/notification/src/test/java/com/shop/delivery/notification/OrderAssignedNotifierTest.java
git commit -m "feat(notification): send rating keyboard on OrderDeliveredEvent (idempotent)"
```

**Acceptance:**
- `RatingPromptBuilderTest` (1 case) passes — 5 stars + skip row + callback data under 64 bytes
- `OrderAssignedNotifierTest.onOrderDelivered_*` (2 new cases) pass — prompt sent only when not yet rated
- Constructor injection includes `RatingRepository` + `RatingPromptBuilder`
- Other handlers in the notifier remain untouched

---

## WAVE 0 SMOKE CHECKPOINT

After Tasks 1–6 land, run a full backend build to catch wiring issues before moving to bot handlers:

- [ ] **Wave 0 smoke**

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang/backend
./mvnw -q verify -pl modules/delivery,modules/bot,modules/notification -am
# Expected: BUILD SUCCESS
# Tests: RatingRepositoryIT (6), RatingServiceTest (9), RatingControllerTest (4),
#        ConversationStateServiceTest (4), ConversationStateServiceIT (4),
#        RatingPromptBuilderTest (1), OrderAssignedNotifierTest (2 new + existing)
```

If green: Wave 0 complete. Proceed to Wave 1 (bot handlers).

---

## WAVE 1 — Bot rating handlers + router verification (Tasks 7–9)

Wave 1 wires the bot to the rating service via two handlers + an idempotency / handler-ordering check on the existing `UpdateRouter`.


## TASK 7: RatingCallbackHandler (TDD, 1 commit)

**Files:**
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/customer/RatingCallbackHandler.java`
- Create: `backend/modules/bot/src/test/java/com/shop/delivery/bot/handler/customer/RatingCallbackHandlerTest.java`

> **Note on cross-module deps:** `bot` already depends on `delivery` (existing `OrderOfferCallbackHandler` uses `DeliveryAssignmentService`). No pom change needed. Injecting `RatingService` is allowed.

- [ ] **Step 1: Write failing test (RED)**

Create `backend/modules/bot/src/test/java/com/shop/delivery/bot/handler/customer/RatingCallbackHandlerTest.java`:

```java
package com.shop.delivery.bot.handler.customer;

import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.entity.Rating;
import com.shop.delivery.delivery.service.RatingService;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingCallbackHandlerTest {

    @Mock RatingService ratingService;
    @Mock ConversationStateService conv;
    @Mock BotSender sender;

    @InjectMocks RatingCallbackHandler handler;

    private final UUID orderId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private final Long customerId = 1001L;
    private final Long chatId = 1001L;
    private final Integer messageId = 42;

    @Test
    void canHandle_returnsTrue_forRatePrefix() {
        assertThat(handler.canHandle(callback("RATE:" + orderId + ":5"))).isTrue();
        assertThat(handler.canHandle(callback("RATE_SKIP:" + orderId))).isTrue();
    }

    @Test
    void canHandle_returnsFalse_forOtherPrefixes() {
        assertThat(handler.canHandle(callback("ACCEPT_ORDER:abc"))).isFalse();
        assertThat(handler.canHandle(callback("REJECT_ORDER:abc"))).isFalse();
        assertThat(handler.canHandle(callback("RANDOM"))).isFalse();
    }

    @Test
    void canHandle_returnsFalse_forNonCallbackUpdate() {
        Update u = new Update();
        // no callbackQuery
        assertThat(handler.canHandle(u)).isFalse();
    }

    @Test
    void handle_validRate_persistsAndOpensFsm() {
        when(ratingService.rate(orderId, customerId, 5, null))
            .thenReturn(new Rating());

        handler.handle(callback("RATE:" + orderId + ":5"));

        verify(ratingService).rate(orderId, customerId, 5, null);

        // Keyboard removed
        ArgumentCaptor<EditMessageReplyMarkup> editCap = ArgumentCaptor.forClass(EditMessageReplyMarkup.class);
        verify(sender).execute(editCap.capture());
        assertThat(editCap.getValue().getChatId()).isEqualTo(chatId.toString());
        assertThat(editCap.getValue().getMessageId()).isEqualTo(messageId);

        // FSM opened
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(conv).put(eqLong(customerId), eqStr("CUSTOMER_RATING_COMMENT"), payload.capture());
        assertThat(payload.getValue()).containsEntry("orderId", orderId.toString());

        // Thanks sent
        verify(sender).sendText(eqLong(chatId), anyString());
    }

    @Test
    void handle_skip_removesKeyboardAndAnswersCallback_noDbWrite() {
        handler.handle(callback("RATE_SKIP:" + orderId));

        verify(sender).execute(any(EditMessageReplyMarkup.class));
        verify(sender).execute(any(AnswerCallbackQuery.class));
        verify(ratingService, never()).rate(any(), anyLong(), anyInt(), any());
        verify(conv, never()).put(anyLong(), anyString(), any());
    }

    @Test
    void handle_invalidStars_answersCallbackError_noDbWrite() {
        handler.handle(callback("RATE:" + orderId + ":99"));

        verify(ratingService, never()).rate(any(), anyLong(), anyInt(), any());
        verify(sender).execute(any(AnswerCallbackQuery.class));
    }

    @Test
    void handle_malformedUuid_answersCallbackError() {
        handler.handle(callback("RATE:not-a-uuid:5"));

        verify(ratingService, never()).rate(any(), anyLong(), anyInt(), any());
        verify(sender).execute(any(AnswerCallbackQuery.class));
    }

    @Test
    void handle_alreadyRated_answersCallbackAndRemovesKeyboard() {
        when(ratingService.rate(any(), anyLong(), anyInt(), any()))
            .thenThrow(new ConflictException("ALREADY_RATED", "Đơn đã được đánh giá rồi"));

        handler.handle(callback("RATE:" + orderId + ":5"));

        verify(sender).execute(any(AnswerCallbackQuery.class));
        verify(sender).execute(any(EditMessageReplyMarkup.class));
        verify(conv, never()).put(anyLong(), anyString(), any());
    }

    @Test
    void handle_domainException_answersCallback_noFsmOpen() {
        when(ratingService.rate(any(), anyLong(), anyInt(), any()))
            .thenThrow(new BusinessRuleException("ORDER_NOT_RATEABLE", "Chỉ đánh giá được sau khi đơn đã giao"));

        handler.handle(callback("RATE:" + orderId + ":5"));

        verify(sender).execute(any(AnswerCallbackQuery.class));
        verify(conv, never()).put(anyLong(), anyString(), any());
    }

    private Update callback(String data) {
        Update u = new Update();
        CallbackQuery cb = new CallbackQuery();
        cb.setId("cb-id-1");
        cb.setData(data);
        User from = new User(); from.setId(customerId);
        cb.setFrom(from);
        Message msg = new Message();
        msg.setMessageId(messageId);
        org.telegram.telegrambots.meta.api.objects.Chat chat = new org.telegram.telegrambots.meta.api.objects.Chat();
        chat.setId(chatId);
        msg.setChat(chat);
        cb.setMessage(msg);
        u.setCallbackQuery(cb);
        return u;
    }

    private static Long eqLong(Long v) { return org.mockito.ArgumentMatchers.eq(v); }
    private static String eqStr(String v) { return org.mockito.ArgumentMatchers.eq(v); }
}
```

- [ ] **Step 2: Run — expect compile fail**

```bash
cd backend && ./mvnw -q -pl modules/bot -am test -Dtest=RatingCallbackHandlerTest
```

- [ ] **Step 3: Implement RatingCallbackHandler (GREEN)**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/customer/RatingCallbackHandler.java`:

```java
package com.shop.delivery.bot.handler.customer;

import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.service.RatingService;
import com.shop.delivery.shared.exception.ConflictException;
import com.shop.delivery.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the 5-star + skip inline keyboard sent after order DELIVERED.
 *
 * <p>Callback data format:
 *   - {@code RATE:<orderId>:<stars>}  → persist rating, open FSM for optional comment
 *   - {@code RATE_SKIP:<orderId>}     → just remove the keyboard, no DB write
 *
 * <p>@Order(50): runs before generic command handlers but after FSM-bound text handlers.
 * Callback-data prefix is unique so order doesn't strictly matter, but explicit is better.
 */
@Component
@Order(50)
public class RatingCallbackHandler implements UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(RatingCallbackHandler.class);
    private static final String STATE_COMMENT = "CUSTOMER_RATING_COMMENT";

    private final RatingService ratingService;
    private final ConversationStateService conv;
    private final BotSender sender;

    public RatingCallbackHandler(RatingService ratingService,
                                 ConversationStateService conv,
                                 BotSender sender) {
        this.ratingService = ratingService;
        this.conv = conv;
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update u) {
        if (!u.hasCallbackQuery()) return false;
        String data = u.getCallbackQuery().getData();
        return data != null
            && (data.startsWith("RATE:") || data.startsWith("RATE_SKIP:"));
    }

    @Override
    public void handle(Update u) {
        CallbackQuery cb = u.getCallbackQuery();
        String data = cb.getData();
        Long customerId = cb.getFrom().getId();
        Long chatId = cb.getMessage().getChatId();
        Integer messageId = cb.getMessage().getMessageId();

        if (data.startsWith("RATE_SKIP:")) {
            removeKeyboard(chatId, messageId);
            sender.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(cb.getId())
                .text("Đã bỏ qua")
                .build());
            return;
        }

        // RATE:<orderId>:<stars>
        String[] parts = data.split(":");
        if (parts.length != 3) {
            answerError(cb.getId(), "Dữ liệu không hợp lệ");
            return;
        }

        UUID orderId;
        int stars;
        try {
            orderId = UUID.fromString(parts[1]);
            stars = Integer.parseInt(parts[2]);
            if (stars < 1 || stars > 5) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            log.warn("Bad RATE callback data: {}", data);
            answerError(cb.getId(), "Dữ liệu không hợp lệ");
            return;
        }

        try {
            ratingService.rate(orderId, customerId, stars, null);
        } catch (ConflictException ex) {
            sender.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(cb.getId())
                .text("Đơn đã được đánh giá rồi")
                .showAlert(true)
                .build());
            removeKeyboard(chatId, messageId);
            return;
        } catch (DomainException ex) {
            log.warn("Rating rejected for order {} customer {}: {}", orderId, customerId, ex.getMessage());
            sender.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(cb.getId())
                .text(ex.getMessage())
                .showAlert(true)
                .build());
            return;
        }

        // SUCCESS PATH
        removeKeyboard(chatId, messageId);
        sender.execute(AnswerCallbackQuery.builder()
            .callbackQueryId(cb.getId())
            .text("Cảm ơn bạn!")
            .build());

        conv.put(customerId, STATE_COMMENT, Map.of("orderId", orderId.toString()));
        sender.sendText(chatId, "Bạn có muốn nhập nhận xét? Gõ tin nhắn hoặc /skip để bỏ qua.");
    }

    private void removeKeyboard(Long chatId, Integer messageId) {
        InlineKeyboardMarkup empty = new InlineKeyboardMarkup();
        empty.setKeyboard(List.of());
        EditMessageReplyMarkup edit = EditMessageReplyMarkup.builder()
            .chatId(chatId.toString())
            .messageId(messageId)
            .replyMarkup(empty)
            .build();
        sender.execute(edit);
    }

    private void answerError(String cbId, String msg) {
        sender.execute(AnswerCallbackQuery.builder()
            .callbackQueryId(cbId)
            .text(msg)
            .showAlert(true)
            .build());
    }
}
```

- [ ] **Step 4: Run — expect GREEN**

```bash
cd backend && ./mvnw -q -pl modules/bot -am test -Dtest=RatingCallbackHandlerTest
```

- [ ] **Step 5: Commit**

```bash
git add backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/customer/RatingCallbackHandler.java \
        backend/modules/bot/src/test/java/com/shop/delivery/bot/handler/customer/RatingCallbackHandlerTest.java
git commit -m "feat(bot): add RatingCallbackHandler for RATE/RATE_SKIP callbacks"
```

**Acceptance:**
- 9 tests pass
- `RATE_SKIP:` removes keyboard, never calls `ratingService.rate`
- `RATE:<uuid>:<n>` calls `ratingService.rate(orderId, customerId, n, null)` then opens FSM
- Invalid stars or malformed UUID → answer-callback with error, no DB write
- ConflictException ALREADY_RATED → removes keyboard + does NOT open FSM
- Other DomainException → does NOT remove keyboard (user may need to retry)
- Class-level `@Order(50)` annotation present

---

## TASK 8: RatingCommentHandler (TDD, 1 commit)

**Files:**
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/customer/RatingCommentHandler.java`
- Create: `backend/modules/bot/src/test/java/com/shop/delivery/bot/handler/customer/RatingCommentHandlerTest.java`

This handler is FSM-state-gated — its `canHandle` returns true ONLY when the user has `state == "CUSTOMER_RATING_COMMENT"`. Annotated `@Order(0)` so it runs before any command/free-text handler.

- [ ] **Step 1: Write failing test (RED)**

Create `backend/modules/bot/src/test/java/com/shop/delivery/bot/handler/customer/RatingCommentHandlerTest.java`:

```java
package com.shop.delivery.bot.handler.customer;

import com.shop.delivery.bot.fsm.ConversationState;
import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.service.RatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingCommentHandlerTest {

    @Mock ConversationStateService conv;
    @Mock RatingService ratingService;
    @Mock BotSender sender;

    @InjectMocks RatingCommentHandler handler;

    private final Long customerId = 1001L;
    private final UUID orderId = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void canHandle_returnsFalse_whenNoMessage() {
        Update u = new Update();
        assertThat(handler.canHandle(u)).isFalse();
    }

    @Test
    void canHandle_returnsFalse_whenNoFsmState() {
        when(conv.get(customerId)).thenReturn(Optional.empty());
        assertThat(handler.canHandle(textUpdate("hello"))).isFalse();
    }

    @Test
    void canHandle_returnsFalse_whenWrongFsmState() {
        when(conv.get(customerId)).thenReturn(Optional.of(stateOf("OTHER_STATE")));
        assertThat(handler.canHandle(textUpdate("hello"))).isFalse();
    }

    @Test
    void canHandle_returnsTrue_whenInRatingCommentState() {
        when(conv.get(customerId)).thenReturn(Optional.of(stateOf("CUSTOMER_RATING_COMMENT")));
        assertThat(handler.canHandle(textUpdate("Giao nhanh"))).isTrue();
    }

    @Test
    void handle_skipCommand_clearsFsmAndThanks() {
        when(conv.get(customerId)).thenReturn(Optional.of(stateOf("CUSTOMER_RATING_COMMENT")));

        handler.handle(textUpdate("/skip"));

        verify(conv).clear(customerId);
        verify(ratingService, never()).updateComment(any(), anyLong(), anyString());
        verify(sender).sendText(customerId, "Đã ghi nhận đánh giá. Cảm ơn bạn!");
    }

    @Test
    void handle_skipCommand_isCaseInsensitive() {
        when(conv.get(customerId)).thenReturn(Optional.of(stateOf("CUSTOMER_RATING_COMMENT")));
        handler.handle(textUpdate("/SKIP"));
        verify(conv).clear(customerId);
        verify(ratingService, never()).updateComment(any(), anyLong(), anyString());
    }

    @Test
    void handle_freeText_persistsCommentClearsFsm() {
        when(conv.get(customerId)).thenReturn(Optional.of(stateOf("CUSTOMER_RATING_COMMENT")));

        handler.handle(textUpdate("Giao nhanh, thái độ tốt"));

        verify(ratingService).updateComment(orderId, customerId, "Giao nhanh, thái độ tốt");
        verify(conv).clear(customerId);
        verify(sender).sendText(customerId, "Cảm ơn nhận xét của bạn!");
    }

    @Test
    void handle_trimsWhitespace() {
        when(conv.get(customerId)).thenReturn(Optional.of(stateOf("CUSTOMER_RATING_COMMENT")));

        handler.handle(textUpdate("   ok   "));

        verify(ratingService).updateComment(orderId, customerId, "ok");
    }

    @Test
    void handle_emptyTextAfterTrim_doesNotCallService() {
        when(conv.get(customerId)).thenReturn(Optional.of(stateOf("CUSTOMER_RATING_COMMENT")));

        handler.handle(textUpdate("    "));

        verify(ratingService, never()).updateComment(any(), anyLong(), anyString());
        // FSM should still close — nothing to capture
        verify(conv).clear(customerId);
    }

    // ---- helpers ----

    private Update textUpdate(String text) {
        Update u = new Update();
        Message msg = new Message();
        msg.setText(text);
        User from = new User(); from.setId(customerId);
        msg.setFrom(from);
        Chat chat = new Chat(); chat.setId(customerId);
        msg.setChat(chat);
        u.setMessage(msg);
        return u;
    }

    private ConversationState stateOf(String name) {
        ConversationState s = new ConversationState();
        s.setTelegramUserId(customerId);
        s.setState(name);
        s.setData(Map.of("orderId", orderId.toString()));
        return s;
    }
}
```

- [ ] **Step 2: Run — expect compile fail**

```bash
cd backend && ./mvnw -q -pl modules/bot -am test -Dtest=RatingCommentHandlerTest
```

- [ ] **Step 3: Implement RatingCommentHandler (GREEN)**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/customer/RatingCommentHandler.java`:

```java
package com.shop.delivery.bot.handler.customer;

import com.shop.delivery.bot.fsm.ConversationState;
import com.shop.delivery.bot.fsm.ConversationStateService;
import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.service.RatingService;
import com.shop.delivery.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;
import java.util.UUID;

/**
 * FSM-state-gated text handler. canHandle returns true only when the sender is
 * in {@code CUSTOMER_RATING_COMMENT} state. Captures one text message as the
 * rating comment, or accepts {@code /skip} to abort the comment capture.
 *
 * <p>@Order(0): MUST run before command/free-text handlers so a user typing
 * "/help" while in this state doesn't fall through to the help handler.
 * (Confirmed by ConversationStateServiceIT — Spring honors @Order on injected
 * {@code List<UpdateHandler>}.)
 */
@Component
@Order(0)
public class RatingCommentHandler implements UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(RatingCommentHandler.class);
    private static final String STATE = "CUSTOMER_RATING_COMMENT";

    private final ConversationStateService conv;
    private final RatingService ratingService;
    private final BotSender sender;

    public RatingCommentHandler(ConversationStateService conv,
                                RatingService ratingService,
                                BotSender sender) {
        this.conv = conv;
        this.ratingService = ratingService;
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update u) {
        if (u == null || u.getMessage() == null || u.getMessage().getText() == null) return false;
        if (u.getMessage().getFrom() == null) return false;
        Long userId = u.getMessage().getFrom().getId();
        return conv.get(userId)
            .map(s -> STATE.equals(s.getState()))
            .orElse(false);
    }

    @Override
    public void handle(Update u) {
        Long userId = u.getMessage().getFrom().getId();
        Long chatId = u.getMessage().getChatId();
        String raw = u.getMessage().getText();
        String text = raw == null ? "" : raw.trim();

        if ("/skip".equalsIgnoreCase(text)) {
            conv.clear(userId);
            sender.sendText(chatId, "Đã ghi nhận đánh giá. Cảm ơn bạn!");
            return;
        }

        if (text.isEmpty()) {
            // No comment provided — close FSM, don't bother service.
            conv.clear(userId);
            return;
        }

        Optional<ConversationState> stateOpt = conv.get(userId);
        if (stateOpt.isEmpty()) {
            // Defensive: canHandle said yes but the state vanished — bail.
            return;
        }
        Object orderIdRaw = stateOpt.get().getData() == null ? null : stateOpt.get().getData().get("orderId");
        if (!(orderIdRaw instanceof String s)) {
            log.warn("FSM state missing orderId for user {}", userId);
            conv.clear(userId);
            return;
        }

        try {
            UUID orderId = UUID.fromString(s);
            ratingService.updateComment(orderId, userId, text);
            conv.clear(userId);
            sender.sendText(chatId, "Cảm ơn nhận xét của bạn!");
        } catch (IllegalArgumentException ex) {
            log.warn("FSM orderId not a UUID for user {}: {}", userId, s);
            conv.clear(userId);
        } catch (DomainException ex) {
            log.warn("updateComment rejected for user {}: {}", userId, ex.getMessage());
            sender.sendText(chatId, "Không thể cập nhật nhận xét: " + ex.getMessage());
            conv.clear(userId);
        }
    }
}
```

- [ ] **Step 4: Run — expect GREEN**

```bash
cd backend && ./mvnw -q -pl modules/bot -am test -Dtest=RatingCommentHandlerTest
```

- [ ] **Step 5: Commit**

```bash
git add backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/customer/RatingCommentHandler.java \
        backend/modules/bot/src/test/java/com/shop/delivery/bot/handler/customer/RatingCommentHandlerTest.java
git commit -m "feat(bot): add RatingCommentHandler — FSM-gated comment capture with /skip"
```

**Acceptance:**
- 9 tests pass
- `canHandle` returns true ONLY when FSM state == `CUSTOMER_RATING_COMMENT`
- `/skip` (case-insensitive) clears FSM, no service call
- Empty/whitespace text closes FSM but does NOT call `updateComment`
- Free text trimmed before persistence
- `@Order(0)` annotation present on class
- DomainException from `updateComment` does NOT leave the FSM open

---

## TASK 9: UpdateRouter @Order verification + Wave 1 smoke (1 commit)

**Files:**
- Create: `backend/modules/bot/src/test/java/com/shop/delivery/bot/router/UpdateRouterOrderIT.java`

This is the safety net for assumption A5 (research §18). Spring's `OrderUtils` should sort `@Order`-annotated beans when injected as `List<T>`. We verify with a tiny Spring test that registers two stub handlers with explicit `@Order` and confirms iteration order.

- [ ] **Step 1: Write failing test (RED) — or rather, expect it to pass on first run since Spring already does this; this test guards future regressions**

Create `backend/modules/bot/src/test/java/com/shop/delivery/bot/router/UpdateRouterOrderIT.java`:

```java
package com.shop.delivery.bot.router;

import com.shop.delivery.bot.handler.UpdateHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {
    UpdateRouterOrderIT.TestConfig.class
})
class UpdateRouterOrderIT {

    static final List<String> CALL_ORDER = new ArrayList<>();

    @Autowired List<UpdateHandler> handlers;

    @Test
    void springSortsByOrderAnnotation() {
        // The injected List<UpdateHandler> must be ordered: first(0) → middle(50) → last(100)
        List<String> classNames = handlers.stream()
            .map(h -> h.getClass().getSimpleName())
            .toList();
        int idxFirst  = classNames.indexOf("FirstHandler");
        int idxMiddle = classNames.indexOf("MiddleHandler");
        int idxLast   = classNames.indexOf("LastHandler");

        assertThat(idxFirst).isLessThan(idxMiddle);
        assertThat(idxMiddle).isLessThan(idxLast);
    }

    // WARNING fix from plan-checker: `@SpringBootTest(classes = TestConfig.class)` requires a
    // @SpringBootConfiguration (or @SpringBootApplication) on the target — a bare @Configuration
    // is rejected with "Unable to find a @SpringBootConfiguration". Matches the pattern used by
    // ProcessedUpdateServiceIT in this module.
    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestConfig {
        @Bean @Order(0)   UpdateHandler firstHandler()  { return new FirstHandler();  }
        @Bean @Order(50)  UpdateHandler middleHandler() { return new MiddleHandler(); }
        @Bean @Order(100) UpdateHandler lastHandler()   { return new LastHandler();   }
    }

    static class FirstHandler  implements UpdateHandler {
        @Override public boolean canHandle(Update u) { return false; }
        @Override public void handle(Update u) {}
    }
    static class MiddleHandler implements UpdateHandler {
        @Override public boolean canHandle(Update u) { return false; }
        @Override public void handle(Update u) {}
    }
    static class LastHandler   implements UpdateHandler {
        @Override public boolean canHandle(Update u) { return false; }
        @Override public void handle(Update u) {}
    }
}
```

> If this IT becomes painful because of Spring Boot test slice complexity, fall back to a plain unit test that constructs a List with three handlers and uses `org.springframework.core.annotation.AnnotationAwareOrderComparator.sort(list)` to verify the ordering. Either way, the goal is to lock the assumption.

- [ ] **Step 2: Run — expect GREEN (Spring already sorts; test exists to lock the behavior)**

```bash
cd backend && ./mvnw -q -pl modules/bot -am test -Dtest=UpdateRouterOrderIT
```

- [ ] **Step 3: Wave 1 backend smoke — full bot module**

```bash
cd backend && ./mvnw -q -pl modules/bot,modules/notification,modules/delivery -am verify
# Expected: BUILD SUCCESS, no test failures
```

- [ ] **Step 4: End-to-end manual smoke (optional but recommended)**

```bash
# Boot full app
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
docker compose -f infra/docker-compose.dev.yml up -d
sleep 5
cd backend/app
BOT_TOKEN=$REAL_BOT_TOKEN BOT_USERNAME=$REAL_BOT_USERNAME \
  ../mvnw spring-boot:run -Dspring-boot.run.profiles=dev > /tmp/p8_t9.log 2>&1 &
sleep 25

# In Telegram with a real customer account on a real DELIVERED order:
# 1. Trigger an order to DELIVERED (admin → delivery flow)
# 2. Customer chat receives keyboard "⭐ ⭐⭐ ⭐⭐⭐ ⭐⭐⭐⭐ ⭐⭐⭐⭐⭐ [Bỏ qua]"
# 3. Tap 5 stars → keyboard disappears, "Cảm ơn bạn!" toast, then "Bạn có muốn nhập nhận xét?..."
# 4. Type "Giao nhanh" → "Cảm ơn nhận xét của bạn!"
# 5. Verify DB: SELECT * FROM rating WHERE order_id = '<uuid>';
#    expect 1 row with stars=5, comment='Giao nhanh'
# 6. Verify shipper_profile: rating_avg and rating_count populated
```

- [ ] **Step 5: Commit**

```bash
git add backend/modules/bot/src/test/java/com/shop/delivery/bot/router/UpdateRouterOrderIT.java
git commit -m "test(bot): lock @Order behavior on injected UpdateHandler list"
```

**Acceptance:**
- `UpdateRouterOrderIT` passes — confirms Spring sorts `@Order`-annotated beans
- Full Wave 1 backend build passes
- Manual smoke (if run) confirms keyboard appears + rating persisted

---

## WAVE 1 SMOKE CHECKPOINT

Wave 1 closes the bot side of the rating flow. The system is now end-to-end functional for the customer rating loop. Wave 2 adds the admin-facing reports.

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang/backend
./mvnw -q verify -pl modules/delivery,modules/bot,modules/notification -am
```

Expected: BUILD SUCCESS with ~30+ rating/FSM tests passing.

---

## WAVE 2 — Admin dashboard + reports backend (Tasks 10–13)

Wave 2 adds the four dashboard + reports endpoints. Native SQL via interface projection (research §3). `groupBy` whitelist + 90-day cap enforced at controller.


## TASK 10: ReportsRepository + ReportsQueryService + DTOs + AdminDashboardController + AdminReportsController (1 commit)

**Files:**
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/repository/ReportsRepository.java`
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/ReportsQueryService.java`
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/dto/DashboardSummary.java`
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/dto/RevenuePoint.java`
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/dto/TopShipperRow.java`
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/dto/CancellationReport.java`
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/AdminDashboardController.java`
- Create: `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/AdminReportsController.java`

- [ ] **Step 1: Create the DTOs first**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/dto/RevenuePoint.java`:

```java
package com.shop.delivery.delivery.api.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One bucket of revenue / order-count data. Used by both the dashboard
 * 7-day mini-series and the reports endpoint with date / week buckets.
 */
public record RevenuePoint(
    LocalDate date,
    BigDecimal revenue,
    long orderCount
) {}
```

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/dto/TopShipperRow.java`:

```java
package com.shop.delivery.delivery.api.admin.dto;

import java.math.BigDecimal;

public record TopShipperRow(
    Long shipperId,
    String name,
    long deliveredCount,
    BigDecimal revenueGenerated,
    BigDecimal ratingAvg
) {}
```

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/dto/CancellationReport.java`:

```java
package com.shop.delivery.delivery.api.admin.dto;

import java.util.List;

public record CancellationReport(
    long totalOrders,
    long cancelledCount,
    double cancelRate,
    List<ReasonCount> byReason
) {
    public record ReasonCount(String reason, long count) {}
}
```

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/dto/DashboardSummary.java`:

```java
package com.shop.delivery.delivery.api.admin.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardSummary(
    OrdersTodaySummary ordersToday,
    BigDecimal revenueToday,
    long activeShippers,
    long newCustomersToday,
    List<RevenuePoint> revenueLast7Days
) {
    public record OrdersTodaySummary(
        long total,
        Map<String, Long> byStatus
    ) {}
}
```

- [ ] **Step 2: Create `ReportsRepository` with 8 native queries**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/repository/ReportsRepository.java`:

```java
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
```

- [ ] **Step 3: Create `ReportsQueryService` that maps raw projections → DTOs**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/ReportsQueryService.java`:

```java
package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.api.admin.dto.CancellationReport;
import com.shop.delivery.delivery.api.admin.dto.DashboardSummary;
import com.shop.delivery.delivery.api.admin.dto.RevenuePoint;
import com.shop.delivery.delivery.api.admin.dto.TopShipperRow;
import com.shop.delivery.delivery.repository.ReportsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ReportsQueryService {

    private final ReportsRepository repo;

    public ReportsQueryService(ReportsRepository repo) {
        this.repo = repo;
    }

    public DashboardSummary dashboardSummary() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long total = 0;
        for (var row : repo.ordersTodayByStatus()) {
            byStatus.put(row.getStatus(), row.getCnt());
            total += row.getCnt();
        }
        BigDecimal revenue = repo.revenueToday() == null ? BigDecimal.ZERO : repo.revenueToday();
        long activeShippers = repo.activeShippers();
        long newCustomers = repo.newCustomersToday();

        List<RevenuePoint> last7 = repo.revenueLast7Days().stream()
            .map(r -> new RevenuePoint(r.getBucketDate(), r.getRevenue(),
                                        r.getOrderCount() == null ? 0L : r.getOrderCount()))
            .toList();

        return new DashboardSummary(
            new DashboardSummary.OrdersTodaySummary(total, byStatus),
            revenue,
            activeShippers,
            newCustomers,
            last7
        );
    }

    public List<RevenuePoint> revenueSeries(LocalDate from, LocalDate to, String bucket) {
        return repo.revenueSeries(from, to, bucket).stream()
            .map(r -> new RevenuePoint(r.getBucketDate(), r.getRevenue(),
                                        r.getOrderCount() == null ? 0L : r.getOrderCount()))
            .toList();
    }

    public List<TopShipperRow> topShippers(LocalDate from, LocalDate to, int limit) {
        return repo.topShippers(from, to, limit).stream()
            .map(r -> new TopShipperRow(
                r.getShipperId(),
                buildName(r.getFirstName(), r.getLastName(), r.getUsername()),
                r.getDeliveredCount() == null ? 0L : r.getDeliveredCount(),
                r.getRevenueGenerated() == null ? BigDecimal.ZERO : r.getRevenueGenerated(),
                r.getRatingAvg() == null ? BigDecimal.ZERO : r.getRatingAvg()
            ))
            .toList();
    }

    public CancellationReport cancellation(LocalDate from, LocalDate to) {
        var totals = repo.cancellationTotals(from, to);
        long total = totals.getTotalOrders() == null ? 0L : totals.getTotalOrders();
        long cancelled = totals.getCancelledCount() == null ? 0L : totals.getCancelledCount();
        double rate = total == 0 ? 0.0 : (double) cancelled / (double) total;
        List<CancellationReport.ReasonCount> reasons = repo.cancellationByReason(from, to).stream()
            .map(r -> new CancellationReport.ReasonCount(r.getReason(), r.getCnt() == null ? 0L : r.getCnt()))
            .toList();
        return new CancellationReport(total, cancelled, rate, reasons);
    }

    private static String buildName(String first, String last, String username) {
        String f = first == null ? "" : first.trim();
        String l = last  == null ? "" : last.trim();
        String combined = (f + " " + l).trim();
        if (!combined.isEmpty()) return combined;
        return username != null && !username.isBlank() ? "@" + username : "Shipper";
    }
}
```

- [ ] **Step 4: Create `AdminDashboardController`**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/AdminDashboardController.java`:

```java
package com.shop.delivery.delivery.api.admin;

import com.shop.delivery.delivery.api.admin.dto.DashboardSummary;
import com.shop.delivery.delivery.service.ReportsQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminDashboardController {

    private final ReportsQueryService service;

    public AdminDashboardController(ReportsQueryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() {
        return service.dashboardSummary();
    }
}
```

- [ ] **Step 5: Create `AdminReportsController` with validation**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/AdminReportsController.java`:

```java
package com.shop.delivery.delivery.api.admin;

import com.shop.delivery.delivery.api.admin.dto.CancellationReport;
import com.shop.delivery.delivery.api.admin.dto.RevenuePoint;
import com.shop.delivery.delivery.api.admin.dto.TopShipperRow;
import com.shop.delivery.delivery.service.ReportsQueryService;
import com.shop.delivery.shared.exception.ValidationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminReportsController {

    private static final Set<String> ALLOWED_BUCKETS = Set.of("day", "week");
    private static final int MAX_RANGE_DAYS = 90;
    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final int MAX_TOP_LIMIT = 50;

    private final ReportsQueryService service;

    public AdminReportsController(ReportsQueryService service) {
        this.service = service;
    }

    @GetMapping("/revenue")
    public List<RevenuePoint> revenue(
        @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam("to")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(value = "groupBy", defaultValue = "day") String groupBy
    ) {
        requireRange(from, to);
        requireBucket(groupBy);
        return service.revenueSeries(from, to, groupBy);
    }

    @GetMapping("/top-shippers")
    public List<TopShipperRow> topShippers(
        @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam("to")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        requireRange(from, to);
        if (limit < 1 || limit > MAX_TOP_LIMIT) {
            throw new ValidationException("INVALID_LIMIT", "limit phải nằm trong khoảng 1..50");
        }
        return service.topShippers(from, to, limit);
    }

    @GetMapping("/cancellation")
    public CancellationReport cancellation(
        @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam("to")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        requireRange(from, to);
        return service.cancellation(from, to);
    }

    // ---------- validation helpers ----------

    private void requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ValidationException("MISSING_DATES", "Thiếu tham số from/to");
        }
        if (to.isBefore(from)) {
            throw new ValidationException("INVALID_RANGE", "Ngày kết thúc phải sau ngày bắt đầu");
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days > MAX_RANGE_DAYS) {
            throw new ValidationException("DATE_RANGE_TOO_LARGE",
                "Phạm vi tối đa " + MAX_RANGE_DAYS + " ngày");
        }
    }

    private void requireBucket(String bucket) {
        if (bucket == null || !ALLOWED_BUCKETS.contains(bucket)) {
            throw new ValidationException("INVALID_GROUP_BY",
                "groupBy phải là 'day' hoặc 'week'");
        }
    }
}
```

- [ ] **Step 6: Verify build (no tests yet — those are TASK 11/12)**

```bash
cd backend && ./mvnw -q -pl modules/delivery -am compile
# Expected: BUILD SUCCESS
```

- [ ] **Step 7: Commit**

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/repository/ReportsRepository.java \
        backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/ReportsQueryService.java \
        backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/dto/RevenuePoint.java \
        backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/dto/TopShipperRow.java \
        backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/dto/CancellationReport.java \
        backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/dto/DashboardSummary.java \
        backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/AdminDashboardController.java \
        backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/AdminReportsController.java
git commit -m "feat(reports): add ReportsRepository + 4 admin endpoints (dashboard + revenue + top-shippers + cancellation)"
```

**Acceptance:**
- Module compiles cleanly
- All 4 endpoints registered: `GET /api/admin/dashboard/summary`, `GET /api/admin/reports/revenue`, `GET /api/admin/reports/top-shippers`, `GET /api/admin/reports/cancellation`
- Class-level `@PreAuthorize("hasRole('SHOP_OWNER')")` on both controllers
- `groupBy` whitelist + 90-day cap + limit cap all enforced via `ValidationException`

---

## TASK 11: AdminDashboardControllerTest + AdminReportsControllerTest (1 commit)

**Files:**
- Create: `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/api/admin/AdminDashboardControllerTest.java`
- Create: `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/api/admin/AdminReportsControllerTest.java`

`@WebMvcTest` with mocked `ReportsQueryService` — verifies JSON shape + validation.

- [ ] **Step 1: Write AdminDashboardControllerTest**

Create `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/api/admin/AdminDashboardControllerTest.java`:

```java
package com.shop.delivery.delivery.api.admin;

import com.shop.delivery.delivery.api.admin.dto.DashboardSummary;
import com.shop.delivery.delivery.api.admin.dto.RevenuePoint;
import com.shop.delivery.delivery.service.ReportsQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDashboardController.class)
class AdminDashboardControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ReportsQueryService service;

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void summary_returnsJson() throws Exception {
        DashboardSummary expected = new DashboardSummary(
            new DashboardSummary.OrdersTodaySummary(7L,
                Map.of("PENDING", 2L, "DELIVERED", 5L)),
            new BigDecimal("1500000"),
            3L,
            12L,
            List.of(
                new RevenuePoint(LocalDate.of(2026, 5, 26), new BigDecimal("100000"), 1L),
                new RevenuePoint(LocalDate.of(2026, 5, 27), BigDecimal.ZERO, 0L)
            )
        );
        when(service.dashboardSummary()).thenReturn(expected);

        mvc.perform(get("/api/admin/dashboard/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ordersToday.total").value(7))
            .andExpect(jsonPath("$.ordersToday.byStatus.PENDING").value(2))
            .andExpect(jsonPath("$.revenueToday").value(1500000))
            .andExpect(jsonPath("$.activeShippers").value(3))
            .andExpect(jsonPath("$.newCustomersToday").value(12))
            .andExpect(jsonPath("$.revenueLast7Days[0].date").value("2026-05-26"))
            .andExpect(jsonPath("$.revenueLast7Days[0].revenue").value(100000))
            .andExpect(jsonPath("$.revenueLast7Days[1].revenue").value(0));
    }

    @Test
    void summary_unauthenticated_returns401or403() throws Exception {
        mvc.perform(get("/api/admin/dashboard/summary"))
            .andExpect(result -> {
                int sc = result.getResponse().getStatus();
                if (sc != 401 && sc != 403) {
                    throw new AssertionError("expected 401 or 403, got " + sc);
                }
            });
    }
}
```

- [ ] **Step 2: Write AdminReportsControllerTest**

Create `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/api/admin/AdminReportsControllerTest.java`:

```java
package com.shop.delivery.delivery.api.admin;

import com.shop.delivery.delivery.api.admin.dto.CancellationReport;
import com.shop.delivery.delivery.api.admin.dto.RevenuePoint;
import com.shop.delivery.delivery.api.admin.dto.TopShipperRow;
import com.shop.delivery.delivery.service.ReportsQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminReportsController.class)
class AdminReportsControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ReportsQueryService service;

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void revenue_dayBucket_returnsSeries() throws Exception {
        when(service.revenueSeries(any(), any(), eq("day"))).thenReturn(List.of(
            new RevenuePoint(LocalDate.of(2026, 5, 27), new BigDecimal("500000"), 3L)
        ));

        mvc.perform(get("/api/admin/reports/revenue")
                .param("from", "2026-05-27")
                .param("to",   "2026-05-30")
                .param("groupBy", "day"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].date").value("2026-05-27"))
            .andExpect(jsonPath("$[0].revenue").value(500000))
            .andExpect(jsonPath("$[0].orderCount").value(3));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void revenue_invalidGroupBy_returns400() throws Exception {
        mvc.perform(get("/api/admin/reports/revenue")
                .param("from", "2026-05-27")
                .param("to",   "2026-05-30")
                .param("groupBy", "month"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_GROUP_BY"));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void revenue_rangeTooLarge_returns400() throws Exception {
        mvc.perform(get("/api/admin/reports/revenue")
                .param("from", "2026-01-01")
                .param("to",   "2026-05-01")
                .param("groupBy", "day"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DATE_RANGE_TOO_LARGE"));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void revenue_toBeforeFrom_returns400() throws Exception {
        mvc.perform(get("/api/admin/reports/revenue")
                .param("from", "2026-05-30")
                .param("to",   "2026-05-27"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void topShippers_returnsRows() throws Exception {
        when(service.topShippers(any(), any(), eq(10))).thenReturn(List.of(
            new TopShipperRow(2001L, "Nguyen Van A", 12L, new BigDecimal("3000000"), new BigDecimal("4.80"))
        ));

        mvc.perform(get("/api/admin/reports/top-shippers")
                .param("from", "2026-05-01")
                .param("to",   "2026-05-30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].shipperId").value(2001))
            .andExpect(jsonPath("$[0].name").value("Nguyen Van A"))
            .andExpect(jsonPath("$[0].deliveredCount").value(12))
            .andExpect(jsonPath("$[0].revenueGenerated").value(3000000))
            .andExpect(jsonPath("$[0].ratingAvg").value(4.80));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void topShippers_invalidLimit_returns400() throws Exception {
        mvc.perform(get("/api/admin/reports/top-shippers")
                .param("from", "2026-05-01")
                .param("to",   "2026-05-30")
                .param("limit", "999"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_LIMIT"));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void cancellation_returnsRateAndReasons() throws Exception {
        when(service.cancellation(any(), any())).thenReturn(
            new CancellationReport(100L, 7L, 0.07, List.of(
                new CancellationReport.ReasonCount("Khách huỷ", 5L),
                new CancellationReport.ReasonCount("Sai địa chỉ", 2L)
            ))
        );

        mvc.perform(get("/api/admin/reports/cancellation")
                .param("from", "2026-05-01")
                .param("to",   "2026-05-30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalOrders").value(100))
            .andExpect(jsonPath("$.cancelledCount").value(7))
            .andExpect(jsonPath("$.cancelRate").value(0.07))
            .andExpect(jsonPath("$.byReason[0].reason").value("Khách huỷ"))
            .andExpect(jsonPath("$.byReason[0].count").value(5));
    }

    @Test
    void anyEndpoint_unauthenticated_returns401or403() throws Exception {
        mvc.perform(get("/api/admin/reports/cancellation")
                .param("from", "2026-05-01")
                .param("to",   "2026-05-30"))
            .andExpect(result -> {
                int sc = result.getResponse().getStatus();
                if (sc != 401 && sc != 403) {
                    throw new AssertionError("expected 401 or 403, got " + sc);
                }
            });
    }
}
```

- [ ] **Step 3: Run tests**

```bash
cd backend && ./mvnw -q -pl modules/delivery -am test \
  -Dtest=AdminDashboardControllerTest,AdminReportsControllerTest
# Expected: 2 + 8 tests pass
```

- [ ] **Step 4: Commit**

```bash
git add backend/modules/delivery/src/test/java/com/shop/delivery/delivery/api/admin/AdminDashboardControllerTest.java \
        backend/modules/delivery/src/test/java/com/shop/delivery/delivery/api/admin/AdminReportsControllerTest.java
git commit -m "test(reports): add @WebMvcTest for AdminDashboard + AdminReports controllers"
```

**Acceptance:**
- 10 tests pass total
- Validation errors translate to `400` with `code` field (matching existing `GlobalExceptionHandler`)
- Auth-less requests get `401`/`403`
- JSON shape matches frontend types defined in Task 14

---

## TASK 12: ReportsRepositoryIT — seeded native SQL verification (1 commit)

**Files:**
- Create: `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/repository/ReportsRepositoryIT.java`

> **Pragmatic scope:** This IT seeds a minimal set of `orders`, `delivery_assignment`, `shipper_profile`, `telegram_user`, `status_history` rows directly via `TestEntityManager` and asserts the SQL returns the expected shape. We are NOT replaying full Flyway migrations — instead we let Hibernate `ddl-auto=create-drop` materialize ALL JPA entities from the entire `com.shop.delivery` tree, and rely on the existing entity-table mappings to produce compatible columns.
>
> **BLOCKER fix from plan-checker:** `@DataJpaTest` only scans entities in the test class's package, so it won't materialize cross-module tables (`orders`, `telegram_user`, `user_role`, `status_history`, `delivery_assignment`). Use `@SpringBootTest` with an explicit `@SpringBootConfiguration + @EntityScan(basePackages = "com.shop.delivery") + @EnableJpaRepositories(basePackages = "com.shop.delivery")` inner config (matches the `ProcessedUpdateServiceIT` pattern in the bot module). This way Hibernate generates `Order`, `TelegramUser`, `UserRole`, `StatusHistory`, `DeliveryAssignment`, `ShipperProfile`, `LocationPing`, `Rating`, and `ConversationState` from their JPA entities, and `em.persist(o)` for an `Order` instance works without an explicit table-of-tables migration.
>
> The Testcontainers config (`POSTGRES`, `@DynamicPropertySource`) does NOT live in `DeliveryTestcontainerBase` for this IT — it's inline in the inner `TestConfig`, because `@DataJpaTest`'s `@Import` of a base does NOT register the base's `@Container` lifecycle when the test isn't a `@DataJpaTest` itself.

- [ ] **Step 1: Write the IT**

Create `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/repository/ReportsRepositoryIT.java`:

```java
package com.shop.delivery.delivery.repository;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.order.entity.Order;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ReportsRepositoryIT.TestConfig.class)
@Testcontainers
@Transactional
class ReportsRepositoryIT {

    /**
     * Inner Spring Boot configuration: scans ALL of {@code com.shop.delivery} so Hibernate
     * generates every JPA entity's table (orders, telegram_user, user_role, status_history,
     * delivery_assignment, shipper_profile, location_ping, rating, conversation_state, payment...).
     * Without the broad {@code @EntityScan}, {@code @DataJpaTest} would only scan the test
     * package's entities and leave cross-module joins ("relation 'orders' does not exist").
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.shop.delivery")
    @EnableJpaRepositories(basePackages = "com.shop.delivery")
    static class TestConfig {}

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("delivery_reports_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired ReportsRepository repo;
    @PersistenceContext EntityManager em;

    @Test
    void revenueLast7Days_returnsSevenRowsEvenWhenSparse() {
        // Seed: one DELIVERED order today
        seedOrder(LocalDate.now(), new BigDecimal("500000"), OrderStatus.DELIVERED);

        List<ReportsRepository.RevenuePointRow> rows = repo.revenueLast7Days();

        assertThat(rows).hasSize(7);
        // last row is today
        assertThat(rows.get(6).getBucketDate()).isEqualTo(LocalDate.now());
        assertThat(rows.get(6).getRevenue()).isEqualByComparingTo("500000");
        assertThat(rows.get(6).getOrderCount()).isEqualTo(1L);
        // earlier rows are zero
        assertThat(rows.get(0).getRevenue()).isEqualByComparingTo("0");
        assertThat(rows.get(0).getOrderCount()).isEqualTo(0L);
    }

    @Test
    void revenueSeries_dayBucket_groupsByDate() {
        LocalDate today = LocalDate.now();
        seedOrder(today, new BigDecimal("100000"), OrderStatus.DELIVERED);
        seedOrder(today, new BigDecimal("200000"), OrderStatus.DELIVERED);
        seedOrder(today.minusDays(1), new BigDecimal("50000"), OrderStatus.DELIVERED);

        List<ReportsRepository.RevenuePointRow> rows = repo.revenueSeries(
            today.minusDays(2), today, "day");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getRevenue()).isEqualByComparingTo("50000");
        assertThat(rows.get(1).getRevenue()).isEqualByComparingTo("300000");
    }

    @Test
    void revenueSeries_weekBucket_groupsByIsoWeek() {
        LocalDate monday = LocalDate.of(2026, 5, 25); // Monday
        seedOrder(monday,            new BigDecimal("100000"), OrderStatus.DELIVERED);
        seedOrder(monday.plusDays(3), new BigDecimal("200000"), OrderStatus.DELIVERED);
        seedOrder(monday.plusDays(7), new BigDecimal("400000"), OrderStatus.DELIVERED); // next week

        List<ReportsRepository.RevenuePointRow> rows = repo.revenueSeries(
            monday, monday.plusDays(13), "week");

        assertThat(rows).hasSize(2);
        // Week 1: 300_000
        assertThat(rows.get(0).getRevenue()).isEqualByComparingTo("300000");
        // Week 2: 400_000
        assertThat(rows.get(1).getRevenue()).isEqualByComparingTo("400000");
    }

    @Test
    void revenueSeries_excludesNonDeliveredOrders() {
        LocalDate today = LocalDate.now();
        seedOrder(today, new BigDecimal("100000"), OrderStatus.DELIVERED);
        seedOrder(today, new BigDecimal("999999"), OrderStatus.CANCELLED);  // ignored

        List<ReportsRepository.RevenuePointRow> rows = repo.revenueSeries(
            today.minusDays(1), today, "day");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRevenue()).isEqualByComparingTo("100000");
    }

    @Test
    void cancellationTotals_countsTotalAndCancelled() {
        LocalDate today = LocalDate.now();
        seedOrder(today, new BigDecimal("100000"), OrderStatus.DELIVERED);
        seedOrder(today, new BigDecimal("100000"), OrderStatus.CANCELLED);
        seedOrder(today, new BigDecimal("100000"), OrderStatus.CANCELLED);

        var t = repo.cancellationTotals(today.minusDays(1), today);
        assertThat(t.getTotalOrders()).isEqualTo(3L);
        assertThat(t.getCancelledCount()).isEqualTo(2L);
    }

    @Test
    void emptyRange_returnsEmptyOrZero() {
        LocalDate today = LocalDate.now();
        List<ReportsRepository.RevenuePointRow> rows = repo.revenueSeries(
            today.minusDays(5), today.minusDays(4), "day");
        assertThat(rows).isEmpty();

        var t = repo.cancellationTotals(today.minusDays(5), today.minusDays(4));
        assertThat(t.getTotalOrders()).isEqualTo(0L);
        assertThat(t.getCancelledCount()).isEqualTo(0L);
    }

    // ---- helpers ----

    private void seedOrder(LocalDate date, BigDecimal total, OrderStatus status) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCode("DH-" + System.nanoTime());
        o.setCustomerId(1001L);
        o.setStatus(status);
        o.setPaymentMethod(PaymentMethod.COD);
        o.setPaymentStatus(PaymentStatus.PENDING);
        o.setSubtotal(total);
        o.setDeliveryFee(BigDecimal.ZERO);
        o.setTotal(total);
        // pickup / delivery / distance — required NOT NULL columns
        o.setPickupLat(new BigDecimal("10.7769"));
        o.setPickupLng(new BigDecimal("106.7009"));
        o.setDeliveryAddress("Test address");
        o.setDeliveryLat(new BigDecimal("10.7800"));
        o.setDeliveryLng(new BigDecimal("106.7050"));
        o.setDistanceKm(new BigDecimal("1.500"));
        // Inject created_at — Order extends BaseEntity which has @CreatedDate; we must override.
        // After persisting, update created_at via raw SQL to backdate.
        em.persist(o);
        em.flush();
        // `em` is the standard JPA EntityManager (NOT Spring Boot's TestEntityManager wrapper).
        // Use `em.createNativeQuery(...)` directly — there is no `getEntityManager()` indirection.
        em.createNativeQuery(
            "UPDATE orders SET created_at = :ts WHERE id = :id")
            .setParameter("ts", Instant.ofEpochSecond(date.toEpochSecond(java.time.LocalTime.NOON, ZoneOffset.UTC)))
            .setParameter("id", o.getId())
            .executeUpdate();
        em.clear();
    }
}
```

> **Note for the executor:** `ReportsRepositoryIT` requires the `Order` entity + `Order`-related enums to be on the classpath. They are (the `delivery` module already depends on `order`). For tests that exercise `delivery_assignment` joins (top-shippers) and `status_history` joins (cancellation-by-reason), the executor may need to seed those tables via native SQL too — that scope is left for the executor to expand if the smoke test reveals gaps. The minimum acceptance for this task is: `revenueLast7Days` returns 7 rows, `revenueSeries` buckets day + week correctly, `cancellationTotals` returns the right counts. The full `topShippers` query and `cancellationByReason` can be smoke-verified in manual end-to-end testing rather than in IT.

- [ ] **Step 2: Run IT**

```bash
cd backend && ./mvnw -q -pl modules/delivery -am verify -Dtest=ReportsRepositoryIT
# Expected: 6 tests pass
```

- [ ] **Step 3: Commit**

```bash
git add backend/modules/delivery/src/test/java/com/shop/delivery/delivery/repository/ReportsRepositoryIT.java
git commit -m "test(reports): add IT for revenue series + cancellation totals (day + week buckets)"
```

**Acceptance:**
- 6 tests pass
- `generate_series` confirmed to return 7 rows even with sparse data
- `DATE_TRUNC('week', ...)` confirmed to group ISO-Monday weeks correctly
- Non-DELIVERED orders excluded from revenue
- Empty range returns empty/zero (no NPE)

---

## TASK 13: Wave 2 backend smoke (1 commit — docs only, no code)

- [ ] **Step 1: Full backend verify**

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang/backend
./mvnw -q verify
```

Expected: BUILD SUCCESS, all P5/P6/P7/P8 tests green.

- [ ] **Step 2: HTTP smoke**

```bash
docker compose -f infra/docker-compose.dev.yml up -d
sleep 5
cd backend/app
BOT_TOKEN=dummy BOT_USERNAME=DummyBot \
  ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8089" > /tmp/p8_t13.log 2>&1 &
BOOT_PID=$!
for i in $(seq 1 90); do
  grep -q "Started Application" /tmp/p8_t13.log 2>/dev/null && break
  sleep 1
done

# Login as admin → get JWT
TOKEN=$(curl -s -X POST http://localhost:8089/api/admin/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@shop.local","password":"admin123"}' | jq -r .accessToken)

# Dashboard
curl -s http://localhost:8089/api/admin/dashboard/summary \
  -H "Authorization: Bearer $TOKEN" | jq .

# Revenue
curl -s "http://localhost:8089/api/admin/reports/revenue?from=2026-05-25&to=2026-06-01&groupBy=day" \
  -H "Authorization: Bearer $TOKEN" | jq .

# groupBy=invalid → 400
curl -s -o /tmp/err.json -w "%{http_code}\n" \
  "http://localhost:8089/api/admin/reports/revenue?from=2026-05-25&to=2026-06-01&groupBy=month" \
  -H "Authorization: Bearer $TOKEN"
cat /tmp/err.json | jq .

# Range too large → 400
curl -s -o /tmp/err2.json -w "%{http_code}\n" \
  "http://localhost:8089/api/admin/reports/revenue?from=2025-01-01&to=2026-06-01&groupBy=day" \
  -H "Authorization: Bearer $TOKEN"

# Top shippers
curl -s "http://localhost:8089/api/admin/reports/top-shippers?from=2026-05-01&to=2026-06-01&limit=10" \
  -H "Authorization: Bearer $TOKEN" | jq .

# Cancellation
curl -s "http://localhost:8089/api/admin/reports/cancellation?from=2026-05-01&to=2026-06-01" \
  -H "Authorization: Bearer $TOKEN" | jq .

# No auth → 401
curl -s -o /dev/null -w "%{http_code}\n" \
  http://localhost:8089/api/admin/dashboard/summary

kill $BOOT_PID 2>/dev/null
```

Expected:
- Dashboard returns `{ordersToday, revenueToday, activeShippers, newCustomersToday, revenueLast7Days[]}` with `revenueLast7Days.length === 7`
- Revenue with valid groupBy → 200 + array
- `groupBy=month` → 400 with `code=INVALID_GROUP_BY`
- `(to - from) > 90` → 400 with `code=DATE_RANGE_TOO_LARGE`
- No auth → 401 or 403

- [ ] **Step 3: No-op commit (or skip if nothing to commit)**

```bash
git status
# If clean (no new code in this task), proceed to Wave 3 without committing
```

**Acceptance:**
- Full `./mvnw verify` passes
- All 5 endpoints respond with expected shapes
- Validation paths return 400 with the documented `code`

---

## WAVE 2 SMOKE CHECKPOINT

Backend is complete. Reports + dashboard + rating loop all functional. Move to frontend.

---

## WAVE 3 — Frontend (Tasks 14–18)

Wave 3 installs Recharts, adds shared types, rewrites Dashboard, builds Reports page, wires routing.


## TASK 14: Install recharts + add reports types and API helper to @shop/shared (1 commit)

**Files:**
- Modify: `frontend/webadmin/package.json` (add recharts)
- Create: `frontend/shared/src/types/reports.ts`
- Modify: `frontend/shared/src/types/index.ts`
- Create: `frontend/shared/src/api/reports.ts`
- Modify: `frontend/shared/src/api/index.ts`

- [ ] **Step 1: Install recharts**

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang/frontend/webadmin
pnpm add recharts@^3.8.1
```

Verify `frontend/webadmin/package.json` `dependencies` now includes `"recharts": "^3.8.1"`.

- [ ] **Step 2: Create shared types**

Create `frontend/shared/src/types/reports.ts`:

```typescript
// Reports + Dashboard DTOs — must match backend
// (com.shop.delivery.delivery.api.admin.dto.*)

export interface OrdersTodaySummary {
  total: number;
  byStatus: Partial<Record<OrderStatusKey, number>>;
}

export type OrderStatusKey =
  | 'PENDING'
  | 'CONFIRMED'
  | 'ASSIGNED'
  | 'DELIVERING'
  | 'DELIVERED'
  | 'RETURNED'
  | 'CANCELLED';

export interface DashboardSummary {
  ordersToday: OrdersTodaySummary;
  revenueToday: number;
  activeShippers: number;
  newCustomersToday: number;
  revenueLast7Days: RevenuePoint[];
}

export interface RevenuePoint {
  date: string;        // ISO date "yyyy-MM-dd"
  revenue: number;     // VND
  orderCount: number;
}

export interface TopShipperRow {
  shipperId: number;
  name: string;
  deliveredCount: number;
  revenueGenerated: number;
  ratingAvg: number;
}

export interface CancellationReport {
  totalOrders: number;
  cancelledCount: number;
  cancelRate: number;  // 0..1
  byReason: ReasonCount[];
}

export interface ReasonCount {
  reason: string;
  count: number;
}

export type GroupBy = 'day' | 'week';
```

Append to `frontend/shared/src/types/index.ts`:

```typescript
export * from './reports';
```

- [ ] **Step 3: Create API helper**

Create `frontend/shared/src/api/reports.ts`:

```typescript
import type { AxiosInstance } from 'axios';
import type {
  CancellationReport,
  DashboardSummary,
  GroupBy,
  RevenuePoint,
  TopShipperRow,
} from '../types/reports';

export interface ReportsRange {
  from: string;  // 'yyyy-MM-dd'
  to: string;    // 'yyyy-MM-dd'
}

export function reportsApi(client: AxiosInstance) {
  return {
    dashboardSummary: () =>
      client.get<DashboardSummary>('/api/admin/dashboard/summary').then(r => r.data),

    revenue: (range: ReportsRange, groupBy: GroupBy = 'day') =>
      client
        .get<RevenuePoint[]>('/api/admin/reports/revenue', {
          params: { from: range.from, to: range.to, groupBy },
        })
        .then(r => r.data),

    topShippers: (range: ReportsRange, limit = 10) =>
      client
        .get<TopShipperRow[]>('/api/admin/reports/top-shippers', {
          params: { from: range.from, to: range.to, limit },
        })
        .then(r => r.data),

    cancellation: (range: ReportsRange) =>
      client
        .get<CancellationReport>('/api/admin/reports/cancellation', {
          params: { from: range.from, to: range.to },
        })
        .then(r => r.data),
  };
}
```

Append to `frontend/shared/src/api/index.ts`:

```typescript
export * from './reports';
```

- [ ] **Step 4: Type-check + build**

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
pnpm --filter @shop/shared run build 2>&1 | tail -20  # if shared has a build step
pnpm --filter @shop/webadmin run type-check
```

- [ ] **Step 5: Commit**

```bash
git add frontend/webadmin/package.json \
        frontend/webadmin/pnpm-lock.yaml \
        pnpm-lock.yaml \
        frontend/shared/src/types/reports.ts \
        frontend/shared/src/types/index.ts \
        frontend/shared/src/api/reports.ts \
        frontend/shared/src/api/index.ts
git commit -m "build(webadmin): install recharts + add reports types and API helpers to @shop/shared"
```

> If `pnpm-lock.yaml` doesn't change at the repo root, omit it from `git add`. If only the webadmin workspace lockfile changes, that's enough.

**Acceptance:**
- `recharts` listed in `frontend/webadmin/package.json` at version `^3.8.1`
- `type-check` passes
- Types exported from `@shop/shared` (verify in webadmin: `import { DashboardSummary } from '@shop/shared'` resolves)

---

## TASK 15: Rewrite DashboardPage with KPI cards + 7-day mini chart + top 3 shippers (1 commit)

**Files:**
- Rewrite: `frontend/webadmin/src/pages/DashboardPage.tsx`
- Create: `frontend/webadmin/src/components/charts/RevenueMiniChart.tsx`

- [ ] **Step 1: Create the mini chart component**

Create `frontend/webadmin/src/components/charts/RevenueMiniChart.tsx`:

```tsx
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatVnd } from '@shop/shared';
import type { RevenuePoint } from '@shop/shared';

interface Props { data: RevenuePoint[]; }

export function RevenueMiniChart({ data }: Props) {
  const fmtAxis = (v: number) =>
    v >= 1_000_000 ? `${(v / 1_000_000).toFixed(1)}tr` :
    v >= 1_000     ? `${(v / 1_000).toFixed(0)}k`     :
    `${v}`;

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 10, right: 16, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="date" tickFormatter={(d: string) => d.slice(5)} />
          <YAxis tickFormatter={fmtAxis} />
          <Tooltip
            formatter={(value: number, name: string) =>
              name === 'revenue'
                ? [formatVnd(value), 'Doanh thu']
                : [value, 'Số đơn']
            }
            labelFormatter={(label: string) => `Ngày ${label}`}
          />
          <Line type="monotone" dataKey="revenue" stroke="#16a34a" strokeWidth={2} dot={{ r: 3 }} />
          <Line type="monotone" dataKey="orderCount" stroke="#2563eb" strokeWidth={2} dot={{ r: 3 }} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
```

- [ ] **Step 2: Rewrite DashboardPage**

Replace `frontend/webadmin/src/pages/DashboardPage.tsx` with:

```tsx
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { reportsApi, formatVnd } from '@shop/shared';
import type { DashboardSummary, TopShipperRow } from '@shop/shared';
import { RevenueMiniChart } from '@/components/charts/RevenueMiniChart';

const reports = reportsApi(api);

export function DashboardPage() {
  const { data, isLoading, isError, refetch } = useQuery<DashboardSummary>({
    queryKey: ['admin', 'dashboard', 'summary'],
    queryFn: reports.dashboardSummary,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
    staleTime: 10_000,
  });

  // Top shippers: last 7 days, top 3
  const today = new Date();
  const sevenDaysAgo = new Date(today.getTime() - 6 * 86_400_000);
  const range = {
    from: toIso(sevenDaysAgo),
    to: toIso(today),
  };
  const { data: top } = useQuery<TopShipperRow[]>({
    queryKey: ['admin', 'dashboard', 'top-shippers', range.from, range.to],
    queryFn: () => reports.topShippers(range, 3),
    refetchInterval: 30_000,
    staleTime: 10_000,
  });

  if (isLoading) {
    return <p className="text-gray-600">Đang tải...</p>;
  }
  if (isError || !data) {
    return (
      <div>
        <p className="text-red-600">Không tải được dữ liệu Dashboard.</p>
        <button onClick={() => refetch()} className="mt-2 px-3 py-1 bg-gray-100 rounded">Thử lại</button>
      </div>
    );
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Tổng quan</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Đơn hôm nay"      value={data.ordersToday.total} />
        <StatCard label="Chờ xác nhận"     value={data.ordersToday.byStatus.PENDING ?? 0}  className="text-yellow-600" />
        <StatCard label="Đang giao"        value={data.ordersToday.byStatus.DELIVERING ?? 0} className="text-purple-600" />
        <StatCard label="Doanh thu hôm nay" value={formatVnd(data.revenueToday)}           className="text-green-600" />
        <StatCard label="Shipper đang hoạt động" value={data.activeShippers} />
        <StatCard label="Khách mới hôm nay" value={data.newCustomersToday} />
        <StatCard label="Đã giao hôm nay"   value={data.ordersToday.byStatus.DELIVERED ?? 0} className="text-green-700" />
        <StatCard label="Đã huỷ hôm nay"    value={data.ordersToday.byStatus.CANCELLED ?? 0} className="text-red-600" />
      </div>

      <div className="mt-6 grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="lg:col-span-2 bg-white rounded-lg shadow p-4">
          <h2 className="font-semibold mb-3">Doanh thu 7 ngày qua</h2>
          <RevenueMiniChart data={data.revenueLast7Days} />
        </div>

        <div className="bg-white rounded-lg shadow p-4">
          <h2 className="font-semibold mb-3">Top 3 shipper (7 ngày)</h2>
          {top && top.length > 0 ? (
            <ol className="space-y-3">
              {top.map((s, i) => (
                <li key={s.shipperId} className="flex justify-between items-center">
                  <div>
                    <p className="font-medium">{i + 1}. {s.name}</p>
                    <p className="text-sm text-gray-500">
                      {s.deliveredCount} đơn · ⭐ {Number(s.ratingAvg).toFixed(2)}
                    </p>
                  </div>
                  <p className="text-sm font-semibold text-green-700">
                    {formatVnd(s.revenueGenerated)}
                  </p>
                </li>
              ))}
            </ol>
          ) : (
            <p className="text-sm text-gray-500">Chưa có dữ liệu.</p>
          )}
        </div>
      </div>
    </div>
  );
}

function StatCard({
  label,
  value,
  className = '',
}: {
  label: string;
  value: number | string;
  className?: string;
}) {
  return (
    <div className="bg-white rounded-lg shadow p-4">
      <p className="text-sm text-gray-600">{label}</p>
      <p className={`text-2xl font-bold mt-1 ${className}`}>{value}</p>
    </div>
  );
}

function toIso(d: Date): string {
  // "yyyy-MM-dd" in local time (matches backend's LocalDate semantics)
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
```

- [ ] **Step 3: Type-check + build**

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
pnpm --filter @shop/webadmin run type-check
pnpm --filter @shop/webadmin run build
```

- [ ] **Step 4: Commit**

```bash
git add frontend/webadmin/src/pages/DashboardPage.tsx \
        frontend/webadmin/src/components/charts/RevenueMiniChart.tsx
git commit -m "feat(webadmin): rewrite DashboardPage with KPI cards + 7-day chart + top 3 shippers"
```

**Acceptance:**
- DashboardPage no longer references `/api/admin/orders?size=50` (placeholder removed)
- 8 KPI cards rendered + 7-day LineChart + top 3 shippers list
- Polling: `refetchInterval: 30000`
- Vietnamese labels everywhere
- `formatVnd` used for all currency

---

## TASK 16: Create ReportsPage with date-range picker + 3 charts (1 commit)

**Files:**
- Create: `frontend/webadmin/src/pages/ReportsPage.tsx`
- Create: `frontend/webadmin/src/components/charts/RevenueChart.tsx`
- Create: `frontend/webadmin/src/components/charts/TopShippersChart.tsx`
- Create: `frontend/webadmin/src/components/charts/CancellationChart.tsx`

- [ ] **Step 1: Create RevenueChart**

Create `frontend/webadmin/src/components/charts/RevenueChart.tsx`:

```tsx
import {
  CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { formatVnd } from '@shop/shared';
import type { RevenuePoint } from '@shop/shared';

export function RevenueChart({ data }: { data: RevenuePoint[] }) {
  const fmtAxis = (v: number) =>
    v >= 1_000_000 ? `${(v / 1_000_000).toFixed(1)}tr` :
    v >= 1_000     ? `${(v / 1_000).toFixed(0)}k`     :
    `${v}`;

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 10, right: 16, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="date" tickFormatter={(d: string) => d.slice(5)} />
          <YAxis tickFormatter={fmtAxis} />
          <Tooltip
            formatter={(value: number, name: string) =>
              name === 'revenue'
                ? [formatVnd(value), 'Doanh thu']
                : [value, 'Số đơn']
            }
            labelFormatter={(label: string) => `Ngày ${label}`}
          />
          <Legend formatter={(v) => v === 'revenue' ? 'Doanh thu' : 'Số đơn'} />
          <Line type="monotone" dataKey="revenue" stroke="#16a34a" strokeWidth={2} />
          <Line type="monotone" dataKey="orderCount" stroke="#2563eb" strokeWidth={2} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
```

- [ ] **Step 2: Create TopShippersChart**

Create `frontend/webadmin/src/components/charts/TopShippersChart.tsx`:

```tsx
import {
  Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import type { TopShipperRow } from '@shop/shared';

export function TopShippersChart({ data }: { data: TopShipperRow[] }) {
  // Recharts BarChart works best when each row has a short label
  const rows = data.map(r => ({
    name: r.name.length > 18 ? r.name.slice(0, 17) + '…' : r.name,
    deliveredCount: r.deliveredCount,
  }));

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} layout="vertical" margin={{ top: 10, right: 16, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis type="number" allowDecimals={false} />
          <YAxis dataKey="name" type="category" width={140} />
          <Tooltip formatter={(v: number) => [`${v} đơn`, 'Số đơn đã giao']} />
          <Bar dataKey="deliveredCount" fill="#2563eb" />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
```

- [ ] **Step 3: Create CancellationChart**

Create `frontend/webadmin/src/components/charts/CancellationChart.tsx`:

```tsx
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import type { ReasonCount } from '@shop/shared';

const COLORS = ['#ef4444', '#f97316', '#eab308', '#22c55e', '#3b82f6', '#a855f7', '#ec4899', '#14b8a6', '#84cc16', '#6366f1'];

export function CancellationChart({ data }: { data: ReasonCount[] }) {
  if (data.length === 0) {
    return <div className="h-72 w-full flex items-center justify-center text-gray-500">Không có dữ liệu huỷ trong khoảng này.</div>;
  }

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie data={data} dataKey="count" nameKey="reason" outerRadius={100} label>
            {data.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
          </Pie>
          <Tooltip formatter={(v: number) => [`${v} đơn`, 'Số lượng']} />
          <Legend />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}
```

- [ ] **Step 4: Create ReportsPage**

Create `frontend/webadmin/src/pages/ReportsPage.tsx`:

```tsx
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { format, subDays } from 'date-fns';
import { api } from '@/lib/api';
import { reportsApi, formatVnd } from '@shop/shared';
import type {
  CancellationReport,
  GroupBy,
  RevenuePoint,
  TopShipperRow,
} from '@shop/shared';
import { RevenueChart } from '@/components/charts/RevenueChart';
import { TopShippersChart } from '@/components/charts/TopShippersChart';
import { CancellationChart } from '@/components/charts/CancellationChart';

const reports = reportsApi(api);
const MAX_DAYS = 90;

export function ReportsPage() {
  const today = new Date();
  const [from, setFrom] = useState(format(subDays(today, 30), 'yyyy-MM-dd'));
  const [to, setTo]     = useState(format(today, 'yyyy-MM-dd'));
  const [groupBy, setGroupBy] = useState<GroupBy>('day');

  const maxDate = format(today, 'yyyy-MM-dd');
  const minDate = format(subDays(today, MAX_DAYS), 'yyyy-MM-dd');

  const range = { from, to };
  const rangeValid = from <= to && daysBetween(from, to) <= MAX_DAYS;

  const revenue = useQuery<RevenuePoint[]>({
    queryKey: ['admin', 'reports', 'revenue', from, to, groupBy],
    queryFn: () => reports.revenue(range, groupBy),
    enabled: rangeValid,
  });

  const top = useQuery<TopShipperRow[]>({
    queryKey: ['admin', 'reports', 'top-shippers', from, to],
    queryFn: () => reports.topShippers(range, 10),
    enabled: rangeValid,
  });

  const cancellation = useQuery<CancellationReport>({
    queryKey: ['admin', 'reports', 'cancellation', from, to],
    queryFn: () => reports.cancellation(range),
    enabled: rangeValid,
  });

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Báo cáo</h1>

      {/* Date-range picker */}
      <div className="bg-white rounded-lg shadow p-4 mb-6 flex flex-wrap gap-4 items-end">
        <div>
          <label htmlFor="from" className="block text-sm text-gray-600 mb-1">Từ ngày</label>
          <input
            id="from"
            type="date"
            value={from}
            min={minDate}
            max={to}
            onChange={e => setFrom(e.target.value)}
            className="border border-gray-300 rounded px-2 py-1"
          />
        </div>
        <div>
          <label htmlFor="to" className="block text-sm text-gray-600 mb-1">Đến ngày</label>
          <input
            id="to"
            type="date"
            value={to}
            min={from}
            max={maxDate}
            onChange={e => setTo(e.target.value)}
            className="border border-gray-300 rounded px-2 py-1"
          />
        </div>
        <div>
          <label htmlFor="groupBy" className="block text-sm text-gray-600 mb-1">Nhóm theo</label>
          <select
            id="groupBy"
            value={groupBy}
            onChange={e => setGroupBy(e.target.value as GroupBy)}
            className="border border-gray-300 rounded px-2 py-1"
          >
            <option value="day">Ngày</option>
            <option value="week">Tuần</option>
          </select>
        </div>
        {!rangeValid && (
          <p className="text-red-600 text-sm self-center">
            Khoảng thời gian không hợp lệ (tối đa {MAX_DAYS} ngày).
          </p>
        )}
      </div>

      {/* Revenue chart */}
      <section className="bg-white rounded-lg shadow p-4 mb-6">
        <h2 className="font-semibold mb-3">Doanh thu theo {groupBy === 'day' ? 'ngày' : 'tuần'}</h2>
        {revenue.isLoading ? <p>Đang tải...</p>
          : revenue.isError ? <p className="text-red-600">Lỗi tải doanh thu.</p>
          : <RevenueChart data={revenue.data ?? []} />}
      </section>

      {/* Top shippers */}
      <section className="bg-white rounded-lg shadow p-4 mb-6">
        <h2 className="font-semibold mb-3">Top shipper</h2>
        {top.isLoading ? <p>Đang tải...</p>
          : top.isError ? <p className="text-red-600">Lỗi tải dữ liệu shipper.</p>
          : <TopShippersChart data={top.data ?? []} />}
      </section>

      {/* Cancellation */}
      <section className="bg-white rounded-lg shadow p-4 mb-6">
        <h2 className="font-semibold mb-3">Tỷ lệ huỷ + lý do</h2>
        {cancellation.isLoading ? <p>Đang tải...</p>
          : cancellation.isError ? <p className="text-red-600">Lỗi tải dữ liệu huỷ.</p>
          : cancellation.data && (
              <>
                <p className="mb-3 text-sm text-gray-700">
                  Tổng đơn: <b>{cancellation.data.totalOrders}</b> · Đã huỷ: <b>{cancellation.data.cancelledCount}</b> · Tỷ lệ huỷ: <b>{(cancellation.data.cancelRate * 100).toFixed(1)}%</b>
                </p>
                <CancellationChart data={cancellation.data.byReason} />
              </>
            )}
      </section>
    </div>
  );
}

function daysBetween(from: string, to: string): number {
  const f = new Date(from);
  const t = new Date(to);
  return Math.floor((t.getTime() - f.getTime()) / 86_400_000);
}
```

- [ ] **Step 5: Type-check + build**

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
pnpm --filter @shop/webadmin run type-check
pnpm --filter @shop/webadmin run build
```

- [ ] **Step 6: Commit**

```bash
git add frontend/webadmin/src/pages/ReportsPage.tsx \
        frontend/webadmin/src/components/charts/RevenueChart.tsx \
        frontend/webadmin/src/components/charts/TopShippersChart.tsx \
        frontend/webadmin/src/components/charts/CancellationChart.tsx
git commit -m "feat(webadmin): add ReportsPage with revenue/top-shippers/cancellation charts"
```

**Acceptance:**
- ReportsPage compiles with no TypeScript errors
- 3 charts present, each in `<div className="h-72 w-full">` wrapper to avoid 0-px bug
- Date picker `min`/`max` constrains to last 90 days
- `groupBy` select switches between day/week
- `rangeValid` gate disables queries when range invalid (prevents 400 round-trip)

---

## TASK 17: Wire /reports route + Sidebar nav link (1 commit)

**Files:**
- Modify: `frontend/webadmin/src/App.tsx`
- Modify: `frontend/webadmin/src/components/Sidebar.tsx`

- [ ] **Step 1: Add the route**

Modify `frontend/webadmin/src/App.tsx` — add an import + a `<Route>` for `/reports`:

```tsx
// add to imports
import { ReportsPage } from './pages/ReportsPage';

// inside <Routes><Route element={<Layout />}>...</Route></Routes>, add:
<Route path="reports" element={<ReportsPage />} />
```

The exact placement: between `<Route path="shippers" .../>` and `<Route path="*" element={<NotFoundPage />} />`.

- [ ] **Step 2: Add the Sidebar entry**

Modify `frontend/webadmin/src/components/Sidebar.tsx` — extend the `ITEMS` array:

```tsx
const ITEMS = [
  { to: '/', label: 'Dashboard', icon: '📊' },
  { to: '/orders', label: 'Đơn hàng', icon: '📦' },
  { to: '/reports', label: 'Báo cáo', icon: '📈' },
  { to: '/products', label: 'Sản phẩm', icon: '🛍️' },
  { to: '/shippers', label: 'Shipper', icon: '🚴' },
];
```

- [ ] **Step 3: Type-check + build**

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
pnpm --filter @shop/webadmin run type-check
pnpm --filter @shop/webadmin run build
```

- [ ] **Step 4: Commit**

```bash
git add frontend/webadmin/src/App.tsx \
        frontend/webadmin/src/components/Sidebar.tsx
git commit -m "feat(webadmin): wire /reports route and add Báo cáo sidebar link"
```

**Acceptance:**
- Navigating to `/reports` renders ReportsPage
- Sidebar shows "Báo cáo" item with 📈 icon between "Đơn hàng" and "Sản phẩm"
- Active-link highlighting works (NavLink className pattern)

---

## TASK 18: Frontend smoke + final pre-merge verification (1 commit — docs only)

- [ ] **Step 1: Full root build**

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
pnpm -r run type-check
pnpm -r run build
```

- [ ] **Step 2: Dev-server smoke**

```bash
# Start backend + webadmin
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
docker compose -f infra/docker-compose.dev.yml up -d
sleep 5
cd backend/app
BOT_TOKEN=dummy BOT_USERNAME=DummyBot \
  ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev > /tmp/p8_t18_backend.log 2>&1 &
BACKEND_PID=$!
sleep 25

cd ../../frontend/webadmin
nohup pnpm dev > /tmp/p8_t18_webadmin.log 2>&1 &
WEBADMIN_PID=$!
sleep 5

# Visit http://localhost:5174:
# - log in as admin@shop.local / admin123
# - Verify Dashboard renders: 8 KPI cards + line chart with 7 data points + top 3 shippers list
# - Open browser DevTools Network tab: confirm /api/admin/dashboard/summary returns 200 every 30s
# - Click "Báo cáo" in sidebar
# - Verify ReportsPage:
#   - Date range picker constrains min=90d-ago, max=today
#   - Select "Tuần" → revenue line shows weekly buckets
#   - Try setting from > to → red "Khoảng thời gian không hợp lệ" message + no API call (Network tab)
#   - Try a wide range that simulates >90d (manually edit input) → backend returns 400 with DATE_RANGE_TOO_LARGE
# - Click on dashboard → confirm KPIs update after ~30s if new order is placed

kill $WEBADMIN_PID $BACKEND_PID 2>/dev/null
```

- [ ] **Step 3: Optional commit (only if anything changed during smoke)**

If smoke uncovers small fixes (typos, label tweaks), batch them into a single small commit:

```bash
git status
git add ...
git commit -m "fix(webadmin): smoke-test polish"
```

If nothing changed, no commit needed.

**Acceptance:**
- `pnpm -r build` succeeds for both `@shop/shared` (if it has a build) and `@shop/webadmin`
- Dashboard renders with 8 KPI cards + LineChart + top 3 list
- Reports renders with date picker + 3 charts
- Navigation between pages works
- Polling visible in Network tab on Dashboard
- 400 errors handled gracefully (red message, no crash)

---

## WAVE 3 SMOKE CHECKPOINT

Frontend complete. End-to-end flow verified. Move to final phase verification.

---

## FINAL PHASE VERIFICATION

After all 18 tasks land:

- [ ] **Backend full suite**
  ```bash
  cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang/backend
  ./mvnw -q verify
  ```
  Expected: BUILD SUCCESS, all tests green.

- [ ] **Frontend full build**
  ```bash
  cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
  pnpm -r run type-check
  pnpm -r run build
  ```

- [ ] **DB state check**
  ```bash
  docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
    -c "SELECT version FROM flyway_schema_history ORDER BY version DESC LIMIT 3"
  # Expected: V10, V9, V8
  docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d rating"
  docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d conversation_state"
  ```

- [ ] **End-to-end manual smoke (with real Telegram bot)**
  1. Place a test order through Mini App
  2. Admin confirms → assigns shipper
  3. Shipper Mini App: PICK_UP → STARTED → DELIVERED
  4. Customer Telegram: receives "⭐ Hãy đánh giá shipper" message with 5 stars + "Bỏ qua" buttons
  5. Tap "⭐⭐⭐⭐⭐" → keyboard disappears, "Cảm ơn bạn!" toast, then "Bạn có muốn nhập nhận xét?"
  6. Type "Giao nhanh, thái độ tốt" → "Cảm ơn nhận xét của bạn!"
  7. Verify DB:
     ```sql
     SELECT order_id, customer_id, shipper_id, stars, comment FROM rating ORDER BY id DESC LIMIT 1;
     -- Expected: 1 row with stars=5, comment='Giao nhanh, thái độ tốt'

     SELECT user_id, rating_avg, rating_count FROM shipper_profile WHERE user_id = <shipperId>;
     -- Expected: rating_avg ≈ 5.00, rating_count ≥ 1
     ```
  8. Visit `http://localhost:5174` → Dashboard shows the just-delivered order
  9. Visit `/reports` → "Tỷ lệ huỷ" pie chart updates if any cancellations exist

- [ ] **Idempotency smoke**
  - Try tapping the same star button twice quickly → second tap should answer "Đơn đã được đánh giá rồi"
  - Try `POST /api/orders/{id}/rating` twice with same orderId → second returns 409 ALREADY_RATED


---

## Self-Review Checklist (Pre-Merge)

Use this list before raising a PR / merging to `main`. Tick every box.

### Schema & migration
- [ ] `V10__rating.sql` defines `rating` table with all columns from spec §5 (id, order_id UUID UNIQUE, customer_id BIGINT, shipper_id BIGINT, stars SMALLINT CHECK 1-5, comment TEXT, created_at TIMESTAMPTZ).
- [ ] `ON DELETE CASCADE` on `rating.order_id` (matches `order_item` pattern).
- [ ] Index `idx_rating_shipper_created (shipper_id, created_at DESC)` exists.
- [ ] No new column on `shipper_profile` (recompute strategy avoids it).
- [ ] Flyway history shows V10 success on a clean dev DB.

### Backend security
- [ ] `AdminDashboardController` and `AdminReportsController` are class-annotated with `@PreAuthorize("hasRole('SHOP_OWNER')")`.
- [ ] `RatingController` is annotated with `@PreAuthorize("hasRole('CUSTOMER')")` on the rate endpoint.
- [ ] `groupBy` parameter is whitelisted against `Set.of("day","week")` BEFORE binding into `DATE_TRUNC`.
- [ ] Date range capped at 90 days; `to < from` returns 400 `INVALID_RANGE`.
- [ ] `limit` on top-shippers capped at 50.
- [ ] No `String.format` or string concatenation into native SQL (`@Param` binding only).
- [ ] Customer ownership validated in `RatingService.rate` AND `RatingService.updateComment` (`order.customerId == authCustomerId`).
- [ ] No `dangerouslySetInnerHTML` anywhere; React escapes all rating comments rendered in admin UI (future shipper detail page — not in P8).

### Rating service correctness
- [ ] `rate(...)` is `@Transactional`.
- [ ] DB UNIQUE on `order_id` is the source of truth for idempotency; `DataIntegrityViolationException` → `ConflictException("ALREADY_RATED", ...)`.
- [ ] Star range check (1..5) BEFORE any DB call (`BusinessRuleException("STARS_OUT_OF_RANGE")`).
- [ ] `shipper_profile.rating_avg` rounded to 2 decimals (`HALF_UP`) via `BigDecimal.setScale`.
- [ ] `recomputeShipperStats` reads the freshly inserted row (same tx, READ COMMITTED).
- [ ] `updateComment` rejects when the rating is not the caller's.

### FSM correctness
- [ ] `ConversationStateService.put` is upsert (no error when row missing).
- [ ] `ConversationStateService.clear` is idempotent (`existsById` guard before `deleteById`).
- [ ] `ConversationState.data` round-trips through JSONB via `@JdbcTypeCode(SqlTypes.JSON)` (verified by `ConversationStateServiceIT`).
- [ ] `RatingCommentHandler` clears FSM on `/skip`, on successful comment save, on empty text, and on DomainException.
- [ ] `RatingCommentHandler` is `@Order(0)` so it pre-empts command handlers.
- [ ] `UpdateRouterOrderIT` confirms Spring honors `@Order` on injected `List<UpdateHandler>`.

### Bot flow
- [ ] `RatingPromptBuilder.build(orderId)` returns 5 star buttons + 1 "Bỏ qua" button on 2 rows.
- [ ] All callback data strings ≤ 64 bytes (test enforces).
- [ ] `OrderAssignedNotifier.onOrderDelivered` checks `ratingRepo.existsByOrderId` BEFORE sending the keyboard (idempotent prompt).
- [ ] `RatingCallbackHandler` parses `RATE:<uuid>:<stars>` and `RATE_SKIP:<uuid>`; rejects malformed UUID + stars out of range with `AnswerCallbackQuery`.
- [ ] On ALREADY_RATED: keyboard removed, no FSM opened.
- [ ] On other DomainException: keyboard NOT removed (user might retry).
- [ ] `RATE_SKIP` writes NO row to DB.

### Reports SQL correctness
- [ ] Every `WHERE created_at >= :from AND created_at < :to + INTERVAL '1 day'` (inclusive `to` at day level).
- [ ] Dashboard 7-day chart uses `generate_series` → returns 7 rows even on sparse data.
- [ ] Revenue series uses `DATE_TRUNC(:bucket, ...)` with `bucket` whitelisted.
- [ ] Top shippers `LEFT JOIN delivery_assignment` keeps shippers with zero deliveries.
- [ ] Cancellation `byReason` uses `NULLIF(TRIM(note), '')` → 'Không ghi lý do' fallback.
- [ ] Cancellation rate guards div-by-zero (returns 0 when `totalOrders == 0`).
- [ ] All native queries use interface projections (Spring Data idiom) — no `@SqlResultSetMapping`.

### Frontend type safety
- [ ] `frontend/shared/src/types/reports.ts` matches backend DTOs exactly (`DashboardSummary.ordersToday.byStatus` is `Partial<Record<OrderStatusKey, number>>`).
- [ ] `recharts` version pinned to `^3.8.1` in `frontend/webadmin/package.json`.
- [ ] All Recharts `ResponsiveContainer` wrapped in `<div className="h-72 w-full">` (no 0-px-height bug).
- [ ] `formatVnd` used for ALL currency display (no inline `Intl.NumberFormat`).
- [ ] No `any` types in new code; `pnpm -r run type-check` passes.

### Accessibility
- [ ] Date inputs have `<label htmlFor>` association.
- [ ] `<select>` has `<label>` association.
- [ ] Color contrast on chart strokes is sufficient (green-600, blue-600 on white).
- [ ] Native `<input type="date">` is keyboard-navigable by default.

### Performance
- [ ] Dashboard polls every 30s (not faster).
- [ ] Reports queries `enabled: rangeValid` to avoid pointless API calls.
- [ ] No `recharts` chart re-renders on parent state changes that don't affect chart data (memoize if profiling shows it).
- [ ] No N+1 in `topShippers` query (single SQL JOIN does the work).

### Tests
- [ ] `RatingRepositoryIT`: 6 cases (round-trip, UNIQUE, CHECK both directions, aggregate, zero-shipper).
- [ ] `RatingServiceTest`: 9 cases (rate happy + 5 rejection paths + updateComment 3 paths).
- [ ] `RatingControllerTest`: 4 cases (200, 400, 422, 409).
- [ ] `ConversationStateServiceTest`: 4 unit cases.
- [ ] `ConversationStateServiceIT`: 4 cases (JSONB round-trip, overwrite, clear, deleteStaleSince).
- [ ] `RatingPromptBuilderTest`: 1 case (keyboard shape + callback length).
- [ ] `OrderAssignedNotifierTest.onOrderDelivered_*`: 2 new cases (sends keyboard / skips when already rated).
- [ ] `RatingCallbackHandlerTest`: 9 cases.
- [ ] `RatingCommentHandlerTest`: 9 cases.
- [ ] `UpdateRouterOrderIT`: 1 case.
- [ ] `AdminDashboardControllerTest`: 2 cases (happy + auth).
- [ ] `AdminReportsControllerTest`: 8 cases (3 revenue paths + 2 top-shippers + 1 cancellation + 1 auth).
- [ ] `ReportsRepositoryIT`: 6 cases (7-day series, day bucket, week bucket, non-DELIVERED excluded, cancellation totals, empty range).

### Pre-merge smoke
- [ ] `./mvnw verify` → BUILD SUCCESS, no test failures.
- [ ] `pnpm -r run type-check` → all green.
- [ ] `pnpm -r run build` → all green.
- [ ] Manual: log in as admin, see Dashboard with KPIs + 7-day chart + top 3 shippers.
- [ ] Manual: navigate to /reports, select date range + groupBy, see all 3 charts populate.
- [ ] Manual: trigger DELIVERED on a test order, customer chat shows rating keyboard.
- [ ] Manual: tap 5 stars → keyboard disappears → "Cảm ơn bạn!" → "Bạn có muốn nhập nhận xét?" → type "tốt" → "Cảm ơn nhận xét của bạn!"
- [ ] Manual: re-tap on stale keyboard → "Đơn đã được đánh giá rồi" (idempotency).
- [ ] DB: `rating` row created with stars + comment; `shipper_profile.rating_avg` updated.

### Documentation
- [ ] Plan file (`docs/superpowers/plans/2026-06-01-p8-dashboard-reports-rating.md`) committed and referenced in PR description.
- [ ] No stale "P9 (Reports)" placeholder text anywhere in webadmin source.
- [ ] If `infra/docker-compose.dev.yml` does NOT set `TZ=Asia/Ho_Chi_Minh`, document in PR description that "today = JVM default TZ" (research §16 Pitfall 6).

---

## Commit Summary (expected)

```
TASK 1   — feat(rating): add V10 migration for rating table
TASK 2   — feat(rating): add Rating entity + repository with aggregate query + IT
TASK 3   — feat(rating): add RatingService with rate + updateComment + 9 unit tests
TASK 4   — feat(rating): add POST /api/orders/{id}/rating endpoint + DTO + WebMvcTest
TASK 5   — feat(bot): add ConversationStateService FSM scaffolding + JSONB round-trip IT
TASK 6   — feat(notification): send rating keyboard on OrderDeliveredEvent (idempotent)
TASK 7   — feat(bot): add RatingCallbackHandler for RATE/RATE_SKIP callbacks
TASK 8   — feat(bot): add RatingCommentHandler — FSM-gated comment capture with /skip
TASK 9   — test(bot): lock @Order behavior on injected UpdateHandler list
TASK 10  — feat(reports): add ReportsRepository + 4 admin endpoints (dashboard + revenue + top-shippers + cancellation)
TASK 11  — test(reports): add @WebMvcTest for AdminDashboard + AdminReports controllers
TASK 12  — test(reports): add IT for revenue series + cancellation totals (day + week buckets)
TASK 13  — [no commit — smoke only]
TASK 14  — build(webadmin): install recharts + add reports types and API helpers to @shop/shared
TASK 15  — feat(webadmin): rewrite DashboardPage with KPI cards + 7-day chart + top 3 shippers
TASK 16  — feat(webadmin): add ReportsPage with revenue/top-shippers/cancellation charts
TASK 17  — feat(webadmin): wire /reports route and add Báo cáo sidebar link
TASK 18  — [no commit — smoke only, optional polish]
```

Expected end-of-P8 commit count delta: **~16 commits** on top of the P7 baseline (~138 → ~154).

---

## End of P8 Plan
