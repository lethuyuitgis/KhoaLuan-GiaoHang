# P8 Code Review — Dashboard + Reports + Rating

**Reviewed:** 2026-06-01
**Commit range:** `fa2d8ab^..f0d80f6` (16 commits)
**Reviewer:** Claude (gsd-code-reviewer)

## Summary

- Files reviewed: 52 (3,534 insertions, 47 deletions)
  - Backend (rating): V10 migration, Rating entity + repo + service, RatingController + DTO, 3 test files
  - Backend (bot FSM + handlers): ConversationState entity/repo/service + 2 tests, RatingCallbackHandler, RatingCommentHandler + 2 tests, UpdateRouterOrderIT
  - Backend (notification): OrderAssignedNotifier change, RatingPromptBuilder + 2 tests
  - Backend (reports): ReportsRepository, ReportsQueryService, AdminDashboardController, AdminReportsController + 4 DTOs + 3 test files
  - Frontend (shared): types/reports.ts, api/reports.ts, barrel updates
  - Frontend (webadmin): DashboardPage rewrite, ReportsPage, 4 chart components, Sidebar + App.tsx + package.json
- Critical: 0
- Important: 4
- Minor: 6
- **Overall verdict: PASS** — every Critical guard called out in the review brief is correctly implemented in the committed code. The whitelist on `groupBy`, transactional atomicity of rate-insert + shipper-stats recompute, idempotency (UNIQUE index + `existsByOrderId` check), callback-data size, FSM state cleanup, admin-endpoint authorization (defence-in-depth: SecurityConfig + `@PreAuthorize` both fire), and `@CurrentUser TelegramUser` on `RatingController` all landed correctly. Important items are deferrable polish — not blockers.

The cross-module flow (bot handler → delivery service → JPA + Postgres aggregate → shipper_profile mutation) is wired correctly; integration tests cover the round-trip and the UNIQUE-on-order_id race.

---

## Critical findings

_None._ All seven Critical concerns from the review brief are correctly handled:

1. **`groupBy` SQL injection** — `AdminReportsController:25,85-90` whitelists against `Set.of("day", "week")` BEFORE the value reaches the native query, throwing `ValidationException("INVALID_GROUP_BY")` if it doesn't match. Test coverage at `AdminReportsControllerTest:59-66` verifies `groupBy=month` returns 400. The native query at `ReportsRepository:102` binds via `:bucket` so Postgres treats the value as a `DATE_TRUNC` field literal — combined with the whitelist, no attacker-controlled string can reach `DATE_TRUNC`.
2. **Rating atomicity** — `RatingService.rate` is annotated `@Transactional` (line 54). `saveAndFlush` (line 85) and `recomputeShipperStats` (line 91 → `shipperRepo.save` at line 118) run in the same transaction. If the recompute throws, the rating insert rolls back. Confirmed by tracing: `recomputeShipperStats` is called within the same method scope, no `Propagation.REQUIRES_NEW`.
3. **Idempotency: rating-once-per-order** —
   - `rating.order_id UNIQUE` enforced at DB level (`V10__rating.sql:10`).
   - Catch on `DataIntegrityViolationException` translates to `ConflictException("ALREADY_RATED")` (`RatingService:86-89`).
   - Bot pre-check before sending keyboard: `OrderAssignedNotifier.onOrderDelivered` at line 96-99 calls `ratingRepo.existsByOrderId(e.orderId())` and short-circuits with a `log.debug` if already rated. Test `OrderAssignedNotifierTest:54-68` verifies the skip path.
4. **Callback data ≤ 64 bytes** — `RatingPromptBuilder:29` builds `RATE:<UUID>:<digit>` (43 bytes, well under). `RATE_SKIP:<UUID>` is 46 bytes. Both are explicitly asserted via `getCallbackData().getBytes().length ≤ 64` in `RatingPromptBuilderTest:30-33`. UUID format (not order code) is enforced by passing `e.orderId()` (a `UUID` from the event) — not the human-readable code.
5. **FSM state cleanup** — `RatingCommentHandler` clears state on every terminal path:
   - `/skip` (case-insensitive): line 64-67 → `conv.clear(userId)`.
   - Empty text after trim: line 70-74 → `conv.clear(userId)`.
   - Success after `updateComment`: line 90-92 → `conv.clear(userId)`.
   - Invalid UUID payload: line 93-95 → `conv.clear(userId)`.
   - `DomainException` from service: line 96-100 → `conv.clear(userId)`.
   All five paths are covered by `RatingCommentHandlerTest` (lines 69-117). The state cannot linger.
6. **Auth on admin endpoints** — Defense-in-depth:
   - `SecurityConfig.java:46`: `.requestMatchers("/api/admin/**").hasRole("SHOP_OWNER")` — filter-chain level.
   - `AdminDashboardController:12`: `@PreAuthorize("hasRole('SHOP_OWNER')")` — controller level.
   - `AdminReportsController:22`: `@PreAuthorize("hasRole('SHOP_OWNER')")` — controller level.
   - `@EnableMethodSecurity(prePostEnabled = true)` is set on `SecurityConfig:19`.
   - Both `AdminDashboardControllerTest.summary_unauthenticated_returns401or403` (line 66-75) and `AdminReportsControllerTest.anyEndpoint_unauthenticated_returns401or403` (line 139-150) verify the unauthenticated path returns 401/403.
7. **`@CurrentUser` on RatingController** — Verified at `RatingController.java:32`:
   ```java
   @CurrentUser TelegramUser user
   ```
   The import is `com.shop.delivery.auth.api.CurrentUser` (line 3). The plan-checker patch landed correctly — no `@AuthenticationPrincipal UserDetails` regression. Test at `RatingControllerTest:62-69` uses `requestAttr("currentUser", testUser)` matching the `CurrentUserArgumentResolver` contract.

---

## Important findings

### IM-01: `ReportsRepositoryIT` does NOT seed `status_history`, so `cancellationByReason` is never integration-tested

**File:** `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/repository/ReportsRepositoryIT.java:131-141`

**Issue:** The IT covers `revenueLast7Days`, `revenueSeries` (day + week buckets), `cancellationTotals`, and "empty range" — but there is no integration test that seeds rows in `status_history` and asserts `cancellationByReason` returns the expected `(reason, count)` tuples. The native SQL at `ReportsRepository:177-191` joins `status_history sh` and trims/coalesces `sh.note` to `'Không ghi lý do'`. Without an IT, the cross-table join, the `to_status = 'CANCELLED'` filter, and the COALESCE on empty notes are all relying on the controller test (which mocks the service) — there's no end-to-end coverage that the SQL actually returns what the service layer expects.

Specifically, the brief says: *"If the note is `null` for old data, the byReason map should fall back to 'Không có lý do'."* The code uses `'Không ghi lý do'` (semantically equivalent but slightly different from the brief's wording — fine). The bigger gap is that no test exercises the NULL-note → fallback-string path.

**Suggested fix:** Add a test in `ReportsRepositoryIT`:
```java
@Test
void cancellationByReason_coalescesNullAndEmptyNotes() {
    LocalDate today = LocalDate.now();
    UUID orderId1 = seedOrder(today, BigDecimal.valueOf(100_000), OrderStatus.CANCELLED);
    UUID orderId2 = seedOrder(today, BigDecimal.valueOf(100_000), OrderStatus.CANCELLED);
    UUID orderId3 = seedOrder(today, BigDecimal.valueOf(100_000), OrderStatus.CANCELLED);
    seedStatusHistory(orderId1, "Khách huỷ");
    seedStatusHistory(orderId2, "");      // empty → fallback
    seedStatusHistory(orderId3, null);    // null → fallback

    List<ReportsRepository.ReasonCount> reasons = repo.cancellationByReason(
        today.minusDays(1), today);

    assertThat(reasons).extracting(r -> r.getReason() + ":" + r.getCnt())
        .containsExactlyInAnyOrder("Khách huỷ:1", "Không ghi lý do:2");
}
```

This is Important rather than Critical because the SQL is short and visually correct; missing test coverage is a thesis-completeness concern.

### IM-02: `ReportsQueryService.dashboardSummary` calls `repo.revenueToday()` twice in the null-check ternary

**File:** `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/ReportsQueryService.java:34`

**Issue:**
```java
BigDecimal revenue = repo.revenueToday() == null ? BigDecimal.ZERO : repo.revenueToday();
```

The native query at `ReportsRepository:39-46` wraps the SUM in `COALESCE(SUM(total), 0)`, so it can never actually return null. But assuming it could, the ternary calls the query twice — two round-trips to Postgres for a single field. Negligible perf cost, but it's an obvious code-quality smell. Either:
- Drop the null check entirely (COALESCE in SQL already handles it):
  ```java
  BigDecimal revenue = repo.revenueToday();
  ```
- Or save to a local:
  ```java
  BigDecimal raw = repo.revenueToday();
  BigDecimal revenue = raw == null ? BigDecimal.ZERO : raw;
  ```

Suggest the first form — the SQL already guarantees non-null and the code becomes shorter and matches `activeShippers` / `newCustomersToday` which trust the query.

### IM-03: Bundle size — webadmin grew from 313 kB to 713 kB; no lazy-load on `/reports`

**File:** `frontend/webadmin/src/App.tsx:14,32`

**Issue:** The review brief flags this directly. `ReportsPage` is imported eagerly at the top of `App.tsx`, so the 400 kB of recharts code is in the initial bundle even for users who never visit `/reports`. Vite emits a warning above 500 kB raw / ~140 kB gzipped.

**Suggested fix:** Convert to a lazy import:
```typescript
import { lazy, Suspense } from 'react';
const ReportsPage = lazy(() => import('./pages/ReportsPage').then(m => ({ default: m.ReportsPage })));
```
And wrap the route:
```jsx
<Route path="reports" element={
  <Suspense fallback={<p>Đang tải...</p>}>
    <ReportsPage />
  </Suspense>
} />
```

The Dashboard's `RevenueMiniChart` still pulls recharts into the main bundle, so this only saves ~150-200 kB rather than the full 400 kB. Acceptable trade-off — `/reports` is the heavy page and most admin sessions land on `/`.

### IM-04: `RatingController.rate` returns the raw `Rating` JPA entity — leaks internal `id` + `shipperId`

**File:** `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/customer/RatingController.java:29-36`

**Issue:** The controller returns `ResponseEntity<Rating>` directly. The serialized JSON includes `id` (BIGSERIAL DB key), `customerId`, `shipperId`, `createdAt`, and `comment` verbatim. For a thesis app this is mostly fine, but it's a pattern violation — other customer-facing controllers in this codebase return DTOs (e.g., `OrderResponse`, `RateOrderRequest` request side). Exposing the `id` BIGSERIAL also encourages clients to use it as a stable handle when really only `orderId` should be addressable.

**Suggested fix:** Add `RateOrderResponse`:
```java
public record RateOrderResponse(UUID orderId, int stars, String comment, Instant createdAt) {}
```
And in the controller:
```java
return ResponseEntity.ok(new RateOrderResponse(r.getOrderId(), r.getStars(), r.getComment(), r.getCreatedAt()));
```

Defer if you want to keep the diff small — no security impact (customer already knows their own orderId and shipperId is fine to expose in this domain).

---

## Minor findings

### MN-01: `Rating.stars` widened to `Short` then narrowed back to `int` at API boundary — readability cost, no bug

**Files:**
- `Rating.java:42` — `private Short stars;`
- `RatingService.java:81` — `r.setStars((short) stars);` (downcast from `int`)
- `RatingController.java:34` — `body.stars()` is `Integer`

The `SMALLINT` column choice is correct (saves bytes), but the JPA mapping bouncing through `Short` adds two cast points. Future maintainers may add a third cast and get it wrong. Consider a single typed getter that returns `int` for client code:
```java
public int getStarsAsInt() { return stars == null ? 0 : stars.intValue(); }
```
Pure style — not a defect.

### MN-02: `RatingCallbackHandler` answers TWO callback queries on the success path

**File:** `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/customer/RatingCallbackHandler.java:117-124`

After `ratingService.rate()` succeeds, the handler:
1. Calls `removeKeyboard(...)` (sends `EditMessageReplyMarkup`)
2. Calls `sender.execute(AnswerCallbackQuery...)` with "Cảm ơn bạn!"
3. Calls `sender.sendText(chatId, "Bạn có muốn nhập nhận xét? ...")`

Telegram only allows one `answerCallbackQuery` per callback (subsequent calls are no-ops with a 400). This works fine in practice — only one `AnswerCallbackQuery` is sent on the success path. But the `removeKeyboard` call sends an `EditMessageReplyMarkup` which is a separate Bot API call and counts against rate limits. For thesis demo: fine. At scale (>20 ratings/sec/chat) you might hit 429s.

Suggest leaving as-is; flagging only because the brief asks for cross-cut review.

### MN-03: `MockBean` deprecation warnings will appear at build time

**Files:**
- `RatingControllerTest.java:41` — `@MockBean RatingService ratingService;`
- `AdminDashboardControllerTest.java:36` — `@MockBean ReportsQueryService service;`
- `AdminReportsControllerTest.java:38` — `@MockBean ReportsQueryService service;`

Spring Boot 3.4 deprecates `org.springframework.boot.test.mock.mockito.MockBean` in favour of `org.springframework.test.context.bean.override.mockito.MockitoBean`. The brief flags this as out of scope (module-wide refactor). Just noting it'll show up in the build log.

### MN-04: Tooltip `formatter` types in recharts 3.x — works but uses loose typing

**Files:**
- `RevenueChart.tsx:21-25`
- `RevenueMiniChart.tsx:21-25`
- `CancellationChart.tsx:18`
- `TopShippersChart.tsx:20`

Recharts 3.x tightened the `Tooltip formatter` callback signature. The code stringifies values defensively (`String(value)`, `Number(value)`) which avoids type errors but assumes a specific runtime shape. Works correctly — no fix needed. The W1 deviation from the plan-checker (stringify values for safety) is consistent across all four chart files.

### MN-05: `revenueGenerated` and `ratingAvg` are `BigDecimal` in DTO but `number` in TS

**Files:**
- `backend/.../dto/TopShipperRow.java:9-10` — `BigDecimal revenueGenerated`, `BigDecimal ratingAvg`
- `frontend/shared/src/types/reports.ts:36-37` — `revenueGenerated: number`, `ratingAvg: number`

Jackson serializes `BigDecimal` as a JSON number by default (no `WRITE_BIGDECIMAL_AS_PLAIN`-related quirks here). For values in the range `[0, 10_000_000_000]` (thesis-scale revenue) this is safe — well below `Number.MAX_SAFE_INTEGER` (2^53 - 1 ≈ 9 × 10^15). However, if revenue ever exceeds `2^53 - 1` cents (very unlikely in thesis), precision loss could occur. The `DashboardPage` already handles this via `Number(s.ratingAvg).toFixed(2)` (line 74). Note it's already gracefully cast.

### MN-06: `Sidebar.tsx` insertion order — "Báo cáo" sits between Orders and Products

**File:** `frontend/webadmin/src/components/Sidebar.tsx:7`

The new "Báo cáo" link is inserted at index 2 (between Đơn hàng and Sản phẩm). Information-hierarchy nit: Dashboard → Đơn hàng → Sản phẩm → Shipper → Báo cáo (reports usually go last as they cover historical data, not operational). Pure subjective UX call; leave if the team prefers reports near the top.

---

## What was done well

1. **Defense-in-depth on admin authorization.** Both filter-chain (`SecurityConfig:46`) AND method-level `@PreAuthorize` on both admin controllers. Tests verify the unauthenticated 401/403 path (`AdminDashboardControllerTest:66`, `AdminReportsControllerTest:139`).

2. **`groupBy` whitelist is enforced upstream of the native query.** `AdminReportsController:25,85-90` rejects anything outside `Set.of("day","week")` BEFORE `:bucket` is bound. Test `revenue_invalidGroupBy_returns400` (line 59) covers `groupBy=month`. Even if Postgres `DATE_TRUNC` accepts arbitrary strings, the whitelist makes the bind safe.

3. **Date-range cap and limit clamp implemented as documented.** 90-day cap at `AdminReportsController:26,78-82` throws `DATE_RANGE_TOO_LARGE`. Limit clamp `1..50` at line 54-56 throws `INVALID_LIMIT`. Both tested.

4. **`generate_series` only used for the 7-day fill, not arbitrary report ranges.** `ReportsRepository.revenueLast7Days` (line 69-85) uses `generate_series(CURRENT_DATE - 6, CURRENT_DATE, 1d)` — bounded to 7 rows. The user-supplied `revenueSeries` does NOT use `generate_series` and will simply omit days with no data. Matches the plan's risk-mitigation rationale.

5. **Idempotent rating prompt.** `OrderAssignedNotifier.onOrderDelivered:96-99` short-circuits with `existsByOrderId` BEFORE sending the keyboard. The DB-level `UNIQUE(order_id)` (V10:10) is the ultimate guard; the pre-check just avoids redundant prompts. Both paths tested (`OrderAssignedNotifierTest`).

6. **FSM state cleanup is exhaustive.** Every exit path in `RatingCommentHandler` (5 branches) calls `conv.clear(userId)`. Verified by reading every branch + matching test cases. No state leak possible after success, /skip, empty text, malformed UUID, or service error.

7. **CAST :to AS date deviation landed.** `ReportsRepository:107,135,167,185` uses `CAST(:to AS date) + INTERVAL '1 day'` — the executor's deviation #1 (avoiding `LocalDate + INTERVAL` parse issues across Postgres + Hibernate) is in the committed code. Mentioned in the JavaDoc at line 16-17.

8. **Cross-module call architecture is sound.** `bot → delivery` and `notification → delivery + bot` is one-directional. The `RatingService` is the single owner of rating-write + shipper-stats-recompute; bot handlers call it as a black box rather than reaching into the DB.

9. **JSONB round-trip is integration-tested.** `ConversationStateServiceIT.roundTrip_jsonbMapPayload` (line 51-70) verifies `Map<String,Object>` → `jsonb` → `Map<String,Object>` preserves String, Integer, and Boolean values. Removes a class of Hibernate-vs-Postgres-jsonb integration risk.

10. **`@Order` annotation behavior on `List<UpdateHandler>` is locked by `UpdateRouterOrderIT`.** Three test handlers with `@Order(0)`, `@Order(50)`, `@Order(100)` confirm Spring honors the ordering when injecting `List<UpdateHandler>`. This is the contract that `RatingCommentHandler @Order(0)` and `RatingCallbackHandler @Order(50)` rely on.

11. **Callback-data size assertion.** `RatingPromptBuilderTest:30-33` doesn't trust the comment — it asserts `getBytes().length ≤ 64` at runtime. Prevents future regressions where someone might swap UUID for orderCode.

12. **Native-SQL interface projections.** `ReportsRepository.RevenuePointRow`, `TopShipperRowRaw`, `CancellationTotals`, `ReasonCount` — clean Spring-Data idiom, no `@SqlResultSetMapping` ceremony, no anemic DTO duplication.

13. **`DELIVERED`-only filter in `revenueSeries`.** Line 108 filters `o.status = 'DELIVERED'` so cancelled/returned orders don't inflate revenue. Tested by `revenueSeries_excludesNonDeliveredOrders` (line 118).

14. **Rate-once race protection.** `RatingService.rate` uses `saveAndFlush` so the `UNIQUE` constraint violation surfaces SYNCHRONOUSLY (line 85) — not at commit. Translates to `ConflictException` immediately. Confirmed by `rate_alreadyRated_translatesIntegrityViolationToConflict` test.

15. **Vietnamese error messages on every domain exception.** `RatingService` (`Số sao phải từ 1 đến 5`, `Chỉ đánh giá được sau khi đơn đã giao`, `Đây không phải đơn của bạn`, `Đơn đã được đánh giá rồi`) and `AdminReportsController` (`Phạm vi tối đa 90 ngày`, `groupBy phải là 'day' hoặc 'week'`) all match the rest of the codebase's i18n convention.

16. **TopShippers query uses `delivery_assignment.delivered_at` for time-window filter, NOT `created_at` on orders.** This is the correct semantic — top-N is "shippers who delivered the most in window", not "shippers assigned to orders created in window". `ReportsRepository:133-135`.

17. **Polling matches design.** `DashboardPage` uses `refetchInterval: 30_000` on both queries (line 13, 28) — exactly the 30s cadence in the plan.

---

_Reviewed: 2026-06-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
