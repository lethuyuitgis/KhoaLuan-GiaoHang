# Phase 8: Dashboard + Reports + Shipper Rating — Research

**Researched:** 2026-06-01
**Domain:** Spring Boot reporting endpoints + PostgreSQL aggregation queries + Telegram bot FSM (rating callback flow) + React 18 Recharts integration
**Confidence:** HIGH — verified against codebase (modules, migrations, existing handler/notifier patterns), `npm view recharts` (registry truth), Telegram Bot API docs, PostgreSQL `DATE_TRUNC` docs

---

## Summary

P8 is the "should-have" tier of the thesis: it pulls existing data (orders, deliveries, shippers) into three places — **(1)** an admin **Dashboard KPI** view with a small 7-day trend chart, **(2)** a Reports page with three time-bucketed analytics charts, and **(3)** a customer-facing **rating loop** triggered by `OrderDeliveredEvent` in the Telegram bot. None of it requires new infrastructure — Recharts is the only new frontend dep (~80 KB gzipped, single bundle hit on the admin SPA); the backend reuses Flyway, JPA, Spring Data, the existing `TransactionalEventListener(AFTER_COMMIT)` notification pattern, and the existing `UpdateHandler` bot routing.

The complexity hot spots are: **(a)** atomic `rating_avg` math under concurrent ratings (the existing `shipper_profile` table has no `@Version` column — must add one OR recompute via aggregate); **(b)** module placement of `Rating` (best fit: `delivery` module, since `ShipperProfile` and `DeliveryAssignment` live there and `Rating` references both); **(c)** report SQL — daily/weekly bucketing must use PostgreSQL `DATE_TRUNC` over `created_at` (orders table) AND must distinguish "cancellation by user vs by admin" via `status_history`; **(d)** the bot rating flow is the project's first FSM — `conversation_state` table is provisioned in V3 but no Java FSM code exists, so we're paving the path for future flows too.

**Primary recommendation:** Put `Rating` entity + `RatingService` + `RatingRepository` in the `delivery` module. Put **all three admin reports endpoints** (revenue, top-shippers, cancellation) in a single new `AdminReportsController` in the **`delivery` module** with native SQL queries via interface projections. Put the **dashboard summary** in a separate `AdminDashboardController` also in `delivery`, since it joins data from order + delivery + auth. The rating bot handler goes in `bot/handler/customer/RatingCallbackHandler` + `RatingCommentHandler` with a minimal first FSM implementation (`ConversationStateService` + enum) — keep it thin since this is the only flow that needs it in P8. Skip the WebSocket admin auth for the dashboard — use TanStack Query `refetchInterval: 30000` (defer realtime to a future phase if needed).

---

## User Constraints (from Phase 8 Brief)

No formal `CONTEXT.md` exists for P8 yet. The brief itself is the constraint set.

### Locked Decisions
- **Recharts** is the chart library (not Chart.js).
- Three report endpoints exactly:
  - `GET /api/admin/reports/revenue?from&to&groupBy=day|week`
  - `GET /api/admin/reports/top-shippers?from&to&limit=10`
  - `GET /api/admin/reports/cancellation?from&to`
- `GET /api/admin/dashboard/summary` returns KPI cards + 7-day revenue mini-series.
- Rating callback format: `RATE:<orderId>:<stars>` (NOT assignment ID — order is the natural key per spec §5 rating table).
- Rating table per spec §5: `(id BIGSERIAL, order_id UUID UNIQUE, customer_id BIGINT, shipper_id BIGINT, stars SMALLINT CHECK 1-5, comment TEXT NULL, created_at TIMESTAMPTZ)`.
- Frontend: Vietnamese labels everywhere, VND currency formatting.
- Cap date range at last **90 days** (thesis demo limit).

### Claude's Discretion
- Module placement of `Rating` (recommendation: `delivery` — justified below).
- SQL implementation strategy (recommendation: native SQL + interface projection, not JPQL — `DATE_TRUNC` is not JPQL-portable).
- Concurrency strategy for `rating_avg`: incremental update with `@Version` vs. recompute. **Recommendation: recompute** (slower but simpler; thesis-scale traffic doesn't need micro-optimization).
- Bot FSM implementation depth — minimum-viable for the rating flow only.
- Dashboard polling vs WebSocket — **defer WS admin auth**; use 30s polling.
- Recharts component variants (LineChart / BarChart / PieChart — all canonical Recharts components, no custom shapes needed).

### Deferred Ideas (OUT OF SCOPE for P8)
- WebSocket-pushed dashboard updates (admin JWT for WS) — defer to a later phase.
- Rating editing / deletion (immutable per spec).
- Rating moderation / flagging.
- Cross-shipper comparison charts on shipper detail page (out of scope; reports page already covers top-N).
- Revenue forecasting / predictive analytics.
- CSV / Excel export of reports.
- Date ranges > 90 days.

---

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| P8-RAT-01 | New `rating` table via Flyway V10 | §1.1 schema |
| P8-RAT-02 | `Rating` entity + repository + service in `delivery` module | §2.1 module placement |
| P8-RAT-03 | `POST /api/orders/:id/rating` endpoint (used by bot, not by miniapp) | §2.2 controller |
| P8-RAT-04 | Atomic update of `shipper_profile.rating_avg/count` | §1.2 concurrency |
| P8-RAT-05 | Bot inline keyboard sent on `OrderDeliveredEvent` | §4.1 |
| P8-RAT-06 | `RatingCallbackHandler` parses `RATE:<orderId>:<stars>` | §4.2 |
| P8-RAT-07 | Optional comment flow via `ConversationStateService` FSM | §4.3 |
| P8-RAT-08 | Edit message to remove keyboard after click | §4.4 |
| P8-DSH-01 | `GET /api/admin/dashboard/summary` — KPI + 7-day series | §3.1 |
| P8-DSH-02 | Frontend `DashboardPage` rewrite with Recharts | §6 |
| P8-RPT-01 | `GET /api/admin/reports/revenue?from&to&groupBy` | §3.2 |
| P8-RPT-02 | `GET /api/admin/reports/top-shippers?from&to&limit` | §3.3 |
| P8-RPT-03 | `GET /api/admin/reports/cancellation?from&to` | §3.4 |
| P8-RPT-04 | Frontend `ReportsPage` with three charts | §6 |

---

## Project Constraints (from existing codebase)

There is no `./CLAUDE.md` at the repo root. The implicit project conventions are derived from existing modules:

- **Spring Boot 3.4 / Java 17 / Maven multi-module** under `backend/modules/{name}` with parent POM at `backend/pom.xml`.
- **Module dependency direction:** strict — `order` does not depend on `delivery`; `delivery` depends on `order`. Anything that joins both tables lives in `delivery` or `notification`.
- **Flyway numbered migrations** in `backend/app/src/main/resources/db/migration/V{N}__{name}.sql`. Latest is `V9__payment.sql`; new migration must be **`V10__rating.sql`**.
- **Naming:** SQL tables use `snake_case`; entities use `@Table(name = "...")` and `@Column(name = "...")`. UUIDs are stored as `UUID` (not `BYTEA`).
- **`@Version` optimistic lock** is the convention on mutable entities (`Order`, `Payment` both use it). `ShipperProfile` does NOT currently have `@Version` — must add if we go incremental.
- **Domain events:** publishers use `ApplicationEventPublisher.publishEvent`, listeners use `@TransactionalEventListener(phase = AFTER_COMMIT)`. See `OrderAssignedNotifier`.
- **Exception model:** throw `DomainException` subtypes from `shared` module — `NotFoundException`, `ValidationException`, `BusinessRuleException`, `ConflictException`. NEVER throw generic `RuntimeException`.
- **Tests:** Testcontainers Postgres 16 + `@DataJpaTest` for repository ITs (see `OrderTestcontainerBase`); plain JUnit + Mockito for services. Surefire/failsafe split: `*Test` runs unit, `*IT` runs integration.
- **Frontend:** pnpm workspaces; `@shop/shared` exposes `formatVnd`, `formatDateTime`, types via `frontend/shared/src/index.ts`. Add new types to `frontend/shared/src/types/`; add API helpers to `frontend/shared/src/api/`.
- **Security:** `SecurityConfig` already routes `/api/admin/**` → `hasRole('SHOP_OWNER')`. New admin endpoints get the protection for free; just add `@PreAuthorize("hasRole('SHOP_OWNER')")` on controllers for defence-in-depth.

---

## 1. Database schema decisions

### 1.1 Migration `V10__rating.sql`

```sql
-- V10__rating.sql — customer rating of shipper after DELIVERED (Should #10)
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

-- Index for "rating history per shipper" (admin shipper detail page, top-shippers report)
CREATE INDEX idx_rating_shipper_created ON rating(shipper_id, created_at DESC);
-- order_id has UNIQUE which gives us an index for free; no extra index needed for it.
-- customer_id queries are rare (only "did this customer already rate?") — skip the index.
```

**Design notes:**
- `order_id UUID UNIQUE` enforces "one rating per order" at the DB level — the bot handler can rely on this for idempotency. Race-condition double-clicks throw `DataIntegrityViolationException` → translate to `ConflictException("ALREADY_RATED", ...)`.
- `ON DELETE CASCADE` on `order_id` matches the existing pattern (`order_item.order_id` cascades; spec §5 doesn't specify but cascade is safe — ratings are meaningless without their order).
- `customer_id` / `shipper_id` are NOT cascade — we never delete `telegram_user` rows in practice, and a ratings record should outlive a hypothetical user deletion for shipper reputation history.
- `stars SMALLINT CHECK BETWEEN 1 AND 5` — DB-enforced. The Java code will also validate, but defence-in-depth.
- `created_at TIMESTAMPTZ DEFAULT NOW()` — matches convention. No `updated_at` because rating is immutable.
- **No `@Version`** on rating itself — it's append-only, no updates after creation.

### 1.2 Atomic update of `shipper_profile.rating_avg` / `rating_count`

Two options:

| Option | Formula | Pros | Cons |
|--------|---------|------|------|
| **A. Incremental** | `new_avg = (old_avg * old_count + new_stars) / (old_count + 1)` then UPDATE | Fast O(1), no aggregate scan | Needs `@Version` on `ShipperProfile` + retry on lock conflict; rounding accumulates over time |
| **B. Recompute** | After insert, `SELECT AVG(stars), COUNT(*) FROM rating WHERE shipper_id = ?` then UPDATE | Always exact, no concurrency issues (last writer wins is fine here), no schema change | Slower (one extra query); cost grows with rating count but stays milliseconds for thesis scale |

**Recommendation: Option B (recompute).** Justification:
1. Thesis demo scale — even 10k ratings per shipper is < 50 ms on indexed query.
2. No need to add `@Version` to `ShipperProfile` migration (avoids touching V6).
3. Eliminates the entire class of "lost update" bugs from incremental math — under concurrent inserts the recompute reads the latest committed state.
4. **Avoids floating-point drift** — incremental with `NUMERIC(3,2)` truncation drifts visibly after ~1000 ratings.
5. Wrap insert+recompute in a single `@Transactional` method — Postgres's default `READ COMMITTED` is sufficient because the `SELECT AVG()` runs after the INSERT in the same tx and sees the new row.

```java
@Transactional
public Rating rate(UUID orderId, Long customerId, int stars, String comment) {
    Order order = orderRepo.findById(orderId)
        .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "..."));
    if (order.getStatus() != OrderStatus.DELIVERED) {
        throw new BusinessRuleException("ORDER_NOT_RATEABLE",
            "Chỉ đánh giá được sau khi đơn đã giao");
    }
    if (!order.getCustomerId().equals(customerId)) {
        throw new BusinessRuleException("NOT_YOUR_ORDER", "Đây không phải đơn của bạn");
    }
    DeliveryAssignment a = assignmentRepo.findByOrderId(orderId)
        .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND", "..."));

    Rating r = new Rating();
    r.setOrderId(orderId);
    r.setCustomerId(customerId);
    r.setShipperId(a.getShipperId());
    r.setStars(stars);
    r.setComment(comment);
    try {
        ratingRepo.saveAndFlush(r);
    } catch (DataIntegrityViolationException ex) {
        throw new ConflictException("ALREADY_RATED", "Đơn đã được đánh giá rồi");
    }

    // Recompute — exact, concurrency-safe.
    var stats = ratingRepo.aggregateForShipper(a.getShipperId()); // SELECT AVG, COUNT
    shipperRepo.findById(a.getShipperId()).ifPresent(sp -> {
        sp.setRatingAvg(stats.getAvg());
        sp.setRatingCount(stats.getCount());
        shipperRepo.save(sp);
    });
    return r;
}
```

Repository:

```java
public interface RatingRepository extends JpaRepository<Rating, Long> {
    boolean existsByOrderId(UUID orderId);

    @Query(value = """
        SELECT COALESCE(AVG(stars), 0) AS avg, COUNT(*) AS count
        FROM rating WHERE shipper_id = :shipperId
        """, nativeQuery = true)
    RatingStats aggregateForShipper(@Param("shipperId") Long shipperId);

    interface RatingStats {
        BigDecimal getAvg();   // cast to NUMERIC(3,2) at write time
        Integer getCount();
    }
}
```

---

## 2. Backend module placement

### 2.1 Where does `Rating` entity live?

| Option | Pros | Cons |
|--------|------|------|
| **A. New `rating` module** | Cleanest separation, follows single-responsibility | 1 entity + 1 service is too thin to justify a module; adds to parent POM; build time penalty |
| **B. Put in `delivery` module** | `ShipperProfile` and `DeliveryAssignment` already live there; `Rating` references both; no new module | Slightly inflates `delivery` module's responsibilities |
| C. Put in `order` module | `Rating` references `Order.id` | `order` cannot depend on `delivery` (would invert direction); blocks accessing `ShipperProfile` to update rating_avg |

**Recommendation: Option B — put `Rating` in the `delivery` module.**

Rationale:
- `delivery` already depends on `order` (via `OrderRepository`/`OrderService`) → can resolve `Order.id` and check `status = DELIVERED`.
- `delivery` already owns `ShipperProfile` → can update `rating_avg` / `rating_count` directly in the same transaction.
- `delivery` already depends on `auth` → can reference `telegram_user.id` for customer/shipper FK.
- Adding `RatingController @ /api/orders/:id/rating` to `delivery` is fine — Spring scans all `@RestController` beans regardless of module; the URL belongs to the order namespace but the implementation lives wherever is convenient.
- No new module = no parent POM edit, no new test setup boilerplate.

Files to create in `delivery` module:
```
backend/modules/delivery/src/main/java/com/shop/delivery/delivery/
├── entity/Rating.java
├── repository/RatingRepository.java
├── service/RatingService.java
├── api/customer/RatingController.java         (POST /api/orders/:id/rating)
└── api/customer/dto/RateOrderRequest.java     (stars, comment)
```

### 2.2 Where do report endpoints live?

| Option | Tradeoff |
|--------|----------|
| **A. Single `AdminReportsController` + `AdminDashboardController` in `delivery`** | One module owns the analytics surface; delivery already depends on order + auth, can join all needed tables |
| B. Split — revenue → `order`, top-shippers + cancellation → `delivery` | Forces two repository layers, two `@RestController` classes, two test setups; revenue doesn't even need `order` business logic (it's just SUM/GROUP BY) |
| C. New `reports` module | Same overhead objection as 2.1 — too thin to justify |

**Recommendation: Option A** — put `AdminReportsController` + `AdminDashboardController` in the **`delivery`** module:

```
backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/
├── AdminReportsController.java        (3 endpoints)
├── AdminDashboardController.java      (1 endpoint, returns DashboardSummary)
├── dto/
│   ├── DashboardSummary.java
│   ├── RevenuePoint.java              (date, revenue, orderCount)
│   ├── TopShipperRow.java             (shipperId, name, deliveredCount, revenue, ratingAvg)
│   └── CancellationReport.java        (totalOrders, cancelledCount, cancelRate, byReason[])
└── reports/
    ├── ReportsQueryService.java       (transactional, calls repositories)
    └── ReportsRepository.java         (native queries — see §3)
```

Both controllers protected by class-level `@PreAuthorize("hasRole('SHOP_OWNER')")` (matches existing `AdminOrderController`, `AdminShipperController`).

---

## 3. SQL queries for reports

### 3.1 Dashboard summary

The summary is **5 independent aggregates** stitched together. Don't try to do it in one query — KISS, each is fast and indexed.

```java
public interface ReportsRepository extends JpaRepository<Order, UUID> {

    // 1. Orders today, breakdown by status
    @Query(value = """
        SELECT status, COUNT(*) AS count
        FROM orders
        WHERE created_at >= CURRENT_DATE AND created_at < CURRENT_DATE + INTERVAL '1 day'
        GROUP BY status
        """, nativeQuery = true)
    List<StatusCount> ordersTodayByStatus();

    interface StatusCount { String getStatus(); Long getCount(); }

    // 2. Revenue today (DELIVERED only)
    @Query(value = """
        SELECT COALESCE(SUM(total), 0)
        FROM orders
        WHERE status = 'DELIVERED'
          AND created_at >= CURRENT_DATE AND created_at < CURRENT_DATE + INTERVAL '1 day'
        """, nativeQuery = true)
    BigDecimal revenueToday();

    // 3. Active shippers
    @Query(value = """
        SELECT COUNT(*) FROM shipper_profile
        WHERE current_state IN ('AVAILABLE', 'BUSY')
        """, nativeQuery = true)
    long activeShippers();

    // 4. New customers today (telegram_user with CUSTOMER role created today)
    @Query(value = """
        SELECT COUNT(DISTINCT u.id)
        FROM telegram_user u
        JOIN user_role r ON r.telegram_user_id = u.id
        WHERE r.role = 'CUSTOMER'
          AND u.created_at >= CURRENT_DATE AND u.created_at < CURRENT_DATE + INTERVAL '1 day'
        """, nativeQuery = true)
    long newCustomersToday();

    // 5. Last 7 days revenue mini-series (used by dashboard chart)
    @Query(value = """
        SELECT
            d::date AS date,
            COALESCE(SUM(o.total) FILTER (WHERE o.status = 'DELIVERED'), 0) AS revenue,
            COUNT(o.id) FILTER (WHERE o.status = 'DELIVERED') AS orders
        FROM generate_series(
            CURRENT_DATE - INTERVAL '6 days',
            CURRENT_DATE,
            INTERVAL '1 day'
        ) AS d
        LEFT JOIN orders o
          ON o.created_at >= d AND o.created_at < d + INTERVAL '1 day'
        GROUP BY d
        ORDER BY d
        """, nativeQuery = true)
    List<RevenuePointRow> revenueLast7Days();

    interface RevenuePointRow {
        LocalDate getDate(); BigDecimal getRevenue(); Long getOrders();
    }
}
```

**Why `generate_series`?** It guarantees we return 7 rows even when some days had zero orders. Without it, a day with no DELIVERED orders is missing from the result set, breaking the chart's x-axis.

### 3.2 Revenue time-series — `GET /api/admin/reports/revenue?from=YYYY-MM-DD&to=YYYY-MM-DD&groupBy=day|week`

```java
@Query(value = """
    SELECT
        DATE_TRUNC(:bucket, o.created_at)::date AS date,
        COALESCE(SUM(o.total), 0) AS revenue,
        COUNT(*) AS orderCount
    FROM orders o
    WHERE o.created_at >= :from
      AND o.created_at <  :to + INTERVAL '1 day'
      AND o.status = 'DELIVERED'
    GROUP BY DATE_TRUNC(:bucket, o.created_at)
    ORDER BY DATE_TRUNC(:bucket, o.created_at)
    """, nativeQuery = true)
List<RevenuePointRow> revenueSeries(@Param("from") LocalDate from,
                                    @Param("to")   LocalDate to,
                                    @Param("bucket") String bucket);  // 'day' | 'week'
```

**Validations:**
- `bucket` must be one of `'day'` / `'week'` — enforce at controller (whitelist), DO NOT pass arbitrary user input into `DATE_TRUNC` (SQL injection via DATE_TRUNC's part argument is a known vector even though it's bound as a param — be safe).
- `to` is **inclusive** at the day level — the `< :to + INTERVAL '1 day'` handles the "everything that happened on day `to`" case.
- Cap `(to - from)` at 90 days in the controller; throw `ValidationException("DATE_RANGE_TOO_LARGE", ...)` if exceeded.
- `DATE_TRUNC('week', ...)` aligns to **ISO Monday** in PostgreSQL — call this out in the API doc so the FE knows the bucket label is "week starting Monday YYYY-MM-DD".

**Sparse-data note:** Unlike the dashboard's 7-day series, the reports endpoint does NOT need `generate_series` to fill gaps — the frontend chart is fine showing only days that had revenue. If we want a dense series later, add it then.

### 3.3 Top-shippers — `GET /api/admin/reports/top-shippers?from=&to=&limit=10`

```java
@Query(value = """
    SELECT
        sp.user_id              AS shipperId,
        u.first_name             AS firstName,
        u.last_name              AS lastName,
        u.username               AS username,
        COUNT(o.id)              AS deliveredCount,
        COALESCE(SUM(o.total),0) AS revenueGenerated,
        sp.rating_avg            AS ratingAvg
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
List<TopShipperRow> topShippers(@Param("from") LocalDate from,
                                @Param("to")   LocalDate to,
                                @Param("limit") int limit);

interface TopShipperRow {
    Long getShipperId();
    String getFirstName();
    String getLastName();
    String getUsername();
    Long getDeliveredCount();
    BigDecimal getRevenueGenerated();
    BigDecimal getRatingAvg();
}
```

**Notes:**
- `LEFT JOIN` keeps shippers with zero deliveries in the result; `ORDER BY COUNT(o.id) DESC` pushes them to the bottom.
- We filter on `delivery_assignment.delivered_at` (not `orders.created_at`) — the right field for "this shipper completed N deliveries during this window".
- `revenueGenerated` is the sum of `orders.total`, not `delivery_fee` — matches the spec wording "revenue generated by shipper" and is the more business-meaningful metric.
- The controller constructs the display name as `firstName + " " + lastName` (or falls back to `@username` if both null) — keep DTO close to DB columns and let the controller do the cosmetic join.

### 3.4 Cancellation — `GET /api/admin/reports/cancellation?from=&to=`

The spec says "byReason: [{reason, count}]". The current `orders` table has no `cancellation_reason` column — but `status_history.note` is populated with the reason when `cancel()` is called (see `OrderService.cancel()` → `transitionStatus(id, CANCELLED, actorUserId, reason)` → `recordTransition(...)` writes `reason` to `note`). So **use `status_history`** as the source of truth for cancellation reasons.

```java
// Total orders + cancelled count in window
@Query(value = """
    SELECT
        COUNT(*)                                            AS totalOrders,
        COUNT(*) FILTER (WHERE status = 'CANCELLED')        AS cancelledCount
    FROM orders
    WHERE created_at >= :from
      AND created_at <  :to + INTERVAL '1 day'
    """, nativeQuery = true)
CancellationTotals cancellationTotals(@Param("from") LocalDate from,
                                      @Param("to")   LocalDate to);

interface CancellationTotals { Long getTotalOrders(); Long getCancelledCount(); }

// Break down cancellation by reason (from status_history.note)
@Query(value = """
    SELECT
        COALESCE(NULLIF(TRIM(sh.note), ''), 'Không ghi lý do') AS reason,
        COUNT(*) AS count
    FROM status_history sh
    JOIN orders o ON o.id = sh.order_id
    WHERE sh.to_status = 'CANCELLED'
      AND o.created_at >= :from
      AND o.created_at <  :to + INTERVAL '1 day'
    GROUP BY 1
    ORDER BY count DESC
    LIMIT 10
    """, nativeQuery = true)
List<ReasonCount> cancellationByReason(@Param("from") LocalDate from,
                                        @Param("to")   LocalDate to);

interface ReasonCount { String getReason(); Long getCount(); }
```

**Notes:**
- An order can transition to CANCELLED at most once (state machine forbids re-entering CANCELLED), so we don't need DISTINCT.
- `NULLIF(TRIM(note), '')` collapses empty/whitespace-only notes into "Không ghi lý do" — keeps the pie chart readable.
- We `JOIN orders o` and filter on `o.created_at` (not `sh.changed_at`) so the report scope is "orders placed in this window that ended up cancelled" — the natural business reading. If product owner wants "cancellations that happened in this window regardless of order date", flip to `sh.changed_at` — but `o.created_at` is the default per spec wording.
- Cancel rate is computed in the controller: `cancelledCount / totalOrders` (guard against div-by-zero — return `0.0` if `totalOrders == 0`).

---

## 4. Telegram Bot rating handler

### 4.1 Notification listener — extend `OrderAssignedNotifier`

The existing notifier already has `onOrderDelivered(OrderDeliveredEvent)`. Modify it to attach the inline keyboard with rating buttons (instead of sending a plain text message). Move the keyboard-building logic into a new `RatingPromptBuilder` for testability:

```java
// In OrderAssignedNotifier.onOrderDelivered
@TransactionalEventListener(phase = AFTER_COMMIT)
public void onOrderDelivered(OrderDeliveredEvent e) {
    InlineKeyboardMarkup kb = ratingPromptBuilder.build(e.orderId());
    SendMessage msg = SendMessage.builder()
        .chatId(e.customerId())
        .text("✅ Đơn " + e.orderCode() + " đã giao xong.\n⭐ Hãy đánh giá shipper:")
        .replyMarkup(kb)
        .build();
    bot.execute(msg);

    // Keep the shipper-side thank-you unchanged
    bot.sendText(e.shipperId(), "✅ Hoàn thành đơn " + e.orderCode() + "...");
}
```

`RatingPromptBuilder`:
```java
@Component
public class RatingPromptBuilder {
    public InlineKeyboardMarkup build(UUID orderId) {
        List<InlineKeyboardButton> starRow = new ArrayList<>();
        for (int s = 1; s <= 5; s++) {
            starRow.add(InlineKeyboardButton.builder()
                .text("⭐".repeat(s))
                .callbackData("RATE:" + orderId + ":" + s)
                .build());
        }
        InlineKeyboardButton skipBtn = InlineKeyboardButton.builder()
            .text("Bỏ qua")
            .callbackData("RATE_SKIP:" + orderId)
            .build();
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        kb.setKeyboard(List.of(starRow, List.of(skipBtn)));
        return kb;
    }
}
```

### 4.2 `RatingCallbackHandler` (in `bot/handler/customer/`)

```java
@Component
public class RatingCallbackHandler implements UpdateHandler {
    private final RatingService ratingService;
    private final ConversationStateService conv;
    private final BotSender sender;

    public boolean canHandle(Update u) {
        return u.hasCallbackQuery()
            && u.getCallbackQuery().getData() != null
            && (u.getCallbackQuery().getData().startsWith("RATE:")
                || u.getCallbackQuery().getData().startsWith("RATE_SKIP:"));
    }

    public void handle(Update u) {
        CallbackQuery cb = u.getCallbackQuery();
        String data = cb.getData();
        Long customerId = cb.getFrom().getId();
        Long chatId = cb.getMessage().getChatId();
        Integer messageId = cb.getMessage().getMessageId();

        if (data.startsWith("RATE_SKIP:")) {
            removeKeyboard(chatId, messageId);
            sender.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(cb.getId()).text("Đã bỏ qua").build());
            return;
        }
        // "RATE:<orderId>:<stars>"
        String[] parts = data.split(":");
        if (parts.length != 3) { /* answer error */ return; }
        UUID orderId;
        int stars;
        try {
            orderId = UUID.fromString(parts[1]);
            stars = Integer.parseInt(parts[2]);
            if (stars < 1 || stars > 5) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            sender.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(cb.getId()).text("Dữ liệu không hợp lệ").showAlert(true).build());
            return;
        }

        // Persist rating immediately (stars-only — comment is optional)
        try {
            ratingService.rate(orderId, customerId, stars, null);
        } catch (ConflictException ex) {
            sender.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(cb.getId()).text("Đơn đã được đánh giá rồi").showAlert(true).build());
            removeKeyboard(chatId, messageId);
            return;
        } catch (DomainException ex) {
            sender.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(cb.getId()).text(ex.getMessage()).showAlert(true).build());
            return;
        }

        removeKeyboard(chatId, messageId);
        sender.execute(AnswerCallbackQuery.builder()
            .callbackQueryId(cb.getId()).text("Cảm ơn bạn!").build());

        // Open comment FSM
        conv.put(customerId, "CUSTOMER_RATING_COMMENT", Map.of("orderId", orderId.toString()));
        sender.sendText(chatId, "Bạn có muốn nhập nhận xét? Gõ tin nhắn hoặc /skip để bỏ qua.");
    }

    private void removeKeyboard(Long chatId, Integer messageId) {
        EditMessageReplyMarkup edit = EditMessageReplyMarkup.builder()
            .chatId(chatId).messageId(messageId)
            .replyMarkup(InlineKeyboardMarkup.builder().keyboard(List.of()).build())
            .build();
        sender.execute(edit);
    }
}
```

### 4.3 `ConversationStateService` (minimal FSM, new in P8)

The `conversation_state` table already exists in V3 but no Java code has been written for it. P8 introduces the smallest viable FSM. Build it generic so future flows (shipper registration in §7, P9+) reuse it.

```java
@Entity @Table(name = "conversation_state")
public class ConversationState {
    @Id @Column(name = "telegram_user_id") private Long telegramUserId;
    @Column(name = "state", nullable = false, length = 64) private String state;
    @Column(name = "data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> data;
    @Column(name = "updated_at") private Instant updatedAt = Instant.now();
    // getters/setters/@PreUpdate
}

public interface ConversationStateRepository extends JpaRepository<ConversationState, Long> {
    @Modifying @Query("DELETE FROM ConversationState s WHERE s.updatedAt < :cutoff")
    int deleteStaleSince(@Param("cutoff") Instant cutoff);
}

@Service
public class ConversationStateService {
    private final ConversationStateRepository repo;

    public Optional<ConversationState> get(Long userId) { return repo.findById(userId); }

    @Transactional
    public void put(Long userId, String state, Map<String, Object> data) {
        ConversationState cs = repo.findById(userId).orElseGet(() -> {
            ConversationState n = new ConversationState();
            n.setTelegramUserId(userId);
            return n;
        });
        cs.setState(state);
        cs.setData(data);
        cs.setUpdatedAt(Instant.now());
        repo.save(cs);
    }

    @Transactional public void clear(Long userId) { repo.deleteById(userId); }
}
```

`jsonb` mapping in Hibernate 6: use `@JdbcTypeCode(SqlTypes.JSON)` from Hibernate's `org.hibernate.annotations` — works out of the box in Spring Boot 3.4 (Hibernate 6.6) without any extra `hypersistence-utils` dependency. Verified in Spring Boot 3.4 release notes — Hibernate 6 has native `Map<String,Object>` ↔ jsonb support.

### 4.4 `RatingCommentHandler` — captures the optional comment

```java
@Component
public class RatingCommentHandler implements UpdateHandler {
    private final ConversationStateService conv;
    private final RatingService ratingService;
    private final BotSender sender;

    public boolean canHandle(Update u) {
        if (!u.hasMessage() || !u.getMessage().hasText()) return false;
        Long userId = u.getMessage().getFrom().getId();
        return conv.get(userId)
            .map(s -> "CUSTOMER_RATING_COMMENT".equals(s.getState()))
            .orElse(false);
    }

    public void handle(Update u) {
        Long userId = u.getMessage().getFrom().getId();
        Long chatId = u.getMessage().getChatId();
        String text = u.getMessage().getText().trim();

        if ("/skip".equalsIgnoreCase(text)) {
            conv.clear(userId);
            sender.sendText(chatId, "Đã ghi nhận đánh giá. Cảm ơn bạn!");
            return;
        }
        // We can't easily "edit" an existing Rating row since stars were committed.
        // Two options:
        //  (a) Persist comment via ratingService.updateComment(orderId, customerId, comment)
        //      → needs an UPDATE on a single column; safe because rating is per-order.
        //  (b) Defer all rating writes until comment is submitted → loses the rating if user closes app.
        // GO WITH (a) — comment update is allowed even though stars are immutable.
        ConversationState s = conv.get(userId).orElseThrow();
        UUID orderId = UUID.fromString((String) s.getData().get("orderId"));
        ratingService.updateComment(orderId, userId, text);
        conv.clear(userId);
        sender.sendText(chatId, "Cảm ơn nhận xét của bạn!");
    }
}
```

**Important:** `RatingCommentHandler.canHandle` checks the FSM state — it must be registered in `UpdateRouter` **before** any other text-message handler that doesn't check FSM state, so the router picks the right one. Easiest: handlers that consume FSM-bound updates declare themselves first. Verify the order in `UpdateRouter` — current handlers are command-based (`/start`, `/help`) so there's no collision risk for P8, but document this for future flows.

**Handler-ordering gotcha:** `UpdateRouter.handlers` is just `List<UpdateHandler>` — Spring injects in bean creation order, which is NOT deterministic across rebuilds. To guarantee FSM handlers run first, annotate `RatingCommentHandler` with `@Order(0)` and command handlers with `@Order(100)`. Verify Spring honors `@Order` on injected `List<T>` (it does, per `OrderUtils` — confirmed behavior since Spring 4.0).

### 4.5 Callback data length check

- `RATE:<orderId>:<stars>` — `5 + 36 + 1 + 1 = 43 bytes` ✓ well under Telegram's 64-byte limit. [VERIFIED: Telegram Bot API docs](https://core.telegram.org/bots/api#inlinekeyboardbutton)
- `RATE_SKIP:<orderId>` — `10 + 36 = 46 bytes` ✓

### 4.6 `RatingService.updateComment`

```java
@Transactional
public void updateComment(UUID orderId, Long customerId, String comment) {
    Rating r = ratingRepo.findByOrderId(orderId)
        .orElseThrow(() -> new NotFoundException("RATING_NOT_FOUND", "Chưa có đánh giá cho đơn này"));
    if (!r.getCustomerId().equals(customerId)) {
        throw new BusinessRuleException("NOT_YOUR_RATING", "Không phải đánh giá của bạn");
    }
    if (r.getComment() != null) {
        // Idempotent — silently accept the latest comment (or throw if you want strict immutability).
        // For thesis: allow overwrite within the same FSM session.
    }
    r.setComment(comment);
    ratingRepo.save(r);
}
```

---

## 5. WebSocket vs polling for dashboard

The spec mentions `/topic/admin/orders` for realtime order updates. WebSocket auth for admin is **not yet** wired — the existing `WebSocketConfig` only accepts initData (Mini App customer/shipper auth). Adding JWT auth to STOMP CONNECT frames is a small task, but it's not free:

| Concern | Polling (every 30s) | WebSocket push |
|---------|---------------------|----------------|
| Implementation effort | None — TanStack Query `refetchInterval: 30000` | Add `ChannelInterceptor` parsing `Authorization: Bearer ...` on CONNECT; publish events to `/topic/admin/orders` |
| Latency | Up to 30s | < 1s |
| Server cost | 1 query / 30s / open tab | ~0 baseline + push burst on order events |
| Failure mode | Refetch on next tick | Auto-reconnect via SockJS |
| Thesis evaluator impression | "It refreshes" | "Real-time!" |

**Recommendation: defer WebSocket admin auth.** Use TanStack Query polling for both dashboard and orders pages. Rationale:

1. Thesis defense reviewers care that the system **works** — 30s latency is acceptable for an admin dashboard (humans don't refresh every second).
2. WebSocket admin auth is a non-trivial security surface (CSRF, origin checking, token refresh-mid-session) — easy to get wrong, hard to test in a thesis setting.
3. The Mini App already has WebSocket via initData for `/user/queue/orders` (verified in WebSocketConfig path: `/ws/**` permitAll in SecurityConfig). Customer-facing realtime is covered; the admin dashboard is the only place that would benefit, and it's not where the user spends most of their time.
4. P9 can revisit if needed.

**Implementation:** In each Dashboard/Reports query, set `refetchInterval: 30000, refetchOnWindowFocus: true` and call it done.

---

## 6. Recharts integration

### 6.1 Package install

```bash
cd frontend/webadmin
pnpm add recharts@^3.8.1
```

[VERIFIED: npm view recharts version → 3.8.1, published 2026-03-25]
- React 18 supported (and 17, and 19). Verified via [GitHub discussion #5698](https://github.com/recharts/recharts/discussions/5698) and the package's peer deps.
- Bundle size: Recharts 3.x is tree-shakeable — importing only the components you use keeps the gzip size around 80 KB for a typical dashboard. Direct named imports (`import { LineChart, Line, XAxis } from 'recharts'`) work.

### 6.2 Canonical Vietnamese currency tooltip

```tsx
// frontend/webadmin/src/components/charts/RevenueChart.tsx
import { LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer, Legend } from 'recharts';
import { formatVnd } from '@shop/shared';

interface Point { date: string; revenue: number; orderCount: number; }

export function RevenueChart({ data }: { data: Point[] }) {
  const fmtAxis = (v: number) =>
    v >= 1_000_000 ? `${(v / 1_000_000).toFixed(1)}M` :
    v >= 1_000     ? `${(v / 1_000).toFixed(0)}K`     :
    `${v}`;

  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={data} margin={{ top: 10, right: 20, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="date" tickFormatter={(d: string) => d.slice(5)} /> {/* MM-DD */}
        <YAxis tickFormatter={fmtAxis} />
        <Tooltip
          formatter={(value: number, name: string) =>
            name === 'revenue' ? [formatVnd(value), 'Doanh thu']
                               : [value, 'Số đơn']
          }
          labelFormatter={(label: string) => `Ngày ${label}`}
        />
        <Legend formatter={(v) => v === 'revenue' ? 'Doanh thu' : 'Số đơn'} />
        <Line type="monotone" dataKey="revenue"    stroke="#16a34a" strokeWidth={2} />
        <Line type="monotone" dataKey="orderCount" stroke="#2563eb" strokeWidth={2} />
      </LineChart>
    </ResponsiveContainer>
  );
}
```

### 6.3 BarChart (top shippers) and PieChart (cancellation)

```tsx
// BarChart for top shippers
<ResponsiveContainer width="100%" height={300}>
  <BarChart data={data} layout="vertical">
    <CartesianGrid strokeDasharray="3 3" />
    <XAxis type="number" />
    <YAxis dataKey="name" type="category" width={120} />
    <Tooltip formatter={(v: number) => [`${v} đơn`, 'Số đơn đã giao']} />
    <Bar dataKey="deliveredCount" fill="#2563eb" />
  </BarChart>
</ResponsiveContainer>

// PieChart for cancellation reasons
const COLORS = ['#ef4444', '#f97316', '#eab308', '#22c55e', '#3b82f6', '#a855f7'];
<ResponsiveContainer width="100%" height={300}>
  <PieChart>
    <Pie data={data} dataKey="count" nameKey="reason" outerRadius={100} label>
      {data.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
    </Pie>
    <Tooltip formatter={(v: number) => [`${v} đơn`, 'Số lượng']} />
    <Legend />
  </PieChart>
</ResponsiveContainer>
```

### 6.4 Date-range picker

Avoid adding a new dep — use native `<input type="date">` with `min` set to 90 days ago and `max` set to today:

```tsx
const [from, setFrom] = useState(format(subDays(new Date(), 30), 'yyyy-MM-dd'));
const [to,   setTo]   = useState(format(new Date(), 'yyyy-MM-dd'));
const maxDate = format(new Date(), 'yyyy-MM-dd');
const minDate = format(subDays(new Date(), 90), 'yyyy-MM-dd');
// <input type="date" value={from} min={minDate} max={to} onChange={...} />
```

`date-fns` is already in `frontend/webadmin/package.json` v3.6.0. Reuse it.

### 6.5 Shared types in `@shop/shared`

Add to `frontend/shared/src/types/`:

```typescript
// frontend/shared/src/types/reports.ts
export interface DashboardSummary {
  ordersToday: { total: number; pending: number; confirmed: number; delivering: number; delivered: number; cancelled: number };
  revenueToday: number;
  activeShippers: number;
  newCustomersToday: number;
  revenueLast7Days: { date: string; revenue: number; orderCount: number }[];
}

export interface RevenuePoint { date: string; revenue: number; orderCount: number; }

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
  cancelRate: number;
  byReason: { reason: string; count: number }[];
}
```

Export from `frontend/shared/src/types/index.ts` and surface via `frontend/shared/src/index.ts`.

---

## 7. Test strategy

### 7.1 Backend

| Test class | Location | Type | What it covers |
|------------|----------|------|----------------|
| `RatingServiceTest` | `delivery/src/test/.../service/` | Unit (Mockito) | Rate happy path; not-DELIVERED rejection; wrong-customer rejection; already-rated → ConflictException; rating_avg recompute call after insert |
| `RatingRepositoryIT` | `delivery/src/test/.../repository/` | Testcontainers (extend `OrderTestcontainerBase` pattern, but call it `DeliveryTestcontainerBase` if not already in `delivery`) | UNIQUE constraint on `order_id`; CHECK constraint on stars (insert 0 and 6 → expect violation); `existsByOrderId` query |
| `RatingControllerTest` | `delivery/src/test/.../api/customer/` | `@WebMvcTest` | 200 on valid POST; 400 on stars out of range; 409 already-rated; 422 not-delivered |
| `AdminReportsControllerTest` | `delivery/src/test/.../api/admin/` | `@WebMvcTest` + mocked repo | Query param validation (`groupBy=invalid` → 400; range > 90d → 400); response shape |
| `ReportsRepositoryIT` | `delivery/src/test/.../api/admin/reports/` | Testcontainers | Seed orders across 3 weeks with mixed status → verify revenue time-series row count, sum, weekly bucketing; cancellation reason groupBy; top-shippers ordering |
| `OrderAssignedNotifierTest` | `notification/src/test/.../` | Mockito | onOrderDelivered builds keyboard with 5 stars + skip; callback_data format matches `RATE:<uuid>:<n>` |
| `RatingCallbackHandlerTest` | `bot/src/test/.../handler/customer/` | Mockito | canHandle returns true for `RATE:` / `RATE_SKIP:`, false otherwise; handle parses correctly; rejects invalid stars; opens FSM after success; skips edit-keyboard on error |
| `RatingCommentHandlerTest` | `bot/src/test/.../handler/customer/` | Mockito | canHandle gated by FSM state; `/skip` clears state; text persisted via `updateComment` |
| `ConversationStateServiceTest` | `bot/src/test/.../` | Mockito + (optional IT for JSONB) | put-then-get round-trip; jsonb serialization of `Map<String,Object>`; stale cleanup query |

### 7.2 Frontend

Frontend currently has **no test framework** configured (no Vitest in `package.json`). For P8 we keep it minimal:

- **Type-check pass:** `pnpm --filter @shop/webadmin run type-check` (already in scripts).
- **Build pass:** `pnpm --filter @shop/webadmin run build` (catches Recharts integration issues).
- **Manual smoke test:** Start dev server, log in as admin, verify Dashboard renders KPIs + line chart, Reports page renders all three charts with sample data, date picker constraints work.

If the planner wants component tests, add Vitest + `@testing-library/react` in Wave 0 — but I'd argue thesis scope doesn't need it.

### 7.3 Wave 0 gaps

- [ ] No Testcontainers base in `delivery` module — copy `OrderTestcontainerBase.java` → `DeliveryTestcontainerBase.java` (uses same Postgres image with reuse, same Flyway-disabled DDL).
- [ ] `RatingServiceTest` etc. — new test files.
- [ ] Frontend: no Vitest config exists; manual smoke only for P8 (skip adding Vitest unless reviewer requires).

---

## 8. Risks / open questions

### 8.1 Concurrent rating writes for the same shipper
**Risk:** Two customers rate the same shipper simultaneously → both call `aggregateForShipper` + UPDATE → last write wins, both ratings are reflected because the aggregate is recomputed from the rating table (which has both rows).
**Status:** Solved by recompute strategy (§1.2). Not a concern.

### 8.2 Rating allowed only once per order
**Risk:** Customer clicks a star button multiple times before bot edits the keyboard.
**Mitigation:** `rating.order_id UNIQUE` enforces DB-level. The handler translates `DataIntegrityViolationException` → `ConflictException("ALREADY_RATED", ...)` and answers the callback with "Đơn đã được đánh giá rồi". Also, `removeKeyboard(...)` runs after successful rating, but a fast double-click can fire two callbacks before the edit — the UNIQUE catches the second one. ✓

### 8.3 What if the order is later REFUNDED / disputed?
**Decision:** Rating is **immutable** after creation. The `rating` table has no soft-delete column. If the order is refunded, the rating stays. Document in code comment on `Rating` entity. Spec §6.6 implies immutability ("lưu rating").

### 8.4 Customer rates an order that was never DELIVERED
**Mitigation:** `RatingService.rate` checks `order.getStatus() == DELIVERED` and throws `BusinessRuleException("ORDER_NOT_RATEABLE", ...)`. The bot only sends the prompt on `OrderDeliveredEvent`, so this is a defensive check.

### 8.5 Customer rates an order that isn't theirs
**Mitigation:** `RatingService.rate` checks `order.getCustomerId().equals(customerId)`. The bot can only callback from the chat where the prompt was sent (which is the customer's chat), so this is purely defensive — but Telegram callback queries can be triggered by anyone with the message link, so always validate.

### 8.6 Bot rating message is missed (user offline)
**Risk:** If the customer doesn't open Telegram, they never see the prompt. No retry mechanism is built.
**Decision:** Acceptable for thesis scope. The `POST /api/orders/:id/rating` endpoint exists as the canonical API — a future Mini App "My Orders" page could surface a "Rate now" button.

### 8.7 Date range > 90 days
**Mitigation:** Controller-level guard:
```java
if (ChronoUnit.DAYS.between(from, to) > 90) {
    throw new ValidationException("DATE_RANGE_TOO_LARGE", "Phạm vi tối đa 90 ngày");
}
```

### 8.8 `DATE_TRUNC('week', ...)` ISO Monday
**Concern:** Vietnam reviewers may expect Sunday-start weeks.
**Decision:** Stick with ISO Monday (PostgreSQL default). Document on the API. If reviewer disagrees, fix by `DATE_TRUNC('week', x + INTERVAL '1 day') - INTERVAL '1 day'` — but don't bother unless asked.

### 8.9 `status_history.note` is free-text
**Risk:** Cancellation reasons are arbitrary strings → byReason chart could have 100 distinct categories.
**Mitigation:** SQL `GROUP BY` aggregates exact matches and `LIMIT 10` keeps the chart legible. NULL/empty notes collapse into "Không ghi lý do". For thesis, this is fine. Future improvement: enum for reasons.

### 8.10 Existing dashboard placeholder calls `/api/admin/orders?size=50`
**Migration concern:** The new dashboard fully replaces the placeholder logic; remove the old `useQuery` that fetched orders for stat math. No backward-compat layer needed — the placeholder explicitly says "P9 (Reports)".

### 8.11 `ShipperProfile.ratingAvg` is `NUMERIC(3,2)` — max 9.99
**Note:** Field can hold a maximum of `9.99`. Since ratings are 1-5, the average is always ≤ 5.00 — no overflow. ✓ Verified against `V6__delivery.sql:9`.

### 8.12 Handler ordering in `UpdateRouter`
**Risk:** A future free-text handler could intercept the rating-comment input before `RatingCommentHandler` runs.
**Mitigation:** `@Order(0)` on `RatingCommentHandler`; `@Order(100)` on command/fallback handlers. Document this convention in the bot README.

---

## 9. Confidence assessment

| Area | Level | Reason |
|------|-------|--------|
| Rating schema (V10) | HIGH | Spec §5 + verified against existing V4/V6 patterns + DB constraint conventions |
| Rating module placement (`delivery`) | HIGH | Verified `delivery` already depends on `order` + `auth`; `notification` already cross-module |
| `rating_avg` recompute strategy | HIGH | Postgres READ COMMITTED gives consistent read; thesis-scale, perf irrelevant |
| Native query report SQL | HIGH | Patterns verified against `PaymentRepository` (existing native query convention) and PostgreSQL `DATE_TRUNC` / `generate_series` docs |
| Bot inline keyboard + callback flow | HIGH | Existing `OrderOfferCallbackHandler` is the template; callback_data length math checked |
| `ConversationStateService` JSONB + Hibernate 6 | MEDIUM | Hibernate 6.6 supports `@JdbcTypeCode(SqlTypes.JSON)` natively per Spring Boot 3.4 docs; not yet exercised in this codebase — flag for Wave 0 sanity test (insert + read round-trip in IT) |
| Recharts version + React 18 | HIGH | Verified via `npm view recharts version` → 3.8.1 (2026-03-25); React 18/19 compat per GitHub discussion |
| Module placement of reports endpoints | HIGH | Verified module dependency graph; `delivery` already depends on `order` + `auth` |
| WebSocket deferral | HIGH | Verified `WebSocketConfig` has no JWT path; adding would be a security risk in thesis scope |
| Handler ordering with `@Order` | MEDIUM | Spring's `OrderUtils` honors `@Order` on injected `List<T>` — well-documented but flag for IT verification |
| Status_history.note as cancellation reason source | HIGH | Verified `OrderService.cancel()` passes `reason` → `recordTransition(...)` → `note` column |
| `DATE_TRUNC('week', ...)` ISO Monday | HIGH | PostgreSQL official docs; multiple secondary sources confirm |

---

## 10. Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL 16 | All backend tests + dev | ✓ (Docker, infra/) | 16 | — |
| Java 17 | Backend build | ✓ | 17 | — |
| pnpm | Frontend | ✓ | workspaces configured | — |
| Telegram Bot API | Bot integration | ✓ (existing webhook) | 6.9.7.1 | — |
| Recharts 3.8.1 | Dashboard + Reports | (to install) | 3.8.1 | None — must add |
| Testcontainers | Repository ITs | ✓ | 1.20.4 | — |
| Vitest / @testing-library | Frontend tests | ✗ | — | Manual smoke + type-check + build (acceptable for thesis) |

**Missing dependencies with no fallback:** None blocking.

**Missing dependencies with fallback:** Frontend test framework — proceed without; rely on type-check + build + manual smoke.

---

## 11. Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Backend framework | JUnit 5 + Mockito + AssertJ + Spring Boot Test + Testcontainers 1.20.4 |
| Backend config files | `backend/pom.xml` (surefire/failsafe in parent); per-module `pom.xml`; `OrderTestcontainerBase` (replicate for `delivery`) |
| Backend quick run | `./mvnw -pl modules/delivery test -Dtest=RatingServiceTest -q` |
| Backend full suite | `./mvnw verify -q` |
| Frontend framework | None (type-check + build only) |
| Frontend quick run | `pnpm --filter @shop/webadmin run type-check` |
| Frontend full suite | `pnpm --filter @shop/webadmin run build` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| P8-RAT-01 | V10 migration runs cleanly | IT | `./mvnw -pl modules/delivery verify -Dtest=RatingRepositoryIT` | ❌ Wave 0 |
| P8-RAT-02 | Rating entity + repo persist | IT | same | ❌ Wave 0 |
| P8-RAT-03 | POST /api/orders/:id/rating returns 200 | unit | `./mvnw -pl modules/delivery test -Dtest=RatingControllerTest` | ❌ Wave 0 |
| P8-RAT-04 | shipper_profile.rating_avg recomputed | unit | `./mvnw -pl modules/delivery test -Dtest=RatingServiceTest` | ❌ Wave 0 |
| P8-RAT-05 | Bot sends keyboard on OrderDeliveredEvent | unit | `./mvnw -pl modules/notification test -Dtest=OrderAssignedNotifierTest` | ⚠️ exists, needs new test method |
| P8-RAT-06 | RatingCallbackHandler parses callback | unit | `./mvnw -pl modules/bot test -Dtest=RatingCallbackHandlerTest` | ❌ Wave 0 |
| P8-RAT-07 | FSM comment flow | unit | `./mvnw -pl modules/bot test -Dtest=RatingCommentHandlerTest` | ❌ Wave 0 |
| P8-RAT-08 | Remove keyboard via EditMessageReplyMarkup | unit | same as P8-RAT-06 | ❌ Wave 0 |
| P8-DSH-01 | Dashboard summary endpoint | unit + IT | `./mvnw -pl modules/delivery verify -Dtest=AdminDashboardControllerTest,ReportsRepositoryIT` | ❌ Wave 0 |
| P8-DSH-02 | Frontend dashboard builds | build | `pnpm --filter @shop/webadmin run build` | ✓ |
| P8-RPT-01 | Revenue series correct buckets | IT | `./mvnw -pl modules/delivery verify -Dtest=ReportsRepositoryIT` | ❌ Wave 0 |
| P8-RPT-02 | Top shippers sorted by deliveredCount | IT | same | ❌ Wave 0 |
| P8-RPT-03 | Cancellation reasons grouped | IT | same | ❌ Wave 0 |
| P8-RPT-04 | Frontend reports builds | build | `pnpm --filter @shop/webadmin run build` | ✓ |

### Sampling Rate
- **Per task commit:** Module-scoped test command (e.g., `./mvnw -pl modules/delivery test -q`)
- **Per wave merge:** Full backend suite + frontend type-check + build
- **Phase gate:** `./mvnw verify` green + `pnpm -r build` green + manual smoke video for thesis

### Wave 0 Gaps
- [ ] `DeliveryTestcontainerBase.java` — copy `OrderTestcontainerBase` into `delivery` module test support
- [ ] `RatingServiceTest.java` — happy path + 4 rejection cases
- [ ] `RatingRepositoryIT.java` — UNIQUE + CHECK constraints + aggregate query
- [ ] `RatingControllerTest.java` — `@WebMvcTest`
- [ ] `AdminDashboardControllerTest.java` — `@WebMvcTest`
- [ ] `AdminReportsControllerTest.java` — `@WebMvcTest` + param validation
- [ ] `ReportsRepositoryIT.java` — seed mixed orders, assert SQL
- [ ] `RatingCallbackHandlerTest.java`
- [ ] `RatingCommentHandlerTest.java`
- [ ] `ConversationStateServiceTest.java` + optional IT for JSONB round-trip
- [ ] New test method in `OrderAssignedNotifierTest.java` for keyboard build

---

## 12. Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Admin JWT (existing); customer initData (existing); bot callback `from.id` trusted (Telegram-signed) |
| V3 Session Management | yes | JWT refresh flow (existing); FSM state TTL via `conversation_state.updated_at` |
| V4 Access Control | yes | `@PreAuthorize("hasRole('SHOP_OWNER')")` on all `/api/admin/**`; `customerId` ownership check in `RatingService.rate` |
| V5 Input Validation | yes | `@Valid` + `@NotNull` on request DTOs; `stars` range check at DTO + entity + DB CHECK; `groupBy` whitelist before DATE_TRUNC |
| V6 Cryptography | no | No new crypto in P8 |
| V13 API Security | yes | Date-range cap (90 days) prevents pathological queries; pagination not needed at this size |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection via `groupBy` param | Tampering | Whitelist `'day'` / `'week'` in controller before `@Query` (`if (!Set.of("day","week").contains(bucket)) throw new ValidationException(...)`) |
| SQL injection via date params | Tampering | Spring Data binds `@Param` — type-safe `LocalDate` parser already rejects malformed input |
| Cross-customer rating (rate someone else's order) | Spoofing / Tampering | `RatingService.rate` validates `order.customerId == authCustomerId` |
| Replay rating (double-click) | Tampering | UNIQUE(order_id) + `DataIntegrityViolationException` → 409 |
| Callback data tampering (user crafts fake `RATE:` callback) | Spoofing | Telegram signs `from.id`; `RatingService.rate` re-validates ownership + state via DB; UUID format check prevents trivial probes |
| Date-range DoS (query 10 years of data) | DoS | 90-day cap in controller |
| XSS via comment in admin shipper detail | Tampering | React escapes by default; no `dangerouslySetInnerHTML` allowed |
| Stars out of range | Tampering | `@Min(1) @Max(5)` on DTO + entity validator + DB CHECK |

---

## 13. Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.4.0 | Framework | Already in use (parent POM) |
| Spring Data JPA | (managed) | Repository + native queries | Already in use |
| Hibernate | 6.6 (via SB 3.4) | JPA provider; `@JdbcTypeCode(SqlTypes.JSON)` for jsonb | Native jsonb support without 3rd-party |
| PostgreSQL | 16 | DB + DATE_TRUNC + generate_series | Already in use; required for window functions |
| Flyway | (managed) | Schema migration | Already in use |
| telegrambots-spring-boot-starter | 6.9.7.1 | Bot SDK | Already in use |
| recharts | 3.8.1 | Chart library | [VERIFIED: npm view → 2026-03-25]; React 18 supported; tree-shakeable |
| @tanstack/react-query | 5.59.0 (existing) | Polling for dashboard | Already in use; `refetchInterval` is idiomatic |
| date-fns | 3.6.0 (existing) | Date math in frontend | Already in use |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AssertJ | (managed) | Test assertions | All service / repo tests |
| Mockito | (managed) | Service unit tests | When real DB not needed |
| Testcontainers | 1.20.4 | Postgres-backed IT | All repository ITs |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Recharts | Chart.js + react-chartjs-2 | Imperative API, requires `<canvas>`, less idiomatic in React 18 — Recharts wins for declarative composability |
| Recharts | Apache ECharts | Heavier (~250 KB gzipped), more powerful — overkill for thesis |
| Native SQL via `@Query` | JPQL | JPQL doesn't have DATE_TRUNC; would need Hibernate-specific functions — native SQL is cleaner |
| `hypersistence-utils-hibernate-63` | Hibernate 6's native `@JdbcTypeCode(SqlTypes.JSON)` | Saves one dependency; works for `Map<String,Object>` round-trip |
| TanStack Query polling | WebSocket admin push | WS needs JWT auth on STOMP CONNECT — added complexity for marginal UX gain |
| Incremental `rating_avg` update | Recompute from aggregate | Recompute avoids drift + lock contention; cost negligible at thesis scale |
| New `rating` module | Put in `delivery` | One entity doesn't justify a module |

### Installation
```bash
# Frontend
cd frontend/webadmin
pnpm add recharts@^3.8.1

# Backend — no new dependencies needed; all libs already in parent POM
```

### Version Verification
[VERIFIED: 2026-06-01]
- `npm view recharts version` → `3.8.1` (published 2026-03-25T12:12:20.764Z)
- `recharts dist-tags` → `{ alpha: '3.0.0-alpha.9', beta: '3.0.0-beta.2', latest: '3.8.1' }`

---

## 14. Architecture Patterns

### Recommended structure additions
```
backend/modules/delivery/src/main/java/com/shop/delivery/delivery/
├── entity/Rating.java                          [NEW]
├── repository/RatingRepository.java            [NEW]
├── repository/ReportsRepository.java           [NEW — native SQL queries]
├── service/RatingService.java                  [NEW]
├── service/ReportsQueryService.java            [NEW]
├── api/customer/RatingController.java          [NEW]
├── api/customer/dto/RateOrderRequest.java      [NEW]
├── api/admin/AdminDashboardController.java     [NEW]
├── api/admin/AdminReportsController.java       [NEW]
├── api/admin/dto/DashboardSummary.java         [NEW]
├── api/admin/dto/RevenuePoint.java             [NEW]
├── api/admin/dto/TopShipperRow.java            [NEW]
└── api/admin/dto/CancellationReport.java       [NEW]

backend/modules/bot/src/main/java/com/shop/delivery/bot/
├── handler/customer/RatingCallbackHandler.java [NEW]
├── handler/customer/RatingCommentHandler.java  [NEW]
├── fsm/ConversationState.java                  [NEW — JPA entity]
├── fsm/ConversationStateRepository.java        [NEW]
└── fsm/ConversationStateService.java           [NEW]

backend/modules/notification/src/main/java/com/shop/delivery/notification/
├── OrderAssignedNotifier.java                  [MODIFY — onOrderDelivered]
└── RatingPromptBuilder.java                    [NEW]

backend/app/src/main/resources/db/migration/
└── V10__rating.sql                             [NEW]

frontend/webadmin/src/
├── pages/DashboardPage.tsx                     [REWRITE]
├── pages/ReportsPage.tsx                       [NEW]
├── components/charts/RevenueChart.tsx          [NEW]
├── components/charts/TopShippersChart.tsx      [NEW]
└── components/charts/CancellationChart.tsx     [NEW]

frontend/shared/src/types/reports.ts            [NEW]
```

### Pattern 1: `@TransactionalEventListener` for cross-module notification
**What:** Domain modules publish events via `ApplicationEventPublisher`; the `notification` module listens with `@TransactionalEventListener(phase = AFTER_COMMIT)` to send Telegram messages.
**When:** Whenever a domain state change should trigger a Telegram message.
**Example:** see `OrderAssignedNotifier.onOrderDelivered` — modify it to attach the rating keyboard.

### Pattern 2: Interface projection for native SQL DTOs
**What:** Define a Spring Data JPA interface with getters; Spring proxies the result rows.
**When:** Multi-row aggregate queries where you want a typed DTO without `@SqlResultSetMapping` ceremony.
**Example:** `RatingRepository.RatingStats { BigDecimal getAvg(); Integer getCount(); }`.

### Pattern 3: TanStack Query polling for admin pages
**What:** `useQuery({ refetchInterval: 30000, refetchOnWindowFocus: true })`.
**When:** Admin SPA needs "fresh enough" data without WebSocket complexity.
**Example:**
```tsx
const { data } = useQuery({
  queryKey: ['admin', 'dashboard'],
  queryFn: () => api.get<DashboardSummary>('/api/admin/dashboard/summary').then(r => r.data),
  refetchInterval: 30000,
  refetchOnWindowFocus: true,
  staleTime: 10000,
});
```

### Pattern 4: Inline keyboard + EditMessageReplyMarkup
**What:** Send inline buttons via `InlineKeyboardMarkup`; on callback, edit message to remove buttons via `EditMessageReplyMarkup` with empty list.
**When:** One-shot user choice flows (rating, accept/reject).
**Example:** see `OrderOfferCallbackHandler` for the existing pattern; `RatingCallbackHandler.removeKeyboard(...)` follows it.

### Pattern 5: JSONB column with Hibernate 6
**What:** `@Column(columnDefinition = "jsonb") @JdbcTypeCode(SqlTypes.JSON) Map<String,Object> data;`
**When:** Free-form structured data without a fixed schema (FSM state context).
**Why no 3rd-party:** Hibernate 6.6 has native support; no need for `hypersistence-utils`.

### Anti-Patterns to Avoid
- **Hand-rolled aggregate math in Java** for top-shippers / revenue. SUM/GROUP BY in SQL is faster and more readable.
- **Storing rating in a separate `rating_log` + cached `rating_avg`** with eventual consistency. Just recompute — simpler, exact.
- **Passing `groupBy` straight into `DATE_TRUNC`** without whitelist. Validate at the controller.
- **WebSocket admin auth** in P8 — adds a security surface without proportional UX benefit at thesis scale.
- **Custom date picker library** — `<input type="date">` is fine.

---

## 15. Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Chart rendering | Custom SVG / canvas | Recharts 3.8.1 | Accessibility, tooltip positioning, responsive resize, animation — all solved |
| Date bucketing in Java | Group orders into a `TreeMap<LocalDate, BigDecimal>` in service | `DATE_TRUNC` in SQL | Single round-trip, indexed, exact |
| Rating-avg incremental math | `new_avg = (old*n + s) / (n+1)` with `@Version` | `SELECT AVG(stars) FROM rating WHERE shipper_id = ?` | Avoids floating-point drift + lock contention |
| JSONB serialization | Custom `AttributeConverter<Map, String>` with Jackson | `@JdbcTypeCode(SqlTypes.JSON)` | Native in Hibernate 6 |
| Inline keyboard layout | Build button rows manually each time | Builder helper `RatingPromptBuilder` | Testable, reusable |
| Idempotent rating writes | "Did we already rate?" check in Java | UNIQUE constraint + catch DataIntegrityViolationException | DB-level guarantee |
| Vietnamese currency formatting | `value.toLocaleString('vi-VN') + ' ₫'` inline everywhere | `formatVnd()` from `@shop/shared` | Already exists |
| Date range picker | React-datepicker | `<input type="date" min={...} max={...}>` | Native, accessible, zero deps |
| Cancellation reason categorization | Free-text fuzzy match | `GROUP BY status_history.note` + LIMIT 10 | Simple, correct |

**Key insight:** P8 has no novel hard problems — everything maps to an existing pattern in the codebase or a battle-tested library. The temptation to build "custom dashboards" or "smart rating algorithms" should be resisted; the goal is to ship the should-have features cleanly and demo them for the thesis defense.

---

## 16. Common Pitfalls

### Pitfall 1: `DATE_TRUNC` boundary inclusivity
**What goes wrong:** `WHERE created_at BETWEEN :from AND :to` excludes the second half of the `to` day (TIMESTAMPTZ `to` is interpreted as `2026-06-01 00:00:00+00`, missing orders later that day).
**Why it happens:** SQL `BETWEEN` is value-equality, not "day-inclusive".
**How to avoid:** Use `>= :from AND < :to + INTERVAL '1 day'` everywhere.
**Warning signs:** Last day of report missing orders that exist; user reports "today's revenue is wrong".

### Pitfall 2: Forgetting `generate_series` for sparse data
**What goes wrong:** Dashboard 7-day chart has gaps on days with zero DELIVERED orders.
**Why it happens:** SQL `GROUP BY DATE_TRUNC` only returns rows that exist.
**How to avoid:** Use `LEFT JOIN generate_series(...)` for fixed-window dashboards. For variable-range reports, don't bother — the FE can render sparse data.

### Pitfall 3: Callback data exceeding 64 bytes
**What goes wrong:** Long-form callback data silently fails or Telegram rejects the message.
**Why it happens:** Telegram API spec — `callback_data` ≤ 64 UTF-8 bytes.
**How to avoid:** UUID (36 chars) + small prefix is fine. Don't add user names or addresses to callback data.
**Warning signs:** `BUTTON_DATA_INVALID` error from Telegram API.

### Pitfall 4: FSM state collision across handlers
**What goes wrong:** A new "/help" text message arrives while user is in `CUSTOMER_RATING_COMMENT` state → `/help` handler runs and FSM is never cleared.
**Why it happens:** Handler order in `UpdateRouter` is non-deterministic; command handlers don't check FSM.
**How to avoid:** `@Order(0)` on FSM-bound handlers; command handlers check `conv.get(userId).isPresent()` and clear state on command entry.
**Warning signs:** User reports "bot doesn't understand my comment".

### Pitfall 5: Modifying `rating_avg` outside `RatingService`
**What goes wrong:** Some future code path updates `shipper_profile.rating_avg` directly → desync with `rating` table.
**Why it happens:** No protection on the column.
**How to avoid:** Document `rating_avg` and `rating_count` as derived columns owned by `RatingService`. Consider a `@PreUpdate` guard or comment in the entity.

### Pitfall 6: Postgres `BETWEEN` with TIMESTAMPTZ comparing to LocalDate
**What goes wrong:** Hibernate binds `LocalDate` as `DATE`; PostgreSQL compares TIMESTAMPTZ to DATE in UTC — Vietnamese local-time dates are off by 7 hours.
**Why it happens:** Server timezone is UTC; user thinks in Asia/Ho_Chi_Minh.
**How to avoid:** Two strategies — (a) accept TIMESTAMPTZ in the query and compute the bounds in Java using `ZoneId.of("Asia/Ho_Chi_Minh").atStartOfDay()`, OR (b) accept that "today" means "today UTC" and document it. For thesis, **(b)** is fine — deploy server in Asia/Ho_Chi_Minh timezone (`TZ=Asia/Ho_Chi_Minh` env var) so `CURRENT_DATE` matches user expectation.
**Warning signs:** Late-evening orders missing from "today's revenue".

### Pitfall 7: Recharts ResponsiveContainer requires fixed parent height
**What goes wrong:** Chart renders 0px tall, invisible.
**Why it happens:** ResponsiveContainer measures parent — if parent has no height, container is 0.
**How to avoid:** Wrap charts in `<div style={{ width: '100%', height: 300 }}>` or use Tailwind `h-72`.

### Pitfall 8: `@TransactionalEventListener(AFTER_COMMIT)` swallows exceptions
**What goes wrong:** Bot send fails silently — `OrderDeliveredEvent` already committed.
**Why it happens:** AFTER_COMMIT runs outside the transaction; exceptions are logged but don't roll back.
**How to avoid:** Already the pattern (see `BotSender` swallows `TelegramApiException` with logging). For rating prompt failures, this is acceptable — the customer can rate later via Mini App.

---

## 17. State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `hypersistence-utils-hibernate-XX` for jsonb | Hibernate 6 native `@JdbcTypeCode(SqlTypes.JSON)` | Hibernate 6.2 / Spring Boot 3.1 | Zero extra deps |
| Chart.js + react-chartjs-2 | Recharts 3.x (declarative React) | Recharts 3.0 (2025-06) | Tree-shakeable, smaller |
| Native query `@SqlResultSetMapping` | Interface projection | Spring Data JPA 2.0+ | Less boilerplate |
| WebSocket for everything | Polling for low-frequency admin UI | TanStack Query era | Simpler, debuggable |

**Deprecated/outdated:**
- Recharts 1.x / 2.x: Use 3.x for active maintenance and React 18 first-class support.
- Manual `@SqlResultSetMapping` for DTO projections in Spring Data: prefer interface projections.

---

## 18. Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring server timezone is Asia/Ho_Chi_Minh (or `CURRENT_DATE` is acceptable as UTC for thesis) | §3.1, §16 Pitfall 6 | "Today's revenue" off by 7 hours late-evening |
| A2 | `status_history.note` reliably contains the cancellation reason | §3.4 | If notes are empty for some cancels, the byReason chart shows mostly "Không ghi lý do" — still works, just less informative |
| A3 | Hibernate 6.6 in Spring Boot 3.4 supports `@JdbcTypeCode(SqlTypes.JSON)` for `Map<String,Object>` without extra deps | §4.3 | If not, add `hypersistence-utils-hibernate-63` 3.x — small change |
| A4 | Recharts 3.8.1 works with React 18.3.1 + Vite 5.4 | §6.1 | If breaks, downgrade to 3.4.x (last version pre-deprecation of Cell) |
| A5 | `@Order` on Spring `@Component` is honored when injected as `List<T>` | §4.4, §16 Pitfall 4 | If not, manually sort handlers in `UpdateRouter` constructor |
| A6 | 90-day data cap is acceptable for thesis demo | §1, §3.2 | Reviewer asks for full history — easy to remove the cap |
| A7 | Customer can edit rating comment within same FSM session, not edit stars after submit | §4.6 | Reviewer wants edit-stars — small extension to `RatingService` |

---

## 19. Open Questions

1. **Server timezone — UTC or Asia/Ho_Chi_Minh?**
   - What we know: Postgres defaults to UTC in Docker; Spring uses JVM timezone.
   - What's unclear: Whether the existing infra has `TZ=Asia/Ho_Chi_Minh` set.
   - Recommendation: Verify in `infra/` Docker compose; if not, add it for P8. Or, document "today = UTC day" in the API doc.

2. **Should `RATE_SKIP` write any row?**
   - Decision: No. Skip just removes the keyboard and answers the callback. No DB write. The order has no rating, which is indistinguishable from "user never opened the message". The shipper's `rating_count` reflects only actual ratings — which is what we want.

3. **What if customer rates 1 star — escalation?**
   - Out of scope. Spec doesn't mention escalation. Could add a future "low-rating triggers admin notification" but not in P8.

4. **Pagination on rating history for shipper detail page?**
   - Out of scope for P8 (no shipper detail page changes). If added, use existing `Pageable` pattern from `AdminOrderController`.

5. **Currency display: round to integer VND or show decimals?**
   - `formatVnd` already rounds to integer (see `format-money.ts:4`). Consistent across the app. Keep it.

---

## 20. Sources

### Primary (HIGH confidence)
- Codebase inspection (verified against actual files in this session):
  - `backend/pom.xml` — multi-module structure, Java 17, Spring Boot 3.4
  - `backend/modules/{order,delivery,bot,notification,payment}/pom.xml` — module dependencies
  - `backend/app/src/main/resources/db/migration/V*.sql` — Flyway schema
  - `backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderService.java` — status_history population
  - `backend/modules/notification/src/main/java/com/shop/delivery/notification/OrderAssignedNotifier.java` — TransactionalEventListener pattern
  - `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/shipper/OrderOfferCallbackHandler.java` — callback handler template
  - `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/DeliveryAssignmentService.java` — OrderDeliveredEvent publish point
  - `backend/modules/payment/src/main/java/com/shop/delivery/payment/repository/PaymentRepository.java` — native query pattern
  - `frontend/webadmin/package.json` — dep manifest
  - `frontend/shared/src/utils/format-money.ts` — `formatVnd` helper
- Project spec: `docs/superpowers/specs/2026-05-19-he-thong-quan-ly-giao-hang-design.md` (§5 rating table, §6.6 rating flow, §9.3 admin endpoints, §11.1 event listeners)
- `npm view recharts version` → 3.8.1 (2026-03-25) — registry truth
- [Telegram Bot API — InlineKeyboardButton](https://core.telegram.org/bots/api#inlinekeyboardbutton) — 64-byte callback_data limit (well-known spec)
- [PostgreSQL DATE_TRUNC docs](https://neon.com/docs/functions/date_trunc) — ISO Monday week alignment, supported parts

### Secondary (MEDIUM confidence)
- [Spring Data JPA Projections](https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html) — interface projection support for native queries
- [Recharts 3.8.1 release notes](https://github.com/recharts/recharts/releases) — TypeScript improvements, React 18/19 compat
- [Telegram callback_data limit discussion](https://github.com/python-telegram-bot/python-telegram-bot/issues/3528) — confirms 64-byte limit
- [PostgreSQL group-by-week tutorial](https://learnsql.com/blog/postgresql-group-by-week/) — DATE_TRUNC week patterns
- [Hibernate 6 jsonb mapping discussion](https://github.com/recharts/recharts/discussions/5698) — Recharts React-version-compat thread

### Tertiary (LOW confidence — flagged for validation in Wave 0)
- Exact behavior of `@Order` on Spring `@Component` list injection — verify in `UpdateRouterTest` by registering two handlers with different orders.
- JSONB round-trip with Hibernate 6.6 + Map — verify in `ConversationStateServiceIT`.

---

## 21. Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Recharts version registry-verified; backend stack matches existing modules
- Architecture (module placement, patterns): HIGH — Verified module dependency graph in pom.xml files
- SQL queries: HIGH — patterns verified against existing PaymentRepository; PostgreSQL DATE_TRUNC docs confirmed
- Bot FSM: MEDIUM — first FSM in codebase; pattern is correct but unvalidated until Wave 0 IT
- Frontend Recharts integration: MEDIUM — version verified; component composition is canonical Recharts but specific styling needs runtime check
- Concurrency / rating_avg math: HIGH — recompute strategy is bulletproof

**Research date:** 2026-06-01
**Valid until:** 2026-07-01 (30 days — Recharts is stable; PostgreSQL semantics stable; spec is stable)
