# P8 Plan Verification — Dashboard + Reports (Recharts) + Shipper Rating

**Plan:** `docs/superpowers/plans/2026-06-01-p8-dashboard-reports-rating.md` (4718 lines, 18 tasks, 4 waves)
**Research:** `docs/superpowers/research/2026-06-01-p8-dashboard-reports-rating-research.md` (1340 lines)
**Checked:** 2026-06-01
**Verdict:** **NEEDS REVISION** — the architecture is sound and the end-to-end goal chain is fully covered, but the plan introduces three compile-time / runtime BLOCKERS that will derail Wave 0 + Wave 2: (1) `delivery` module is missing test dependencies (Testcontainers + spring-security-test) that 5 of the new tests require; (2) `RatingController` uses a Spring Security principal pattern that does not exist at runtime in this codebase (P7 made the same mistake — same fix applies); (3) the `ReportsRepositoryIT` strategy with `ddl-auto=create-drop` will not materialize the cross-module entities the reports queries join (`telegram_user`, `user_role`, `status_history`, `delivery_assignment`). After patching those three, plan is ready for execution.

---

## Verdict at a glance

| Dimension                                                       | Status |
|-----------------------------------------------------------------|--------|
| 1.  End-to-end goal chain delivered                             | PASS |
| 2.  Module placement (no new modules, no cycle)                 | PASS |
| 3.  V10 schema correctness                                       | PASS |
| 4.  Atomic rating + shipper_profile update (single @Transactional) | PASS |
| 5.  FSM correctness (JSONB via Hibernate 6 native @JdbcTypeCode) | PASS |
| 6.  Bot rating idempotency + callback ≤64-byte                  | PASS |
| 7.  SQL injection prevention (groupBy whitelist)                | PASS |
| 8.  Date range cap + inclusive `to` boundary                    | PASS |
| 9.  `generate_series` usage (dashboard only)                    | PASS |
| 10. Cancellation reasons from `status_history.note`             | PASS |
| 11. `@PreAuthorize` on admin controllers                        | PASS |
| 12. Frontend types in `@shop/shared` (no duplication)           | PASS |
| 13. Recharts integration (ResponsiveContainer wrapper, 3.8.1)   | PASS (minor type drift) |
| 14. Tests planned for every layer                               | PASS |
| 15. Task atomicity (1 commit each)                              | PASS |
| 16. `@Order` on UpdateHandler + router IT                       | PARTIAL (see W2) |

**Counts:** 3 blockers · 5 warnings · 4 nits

---

## Goal-chain trace (the user journey)

### Customer rating loop

| Link | User-visible step | Planned task(s) | OK? |
|------|-------------------|-----------------|-----|
| 1 | Order goes DELIVERED → `OrderDeliveredEvent` fires (existing, P5) | n/a — pre-existing | YES |
| 2 | `OrderAssignedNotifier.onOrderDelivered` checks idempotency + sends inline keyboard | TASK 6 (lines 1799-1821) | YES |
| 3 | Telegram delivers `RATE:<uuid>:<n>` callback → `UpdateRouter` → `RatingCallbackHandler` | TASK 7 + TASK 9 (router-order IT) | YES |
| 4 | Handler calls `RatingService.rate` → INSERT rating + recompute `shipper_profile.rating_avg/count` in one tx | TASK 3 (lines 907-947) | YES |
| 5 | Keyboard removed via `EditMessageReplyMarkup` + callback answered "Cảm ơn bạn!" | TASK 7 (lines 2179-2186) | YES |
| 6 | FSM opens (`CUSTOMER_RATING_COMMENT`) + bot asks "Bạn có muốn nhập nhận xét?" | TASK 7 (lines 2185-2186) | YES |
| 7 | Customer types text or `/skip` → `RatingCommentHandler` (FSM-gated) | TASK 8 | YES |
| 8 | Service `updateComment` writes comment + clears FSM | TASK 3 + TASK 8 (lines 949-960, 2486-2489) | YES |
| 9 | "Bỏ qua" button → keyboard removed, no DB write | TASK 7 (lines 2130-2137) | YES |

### Admin dashboard / reports

| Link | User-visible step | Planned task(s) | OK? |
|------|-------------------|-----------------|-----|
| 10 | Admin opens `/` → KPIs + 7-day chart + top 3 shippers, polls every 30 s | TASK 10 dashboard endpoint + TASK 15 page | YES |
| 11 | Sidebar shows "Báo cáo" → navigates to `/reports` | TASK 17 | YES |
| 12 | Date-range picker + groupBy select | TASK 16 (lines 4292-4334) | YES |
| 13 | Revenue LineChart by day/week | TASK 10 + TASK 16 (RevenueChart) | YES |
| 14 | Top shippers BarChart | TASK 10 + TASK 16 (TopShippersChart) | YES |
| 15 | Cancellation PieChart with rate + reasons | TASK 10 + TASK 16 (CancellationChart) | YES |
| 16 | 90-day cap + groupBy whitelist enforced server-side | TASK 10 controller (lines 3162-3181) | YES |

Every user-observable link has a concrete task with concrete code. No silent gaps. **Goal will be achieved**, conditional on fixing the three blockers below.

---

## BLOCKERS (fix before execution)

### B1 — `delivery` module is missing test dependencies (Tasks 2, 4, 11, 12)

**File:** `backend/modules/delivery/pom.xml` — verified at the working tree (current `<dependencies>` block):

```xml
<dependency><groupId>com.shop.delivery</groupId><artifactId>shared</artifactId></dependency>
<dependency><groupId>com.shop.delivery</groupId><artifactId>auth</artifactId></dependency>
<dependency><groupId>com.shop.delivery</groupId><artifactId>order</artifactId></dependency>
<dependency>org.springframework.boot:spring-boot-starter</dependency>
<dependency>org.springframework.boot:spring-boot-starter-web</dependency>
<dependency>org.springframework.boot:spring-boot-starter-data-jpa</dependency>
<dependency>org.springframework.boot:spring-boot-starter-validation</dependency>
<dependency>org.springframework.boot:spring-boot-starter-websocket</dependency>
<dependency>org.springframework.boot:spring-boot-starter-test (test)</dependency>
```

That is the only test dep. The plan introduces:

- **TASK 2 (plan lines 326-365):** `DeliveryTestcontainerBase` using `org.testcontainers.containers.PostgreSQLContainer` + `@Testcontainers` (`org.testcontainers.junit.jupiter`). These classes are NOT on the delivery module's test classpath.
- **TASK 2 (plan lines 368-473):** `RatingRepositoryIT` extends that base and uses `@DataJpaTest` + Testcontainers — needs the JDBC driver too (`org.postgresql:postgresql` test-scope).
- **TASK 4 (plan line 1028):** `RatingControllerTest` imports `org.springframework.security.test.context.support.WithMockUser` → from `spring-security-test`. The `delivery` pom does NOT inherit that (it is test-scope on `auth`, and test-scope deps are NOT transitive in Maven).
- **TASK 11 (plan lines 3236, 3311):** `AdminDashboardControllerTest` + `AdminReportsControllerTest` — same `@WithMockUser` dependency.
- **TASK 12 (plan lines 3482-3633):** `ReportsRepositoryIT` extends `DeliveryTestcontainerBase` — same Testcontainers gap.

Empirical proof that no existing delivery-module test exercises these: `find backend/modules/delivery/src/test -name "*.java" -exec grep -l "@WebMvcTest\|@WithMockUser" {} \;` returns **nothing**. No existing test imports Testcontainers either.

**Fix (add to `backend/modules/delivery/pom.xml` under `<dependencies>` — before commit of TASK 2):**

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

This mirrors `backend/modules/bot/pom.xml` lines 64-79 (already wired correctly). The plan does not mention modifying `delivery/pom.xml`. Add a step "Step 0: Modify `backend/modules/delivery/pom.xml` to add four test-scope deps" to TASK 2 (before writing the IT), and `git add backend/modules/delivery/pom.xml` to the commit on line 614-619.

**Severity:** BLOCKER. First `./mvnw -pl modules/delivery -am test -Dtest=RatingRepositoryIT` (plan line 479) fails with "package org.testcontainers.containers does not exist".

---

### B2 — `RatingController` principal pattern does not match the codebase (TASK 4)

**File / line:** plan TASK 4, lines 1146-1185 (`RatingController.java`):

```java
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
...
@PreAuthorize("hasRole('CUSTOMER')")
public ResponseEntity<Rating> rate(
    @PathVariable("id") UUID orderId,
    @Valid @RequestBody RateOrderRequest body,
    @AuthenticationPrincipal UserDetails principal
) {
    Long customerId = Long.parseLong(principal.getUsername());
    ...
}
```

**Reality (verified):**

- Customer auth in this codebase is `TelegramAuthFilter` (`backend/modules/auth/src/main/java/com/shop/delivery/auth/api/TelegramAuthFilter.java`). It sets a **request attribute** `currentUser` (a `TelegramUser` entity), **not** a Spring Security `Authentication`. So `SecurityContextHolder.getContext().getAuthentication()` is null on customer requests, which makes:
  1. `@AuthenticationPrincipal UserDetails principal` resolve to `null` → NPE on `principal.getUsername()`, or 401 if the resolver is strict.
  2. `@PreAuthorize("hasRole('CUSTOMER')")` evaluate against an anonymous authentication → fail → 403.
- The existing customer-facing controllers use `@CurrentUser TelegramUser user` (from `com.shop.delivery.auth.api.CurrentUser`). See `backend/modules/order/src/main/java/com/shop/delivery/order/api/OrderController.java` line 48: `public OrderResponse create(@CurrentUser TelegramUser user, @Valid @RequestBody CreateOrderRequest req)`. That resolver reads the `currentUser` request attribute and throws 401 if missing.
- The note at plan line 1188 ("If the codebase uses a different principal type ... adjust accordingly") acknowledges the risk verbally but does NOT fix the code. The accompanying test (line 1028, 1050) uses `@WithMockUser(username = "1001", roles = "CUSTOMER")` — `@WithMockUser` injects a synthetic `Authentication` into the security context, so the test passes locally. But the real request path has no `Authentication`. **Test green, runtime broken.** This is exactly the P7 B1 pattern from the previous CHECK report.

**Fix (TASK 4, Step 3):**

```diff
-import org.springframework.security.access.prepost.PreAuthorize;
-import org.springframework.security.core.annotation.AuthenticationPrincipal;
-import org.springframework.security.core.userdetails.UserDetails;
+import com.shop.delivery.auth.api.CurrentUser;
+import com.shop.delivery.auth.entity.TelegramUser;
 ...
-    @PostMapping("/{id}/rating")
-    @PreAuthorize("hasRole('CUSTOMER')")
-    public ResponseEntity<Rating> rate(
-        @PathVariable("id") UUID orderId,
-        @Valid @RequestBody RateOrderRequest body,
-        @AuthenticationPrincipal UserDetails principal
-    ) {
-        Long customerId = Long.parseLong(principal.getUsername());
-        Rating r = ratingService.rate(orderId, customerId, body.stars(), body.comment());
-        return ResponseEntity.ok(r);
-    }
+    @PostMapping("/{id}/rating")
+    public ResponseEntity<Rating> rate(
+        @PathVariable("id") UUID orderId,
+        @Valid @RequestBody RateOrderRequest body,
+        @CurrentUser TelegramUser user
+    ) {
+        Rating r = ratingService.rate(orderId, user.getId(), body.stars(), body.comment());
+        return ResponseEntity.ok(r);
+    }
```

Then update `RatingControllerTest` (plan lines 1042-1116). With the new signature, `@WithMockUser` no longer suffices — the `@CurrentUser` resolver reads from a request attribute. Two options:
  a. Set the request attribute explicitly via `MockHttpServletRequestBuilder.requestAttr(...)` for each test:
     ```java
     mvc.perform(post(...).requestAttr("currentUser", testUser).content(...))
     ```
  b. Use the same pattern as `OrderControllerTest` (which already exists in `backend/modules/order/src/test` — confirm via `find backend/modules/order/src/test -name "OrderControllerTest.java"`); copy its principal injection strategy verbatim.

The 400/422/409 assertions in the test stay the same — only the principal-injection mechanism changes.

**Severity:** BLOCKER. Runtime would return 403 on every legitimate customer rating attempt via HTTP. (Note: the bot flow does NOT depend on this controller — `RatingCallbackHandler` calls `RatingService` directly, bypassing HTTP. So the phase goal "customer rates via bot" still works even with this bug. But the plan's own "Defer to future phase" section line 23 says "Mini App 'My Orders' rate-now button (canonical `POST /api/orders/:id/rating` already exists)" — that future hook requires this controller to actually function. And the test green/runtime broken split is a maintainability landmine.)

---

### B3 — `ReportsRepositoryIT` strategy will not generate the cross-module tables its queries depend on (TASK 12)

**File / line:** plan TASK 12, lines 3471-3637.

The IT uses `@DataJpaTest` + `@Import(DeliveryTestcontainerBase)`. `DeliveryTestcontainerBase` (plan lines 357-364) sets:

```java
r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
r.add("spring.flyway.enabled", () -> "false");
```

So Hibernate creates schema from JPA entities. But `@DataJpaTest` only scans entities in the test class's package by default — i.e. `com.shop.delivery.delivery.entity.*` (Rating, ShipperProfile, DeliveryAssignment, LocationPing). It does NOT scan `com.shop.delivery.order.entity.Order`, `auth.entity.TelegramUser`, `auth.entity.UserRole`, or `order.entity.StatusHistory`.

The plan's reports queries join those tables:

- `revenueLast7Days` / `revenueSeries` (plan lines 2831, 2862): `FROM orders o` — `orders` table not generated.
- `newCustomersToday` (line 2816): `FROM telegram_user u JOIN user_role r ON ...` — neither table generated.
- `topShippers` (line 2882): `FROM shipper_profile sp JOIN telegram_user u ON ... LEFT JOIN delivery_assignment a ... LEFT JOIN orders o ...` — `telegram_user`, `orders` not generated.
- `cancellationByReason` (line 2939): `FROM status_history sh JOIN orders o ON ...` — both tables not generated.

The footnote at plan line 3476 ("`ddl-auto=create-drop` to materialize the JPA entities, and rely on the existing entity-table mappings to produce compatible columns") admits the strategy. But `@DataJpaTest` does not auto-scan classes in other packages. The seedOrder helper at line 3605-3633 uses `em.persist(o)` for an `Order` entity — `Order.class` won't even be visible to the EM without explicit `@EntityScan(basePackages = "com.shop.delivery")` on a test config.

The existing `ProcessedUpdateServiceIT` in `bot` module (line 40-45) sidesteps this with a hand-built `@SpringBootConfiguration` + `@EntityScan(basePackageClasses = ProcessedUpdate.class)` + `@ComponentScan(...)`. The plan's footnote (line 3637) shrugs: "the full `topShippers` query and `cancellationByReason` can be smoke-verified in manual end-to-end testing" — but the first three tests (lines 3517-3577) all hit the `orders` table, which doesn't exist either.

**Fix (TASK 12 — pick ONE of two approaches):**

**Approach A — full Flyway test setup (recommended; matches production):**

Switch the IT base to use Flyway against Testcontainers (similar to how `OrderTestcontainerBase` in the `order` module does — verify pattern). Specifically:

```java
@DynamicPropertySource
static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    r.add("spring.flyway.enabled", () -> "true");
    r.add("spring.flyway.locations", () -> "classpath:db/migration");
}
```

Then add `org.flywaydb:flyway-core` + `flyway-database-postgresql` to `delivery/pom.xml` test-scope. The migrations live under `backend/app/src/main/resources/db/migration/` — they would need to be on the test classpath. The simplest path is a Maven `<testResource>` block in `delivery/pom.xml`:

```xml
<build>
    <testResources>
        <testResource>
            <directory>${project.basedir}/src/test/resources</directory>
        </testResource>
        <testResource>
            <directory>${project.basedir}/../../app/src/main/resources/db/migration</directory>
            <targetPath>db/migration</targetPath>
        </testResource>
    </testResources>
</build>
```

This way V1..V10 all run in the test container.

**Approach B — explicit `@EntityScan` covering all entities (lightweight; matches `ProcessedUpdateServiceIT`):**

Replace `@DataJpaTest @Import(DeliveryTestcontainerBase)` with:

```java
@SpringBootTest(classes = ReportsRepositoryIT.TestConfig.class)
@Testcontainers
class ReportsRepositoryIT {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.shop.delivery")
    @EnableJpaRepositories(basePackages = "com.shop.delivery")
    static class TestConfig {}
    ...
}
```

This way Hibernate generates ALL tables. The `seedOrder` helper at line 3625 (`em.persist(o)`) then works because `Order` is a known entity.

Note: `ConversationStateServiceIT` (plan lines 1297-1389) has the same flaw to a lesser extent — it does `@DataJpaTest @Import(ConversationStateService.class)`, and `conversation_state.telegram_user_id` is a `BIGINT PRIMARY KEY REFERENCES telegram_user(id) ON DELETE CASCADE` per V3. With `ddl-auto=create-drop` and Flyway disabled, the FK constraint is omitted (Hibernate would emit it but `telegram_user` doesn't exist → DDL fails). The test could still work if Hibernate is configured to ignore FK errors, or if the entity has no `@ManyToOne` (it doesn't — just a Long `telegramUserId` PK). So this one accidentally works. But `ReportsRepositoryIT` will not.

**Severity:** BLOCKER. TASK 12 Step 2 (`./mvnw -pl modules/delivery -am verify -Dtest=ReportsRepositoryIT`, plan line 3642) fails with "ERROR: relation 'orders' does not exist" or "EntityManagerFactory ... Unknown entity: com.shop.delivery.order.entity.Order" before any assertion runs.

---

## WARNINGS (should fix; plan still produces working code but it'll be ugly)

### W1 — `recharts` Tooltip `formatter` return-type drift

**File / line:** plan TASK 15, lines 3955-3962; TASK 16, lines 4152-4159.

```tsx
formatter={(value: number, name: string) =>
  name === 'revenue'
    ? [formatVnd(value), 'Doanh thu']
    : [value, 'Số đơn']
}
```

The first branch returns `[string, string]`. The second returns `[number, string]`. Recharts 3.x's `Formatter<ValueType, NameType>` type expects a uniform return shape `[string, string]` (or `null` to suppress). TypeScript strict mode will flag the second branch.

**Fix:** stringify in the second branch:
```tsx
: [String(value), 'Số đơn']
```

Apply to `RevenueMiniChart.tsx`, `RevenueChart.tsx`, and `TopShippersChart.tsx` (the latter's `formatter={(v: number) => [\`${v} đơn\`, 'Số đơn đã giao']}` is already a template literal, OK).

**Severity:** WARNING — `pnpm -r run type-check` (plan line 3901) may fail with `Type 'number' is not assignable to type 'string'`. If `tsconfig` is loose, it passes; otherwise fix.

---

### W2 — `UpdateRouterOrderIT` will not boot: `@SpringBootTest(classes = TestConfig.class)` without `@SpringBootConfiguration` (TASK 9)

**File / line:** plan TASK 9, lines 2555-2597.

```java
@SpringBootTest(classes = {
    UpdateRouterOrderIT.TestConfig.class
})
class UpdateRouterOrderIT {
    ...
    @Configuration
    static class TestConfig {
        @Bean @Order(0)   UpdateHandler firstHandler()  { return new FirstHandler();  }
        ...
    }
}
```

`@SpringBootTest` requires its `classes` argument to be a `@SpringBootConfiguration` (or to have `@SpringBootApplication` itself). A bare `@Configuration` class is not enough — Spring Boot will fail to start with "Unable to find a @SpringBootConfiguration, you need to use @ContextConfiguration or @SpringBootTest(classes=...) with your test".

Compare against `ProcessedUpdateServiceIT` (existing, working): line 40-45 uses `@SpringBootConfiguration @EnableAutoConfiguration @ComponentScan(...)` on its inner config.

**Fix:**

```diff
-    @Configuration
+    @org.springframework.boot.SpringBootConfiguration
+    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
     static class TestConfig {
         @Bean @Order(0)   UpdateHandler firstHandler()  { return new FirstHandler();  }
         ...
     }
```

Or — per the plan's own fallback at line 2600 — drop `@SpringBootTest` entirely and use the plain unit-test approach:

```java
@Test
void springSortsByOrderAnnotation() {
    List<UpdateHandler> handlers = new ArrayList<>(List.of(new LastHandler(), new FirstHandler(), new MiddleHandler()));
    AnnotationAwareOrderComparator.sort(handlers);
    assertThat(handlers).extracting(h -> h.getClass().getSimpleName())
        .containsExactly("FirstHandler", "MiddleHandler", "LastHandler");
}
```

The plan acknowledges this fallback but does not pick it. The `@SpringBootTest` route will fail without explicit `@SpringBootConfiguration`.

**Severity:** WARNING. TASK 9 Step 2 fails on first run. Cheap to fix.

---

### W3 — Existing handlers (StartHandler, HelpHandler, OrderOfferCallbackHandler, LiveLocationHandler) have NO `@Order` annotation

**File / line:** plan §"Decisions chốt" line 108 claims: "implicit `@Order(LOWEST)` on commands".

Reality (verified via `grep -rn '@Order' backend/modules/bot/src/main/java/`): zero existing handlers carry `@Order`. Spring's behaviour for a bean injected as `List<T>` without `@Order` is `LOWEST_PRECEDENCE` (= `Integer.MAX_VALUE`), so beans without `@Order` *do* sort after beans with explicit `@Order(0)` / `@Order(50)`. So functionally the plan still works:

| Order | Bean |
|-------|------|
| 0     | `RatingCommentHandler` (FSM-bound) |
| 50    | `RatingCallbackHandler` |
| MAX   | `OrderOfferCallbackHandler` (no annotation) |
| MAX   | `LiveLocationHandler` (no annotation) |
| MAX   | `StartHandler` (no annotation) |
| MAX   | `HelpHandler` (no annotation) |

The order of beans with the same precedence is unspecified — but among the rating handlers, `RatingCommentHandler` always wins. `RatingCallbackHandler` always runs before any default-precedence handler. The phrase "implicit `@Order(LOWEST)`" in the plan is technically correct, but the plan doc would be clearer if it added a sentence: "existing handlers do not carry `@Order`; they sort to LOWEST_PRECEDENCE by default."

**Severity:** WARNING — documentation imprecision, not a code bug.

---

### W4 — `RatingCommentHandler.canHandle` calls `conv.get(userId)` on every text update — performance trap

**File / line:** plan TASK 8, lines 2444-2452.

```java
@Override
public boolean canHandle(Update u) {
    if (u == null || u.getMessage() == null || u.getMessage().getText() == null) return false;
    if (u.getMessage().getFrom() == null) return false;
    Long userId = u.getMessage().getFrom().getId();
    return conv.get(userId)
        .map(s -> STATE.equals(s.getState()))
        .orElse(false);
}
```

`conv.get` performs a `SELECT ... FROM conversation_state WHERE telegram_user_id = ?`. Every text update routed through `UpdateRouter` calls every `canHandle`, including this one. For a single text message:
1. `RatingCommentHandler.canHandle` → 1 DB hit.
2. If returns false, next handler runs `canHandle` (free, no DB).
3. If returns true, `handle()` calls `conv.get(userId)` AGAIN (line 2473) — another DB hit.

That's 1-2 DB roundtrips per text message even for users not in any FSM state. At thesis scale (handful of users) it's irrelevant. In production, every `/help`, `/start`, free text, etc. would trigger this.

Caching options: pre-fetch the state in the router and pass via a `Map` attribute on `Update`; or use a short Caffeine cache keyed by `userId`; or add a `null`-state check first (`SELECT 1 FROM conversation_state WHERE telegram_user_id = ? LIMIT 1` is the same cost as the full row, so caching is the better fix).

Out of scope for thesis. **Flag and move on.**

**Severity:** WARNING — performance concern only, not a correctness bug.

---

### W5 — Plan's "expected commit count delta" math is off-by-one

**File / line:** plan lines 4691-4714.

The "Commit Summary" lists 18 tasks but only 16 produce commits (TASK 13 and TASK 18 are explicitly "no commit — smoke only"). Line 4714 says "Expected end-of-P8 commit count delta: **~16 commits** on top of the P7 baseline (~138 → ~154)" — which is correct math (138 + 16 = 154). But Task 9's commit message ("test(bot): lock @Order behavior") only adds the IT, no production code — that's still a commit. So 16 is right.

But cross-check with W2: if `UpdateRouterOrderIT` is refactored to a unit test (drop `@SpringBootTest`), TASK 9 still commits. Good.

**Severity:** WARNING — no real impact; just confirming the math.

---

## NITS (informational, no action required)

### N1 — `Rating` entity uses `@PrePersist` to set `createdAt` but the DB also defaults it

**File / line:** plan TASK 2, lines 535-537:
```java
@PrePersist
void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
}
```

V10 SQL (plan line 256): `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`. Both paths produce the same value; the Java pre-persist is harmless redundancy. No fix needed.

### N2 — `RatingControllerTest.post_alreadyRated_returns409` mocks throw, but the controller doesn't have a try/catch — relies on GlobalExceptionHandler

**File / line:** plan TASK 4, lines 1104-1115. `ConflictException` propagates out of `service.rate` → thrown out of `controller.rate` → caught by `@RestControllerAdvice GlobalExceptionHandler.handleConflict` → 409. Verified `GlobalExceptionHandler` maps `ConflictException` → `HttpStatus.CONFLICT` (line 51-54 in `shared/api/GlobalExceptionHandler.java`). OK.

### N3 — `ReportsRepository extends JpaRepository<Order, UUID>` is a leaky abstraction

**File / line:** plan TASK 10 line 2784: `public interface ReportsRepository extends JpaRepository<Order, java.util.UUID>`.

Strictly, this gives `ReportsRepository` an inherited `save(Order)`, `findById(UUID) → Optional<Order>`, etc. — methods that have nothing to do with the report queries. Spring Data needs SOME entity type for the repo. The convention in the codebase (P7 `PaymentRepository`) does the same. Accept as idiomatic. Cosmetic only.

### N4 — `RatingCallbackHandlerTest.handle_validRate_persistsAndOpensFsm` uses captor type `ArgumentCaptor<Map<String, Object>>` which generates an unchecked-cast warning

**File / line:** plan TASK 7 line 1973. `ArgumentCaptor.forClass(Map.class)` returns raw type. Add `@SuppressWarnings("unchecked")` on the test method. Cosmetic only.

---

## Cross-cutting checks (the 16 audit items requested)

### 1. End-to-end goal chain
Traced above — **PASS**.

### 2. Module placement consistency
- Plan creates files in `delivery`, `bot`, `notification` modules only — no new Maven module. **PASS.**
- No `payment` dep on `delivery` (verified `delivery/pom.xml` has only `shared`, `auth`, `order`). **PASS.**
- No `order` importing `delivery` (verified `order` entities/services/api don't reference any `delivery.*` package). **PASS.**

### 3. V10 schema correctness
- FK to `orders(id)` ✓ (plan line 251 `REFERENCES orders(id) ON DELETE CASCADE`).
- FK to `telegram_user(id)` for both customer + shipper ✓ (plan lines 252-253).
- UNIQUE on `order_id` ✓ (plan line 251).
- CHECK `stars BETWEEN 1 AND 5` ✓ (plan line 254).
- Index on `(shipper_id, created_at DESC)` ✓ (plan line 260).
- Plan does NOT modify `shipper_profile` (V6 already has `rating_avg`, `rating_count` per `backend/app/src/main/resources/db/migration/V6__delivery.sql` lines 9-10). ✓ **PASS.**

### 4. Atomicity of rating + shipper_profile update
- `RatingService.rate` is `@Transactional` ✓ (plan line 907).
- INSERT (`ratingRepo.saveAndFlush`) + recompute (`SELECT AVG, COUNT FROM rating WHERE shipper_id = ?`) + `shipperRepo.save` all in one tx ✓ (plan lines 937-944).
- Aggregate query at plan line 591-595 uses `SELECT COALESCE(AVG(stars), 0), COUNT(*) FROM rating WHERE shipper_id = :shipperId` — recompute strategy, not incremental ✓.
- `BigDecimal.setScale(2, HALF_UP)` applied to avg (plan line 967) ✓. **PASS.**

### 5. FSM correctness
- `ConversationState` uses `@JdbcTypeCode(SqlTypes.JSON)` (plan line 1432-1434) — native Hibernate 6, no `hypersistence-utils`. ✓
- State enum `CUSTOMER_RATING_COMMENT` defined as String constant in both handlers (plan lines 2100, 2430). ✓
- `RatingCommentHandler` clears FSM on: (a) `/skip` (line 2462), (b) empty text (line 2469), (c) success (line 2488), (d) `IllegalArgumentException` from bad UUID in payload (line 2492), (e) `DomainException` (line 2496). ✓

`ConversationStateService.put`/`get`/`clear` API minimal and reusable. ✓ **PASS.**

### 6. Bot rating idempotency
- `onOrderDelivered` checks `ratingRepo.existsByOrderId(e.orderId())` BEFORE sending the keyboard (plan line 1808-1811). ✓
- `RatingCallbackHandler` catches `ConflictException("ALREADY_RATED")` → answers callback with `showAlert(true)` + removes keyboard + does NOT open FSM (plan lines 2160-2167). ✓
- Callback data ≤ 64 bytes: verified empirically — `RATE:<uuid>:<digit>` = 43 bytes; `RATE_SKIP:<uuid>` = 46 bytes. Test enforces (plan line 1602-1604). ✓
- "Bỏ qua" keyboard button on second row (plan lines 1657-1660), callback data `RATE_SKIP:<orderId>`. ✓ **PASS.**

### 7. SQL injection prevention (`groupBy` whitelist)
- `AdminReportsController.requireBucket` checks `ALLOWED_BUCKETS.contains(bucket)` BEFORE the value reaches `repo.revenueSeries` (plan lines 3176-3181). ✓
- Inside the repo, `DATE_TRUNC(:bucket, ...)` is parameter-bound, so even if validation were bypassed, Postgres prepared-statement binding would reject most injection (though `DATE_TRUNC` requires a literal — `:bucket` as a bound parameter actually works in Postgres because the prepared statement substitutes the literal as a string). Whitelist is the real defence and is present. ✓ **PASS.**

### 8. Date range bounds
- 90-day cap enforced (`MAX_RANGE_DAYS = 90`, plan line 3117). ✓
- `to < from` → `ValidationException("INVALID_RANGE", ...)` → 400 (plan lines 3166-3168, test at plan line 3372-3380). ✓
- Inclusive `to`: every query uses `o.created_at < :to + INTERVAL '1 day'` (plan lines 2868, 2899, 2929, 2946). ✓ NOT `BETWEEN`. **PASS.**

### 9. `generate_series` usage
- Used in `revenueLast7Days` ONLY (plan lines 2836-2840). ✓
- Other range queries (`revenueSeries`, `topShippers`, `cancellation*`) do NOT use `generate_series` — sparse rows acceptable per Decision 12 (plan line 117). ✓ **PASS.**

### 10. Cancellation reasons from `status_history.note`
- `cancellationByReason` query (plan lines 2939-2950): `FROM status_history sh JOIN orders o ON o.id = sh.order_id WHERE sh.to_status = 'CANCELLED'`. ✓
- `COALESCE(NULLIF(TRIM(sh.note), ''), 'Không ghi lý do')` handles null/empty notes gracefully. ✓
- Verified `status_history` has `to_status VARCHAR(16)` + `note TEXT` columns (V4__order.sql line 63, 66). ✓ **PASS.**

### 11. Security on admin endpoints
- Class-level `@PreAuthorize("hasRole('SHOP_OWNER')")` on `AdminDashboardController` (plan line 3071) and `AdminReportsController` (plan line 3113). ✓
- `SecurityConfig` (verified) routes `/api/admin/**` → `.hasRole("SHOP_OWNER")` at the filter chain level (`backend/modules/auth/src/main/java/com/shop/delivery/auth/config/SecurityConfig.java` line 46). Defence-in-depth. ✓
- Note that "SHOP_OWNER" is the actual role name in this codebase — plan's brief says "ADMIN role" but the plan correctly uses `SHOP_OWNER` throughout (line 78). ✓ **PASS.**

### 12. Frontend types in `@shop/shared`
- `frontend/shared/src/types/reports.ts` declares `DashboardSummary`, `RevenuePoint`, `TopShipperRow`, `CancellationReport`, `ReasonCount`, `OrderStatusKey`, `GroupBy` (plan lines 3781-3833). ✓
- `frontend/shared/src/types/index.ts` re-exports (plan line 3839). ✓
- `frontend/shared/src/api/reports.ts` declares `reportsApi(client)` factory (plan lines 3861-3887). ✓
- Webadmin imports them: `import type { DashboardSummary, RevenuePoint } from '@shop/shared'` (plan lines 3981, 4137). Not redefined locally. ✓ **PASS.**

### 13. Recharts integration
- Every chart wrapped in `<div className="h-72 w-full">` then `<ResponsiveContainer>` (plan lines 3950-3951, 4146-4147, 4188-4189, 4219-4220). ✓
- `recharts@^3.8.1` installed (plan line 3772). Research § 6.1 verified compat with React 18.3.1. ✓
- Existing `frontend/webadmin/package.json` confirms React 18.3.1. **PASS** (modulo W1 above on Tooltip formatter type).

### 14. Tests planned
Cross-check against the 7 categories the prompt requested:

| Category | Planned tests | Plan line |
|----------|---------------|-----------|
| `RatingServiceTest`: happy + non-DELIVERED + wrong customer + already rated + math | 9 cases | 714-829 |
| `RatingRepositoryIT`: UNIQUE + CHECK | 6 cases | 401-462 |
| `ConversationStateServiceTest` + IT JSONB | 4 unit + 4 IT | 1252-1387 |
| `RatingCallbackHandlerTest`: 409 + skip | 9 cases | 1937-2028 |
| `RatingCommentHandlerTest`: FSM + /skip + empty guard | 9 cases | 2289-2361 |
| `AdminDashboard/ReportsControllerTest`: whitelist + 90d cap | 2 + 8 cases | 3255-3443 |
| `ReportsRepositoryIT`: day + week + empty range | 6 cases | 3517-3601 |

All seven covered. ✓ Total: 9+6+8+9+9+10+6 + 4 (UpdateRouterOrderIT, 1 case actually) + 2 (RatingPromptBuilder + OrderAssignedNotifierTest new cases) + 4 (RatingControllerTest) = ~67 new tests. **PASS.**

### 15. Task atomicity
Per-task file count + role check:

| Task | Files | Role | Atomic? |
|------|-------|------|---------|
| 1   | 1   | migration | ✓ |
| 2   | 4   | entity + repo + IT base + IT | ✓ |
| 3   | 2   | service + test | ✓ |
| 4   | 3   | controller + DTO + test | ✓ |
| 5   | 5   | entity + repo + service + 2 tests | borderline |
| 6   | 4   | builder + notifier edit + 2 tests | ✓ |
| 7   | 2   | handler + test | ✓ |
| 8   | 2   | handler + test | ✓ |
| 9   | 1   | router IT | ✓ |
| 10  | 8   | repo + service + 4 DTOs + 2 controllers | **oversized** — see N5 below |
| 11  | 2   | 2 controller tests | ✓ |
| 12  | 1   | repo IT | ✓ |
| 13  | 0   | smoke only | ✓ |
| 14  | 5   | recharts dep + types + API + 2 barrels | ✓ |
| 15  | 2   | DashboardPage + mini chart | ✓ |
| 16  | 4   | ReportsPage + 3 chart components | ✓ |
| 17  | 2   | App.tsx + Sidebar | ✓ |
| 18  | 0   | smoke only | ✓ |

TASK 10 is the only oversized one. By the gates rubric, 8 files = "warning". All 8 are tightly coupled (DTOs reference repo projection types, controllers wire to the service). Splitting would force a second commit for "controllers only" with no logical separation. Acceptable. Flagging as N5 below.

### 16. `@Order` on UpdateHandler + router IT
- `RatingCommentHandler @Order(0)` ✓ (plan line 2426).
- `RatingCallbackHandler @Order(50)` ✓ (plan line 2096).
- Router IT in TASK 9 (plan lines 2536-2597) — **but** see W2: the IT won't boot as written. Functionally the assertion is correct; mechanically it needs `@SpringBootConfiguration` on the inner config OR the unit-test fallback.

**Partial PASS** — the intent is captured, the implementation needs a one-line annotation fix.

---

### N5 — TASK 10 has 8 files

See "Task atomicity" table above. Borderline-large but cohesive. Plan's atomic-commit principle is preserved. Not a problem.

---

## Recommendation

**Status: NEEDS REVISION** — apply the three blocker fixes (B1, B2, B3) before execution. They are mechanical:

1. **B1** (TASK 2 Step 0): Add four test-scope deps to `backend/modules/delivery/pom.xml` — `spring-security-test`, `testcontainers:junit-jupiter`, `testcontainers:postgresql`, `postgresql`. One pom edit, ~12 lines.
2. **B2** (TASK 4 Step 3): Switch `RatingController` from `@AuthenticationPrincipal UserDetails` to `@CurrentUser TelegramUser`. Replace `Long.parseLong(principal.getUsername())` with `user.getId()`. Drop `@PreAuthorize("hasRole('CUSTOMER')")`. Update test to use the `@CurrentUser` resolver pattern (mirror `OrderControllerTest`).
3. **B3** (TASK 12 / TASK 2): Pick one of two strategies for `ReportsRepositoryIT`:
   - **Approach A** (preferred): switch `DeliveryTestcontainerBase` to enable Flyway against Testcontainers, add migration `<testResource>` block to `delivery/pom.xml` pointing at `backend/app/src/main/resources/db/migration/`. Then existing schema applies automatically.
   - **Approach B**: replace `@DataJpaTest` with `@SpringBootTest(classes = TestConfig.class)` plus an explicit `@SpringBootConfiguration @EnableAutoConfiguration @EntityScan(basePackages = "com.shop.delivery") @EnableJpaRepositories(basePackages = "com.shop.delivery")` inner config, mirroring `ProcessedUpdateServiceIT`.

After those three, the warnings (W1-W5) and nits (N1-N5) can be addressed during execution as code-review comments. None are gating.

**The plan WILL achieve the phase goal** — every link in the user journey has a concrete task, all 11 cross-cutting checks (schema, atomicity, FSM, idempotency, injection guard, date bounds, generate_series usage, cancellation reasons, security, frontend types, recharts) pass, and the architecture (no new modules, no cross-module cycle, all rating/reports live in `delivery`, FSM in `bot`) is correct. Once the three blockers are patched, P8 is ready for execution.
