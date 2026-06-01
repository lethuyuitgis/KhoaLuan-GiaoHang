# P9 Plan Verification — Polish + Deploy (Containerization, Demo Seed, Admin WS, Docs)

**Plan:** `docs/superpowers/plans/2026-06-01-p9-polish-deploy.md` (4252 lines, 15 tasks, 3 waves)
**Checked:** 2026-06-01
**Verdict:** **NEEDS REVISION** — the deployment skeleton (Dockerfiles + compose + nginx), the env-var contract, the admin WS auth fork, the broadcaster, the IT, and the docs all hang together cleanly and will achieve the phase goal end-to-end. However, **TASK 7 (V11 demo seed) contains 3 schema-level BLOCKERS** that will cause the Flyway migration to fail on first boot (which kills the entire `docker compose up` smoke test that is the phase's headline deliverable). **TASK 14 (RateOrderResponse DTO) contains one type-compile BLOCKER**. **TASK 8 (admin WS subscribe allowlist) has one design WARNING that touches the P6 hardening contract**. After patching those, the plan is ready for execution.

The deployment plumbing itself (Tasks 1-6, 11-13) is solid — the most-load-bearing risk (multi-stage Dockerfiles vs. multi-module Maven + pnpm workspace) was traced carefully and the Dockerfiles will produce working images.

---

## Verdict at a glance

| Dimension                                              | Status |
|--------------------------------------------------------|--------|
| 1. `docker compose up` smoke chain (postgres → backend → nginx) | PASS |
| 2. Backend Dockerfile vs. multi-module Maven layout    | PASS |
| 3. Frontend Dockerfiles vs. pnpm workspace             | PASS |
| 4. `application-prod.yml` env-var contract             | PASS |
| 5. `.env.example` completeness                         | PASS (one nit) |
| 6. V11 demo seed schema match                          | **FAIL** (3 blockers) |
| 7. Admin WS dual-auth CONNECT path                     | PASS (one warning) |
| 8. `OrderCreatedEvent` + broadcaster                   | PASS |
| 9. `WebSocketAdminAuthIT`                              | PASS (1 warning) |
| 10. README + RUNBOOK                                   | PASS |
| 11. Lazy-load `/reports`                               | PASS |
| 12. `RateOrderResponse` DTO                            | **FAIL** (1 blocker) |
| 13. Task atomicity (1 commit each)                     | PASS |
| 14. Acceptance criteria concrete                       | PASS |

**Counts:** 4 BLOCKERS · 6 WARNINGS · 5 NITS

---

## Goal-chain trace (the reviewer journey)

| Link | Reviewer step | Planned task(s) | OK? |
|------|---------------|-----------------|-----|
| 1 | `git clone` + `cp .env.example .env` + fill 4 secrets | Task 4 (line 1045-1170) | YES |
| 2 | `docker compose -f infra/docker-compose.yml up -d` | Task 5 (line 1173-1440) | YES |
| 3 | Postgres healthy first | Task 5 healthcheck (line 1212-1218) | YES |
| 4 | Backend boots, Flyway V1..V11 applies | Tasks 1+3+7 | **NO — V11 fails (B1, B2, B3)** |
| 5 | Miniapp + webadmin healthy after backend | Tasks 2+5 | YES |
| 6 | Nginx healthy, port 80 exposed | Task 6 | YES |
| 7 | Reviewer opens `http://localhost/` → 302 `/admin/` | Task 6 (line 1512-1514) | YES |
| 8 | Login `shop@example.com / admin123` | Task 7 admin row | YES (assuming V11 fixed) |
| 9 | Dashboard shows 30+ orders, charts, top shippers | Task 7 seed | YES (assuming V11 fixed) |
| 10 | New order from Mini App pushes to admin via WS within 2s | Tasks 8+9 | YES (one warning on SUBSCRIBE allowlist) |
| 11 | `/reports` lazy-loads recharts chunk | Task 13 | YES |
| 12 | Customer rates a DELIVERED order; response is the slim DTO | Task 14 | **NO — DTO won't compile (B4)** |
| 13 | RUNBOOK + README guide P1-P8 demo | Tasks 11-12 | YES |

Every link has a concrete task. The 4 blockers below are mechanical and don't require restructuring waves.

---

## BLOCKERS (fix before execution)

### B1 — V11 inserts invalid enum values into `delivery_assignment.status`
**File / line:** plan TASK 7, lines **2017-2034**.

```sql
INSERT INTO delivery_assignment (id, order_id, shipper_id, status, ...)
SELECT
    ...
    CASE o.status
        WHEN 'ASSIGNED'   THEN 'ASSIGNED'
        WHEN 'DELIVERING' THEN 'STARTED'
        WHEN 'DELIVERED'  THEN 'DELIVERED'
    END,
    ...
```

**Reality:**
- `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/domain/AssignmentStatus.java`:
  ```java
  public enum AssignmentStatus { OFFERED, ACCEPTED, REJECTED, STARTED, COMPLETED, CANCELLED }
  ```
- `DeliveryAssignment.status` is `@Enumerated(EnumType.STRING)` (entity line 28-30).
- The seed inserts `'ASSIGNED'` (for 3 rows, n=10..12) and `'DELIVERED'` (for 12 rows, n=16..27). Neither is a valid `AssignmentStatus` value.

What happens:
- The INSERT itself succeeds (column is `VARCHAR(16)`, no DB-level enum constraint).
- The Web Admin's first query that hydrates a `DeliveryAssignment` (e.g., `/api/admin/orders` joining assignments) explodes with `IllegalArgumentException: No enum constant ... AssignmentStatus.ASSIGNED` from Hibernate's enum converter.
- **Net effect: dashboard / orders list crashes when it tries to render any seeded ASSIGNED or DELIVERED order. The demo data poisons the demo.**

**Fix:** map order status → assignment status correctly:

```sql
CASE o.status
    WHEN 'ASSIGNED'   THEN 'ACCEPTED'    -- shipper accepted; not yet started
    WHEN 'DELIVERING' THEN 'STARTED'     -- already correct
    WHEN 'DELIVERED'  THEN 'COMPLETED'   -- assignment is done
END,
```

And update the seed header counts comment (line 2141) from `'ASSIGNED'`/`'DELIVERED'` to `'ACCEPTED'`/`'COMPLETED'`.

**Severity:** BLOCKER. Reviewer-visible crash on first page load of the admin orders list.

---

### B2 — V11 violates `uq_assignment_shipper_started` UNIQUE INDEX
**File / line:** plan TASK 7, lines **2017-2034** combined with V8 migration.

The V8 migration (already shipped, `backend/app/src/main/resources/db/migration/V8__assignment_unique_started.sql`):
```sql
CREATE UNIQUE INDEX IF NOT EXISTS uq_assignment_shipper_started
    ON delivery_assignment(shipper_id)
    WHERE status = 'STARTED';
```

The plan assigns DELIVERING orders n=13..15 via:
```sql
CASE WHEN (right(o.code, 4)::int) % 2 = 0 THEN 9000000101 ELSE 9000000102 END
```

- n=13 → 13 % 2 = 1 → shipper **9000000102**
- n=14 → 14 % 2 = 0 → shipper 9000000101
- n=15 → 15 % 2 = 1 → shipper **9000000102** ← same as n=13

Both n=13 and n=15 have `status='STARTED'` (DELIVERING) for the same shipper → V11 INSERT fails with `duplicate key value violates unique constraint "uq_assignment_shipper_started"`. Flyway aborts; backend container stays unhealthy; whole `docker compose up` fails.

**Fix:** ensure at most one STARTED row per shipper. Two options:

**Option A — change the shipper-pick formula for DELIVERING rows only.** Use a row counter that maps each DELIVERING order to a distinct shipper, or assign all DELIVERING to a single shipper that has no other STARTED in the seed:

```sql
-- For the 3 DELIVERING rows (n=13..15), spread across 3 different shippers OR
-- use only 1 STARTED + 2 OFFERED so the unique index isn't violated.
-- Simplest fix: keep only 1 DELIVERING order in the seed (n=13), the rest go DELIVERED.
```

**Option B — relax the seed: change 2 of the 3 DELIVERING into DELIVERED.** Drop the count to "1 DELIVERING + 14 DELIVERED" and rebalance the counts at the bottom comment block (line 2138-2144).

**Severity:** BLOCKER. Migration fails → no boot.

---

### B3 — V11 references column `actor_user_id` which does not exist on `status_history`
**File / line:** plan TASK 7, lines **2098-2105**.

```sql
INSERT INTO status_history (order_id, from_status, to_status, actor_user_id, note, changed_at)
SELECT
    o.id, NULL, 'PENDING', o.customer_id, 'Đơn được tạo (demo)', o.created_at
FROM orders o ...
```

**Reality:**
- `V4__order.sql` line 60: `changed_by_user_id  BIGINT,` (not `actor_user_id`).
- `StatusHistory.java` line 36: `@Column(name = "changed_by_user_id")` confirms the entity-DB binding.

Flyway will fail with `ERROR: column "actor_user_id" of relation "status_history" does not exist`.

**Fix:** rename the column in the INSERT:

```sql
INSERT INTO status_history (order_id, from_status, to_status, changed_by_user_id, note, changed_at)
SELECT
    o.id, NULL, 'PENDING', o.customer_id, 'Đơn được tạo (demo)', o.created_at
FROM orders o ...
```

The accompanying anti-join `WHERE NOT EXISTS (...)` (line 2108-2111) is correct as-is — the duplicate-detection key is `(order_id, to_status='PENDING')`, not the actor column.

**Severity:** BLOCKER. Migration fails on V11 line ~2098.

---

### B4 — `RateOrderResponse.from(Rating)` won't compile — type mismatch
**File / line:** plan TASK 14, lines **3834-3852**.

The DTO is declared as:
```java
public record RateOrderResponse(
    Long ratingId,
    UUID orderId,
    int stars,
    String comment,
    OffsetDateTime createdAt   // ← OffsetDateTime
) { ... }
```

with factory:
```java
public static RateOrderResponse from(Rating rating) {
    return new RateOrderResponse(
        rating.getId(),
        rating.getOrderId(),
        rating.getStars(),       // returns Short
        rating.getComment(),
        rating.getCreatedAt()    // returns Instant — see Rating.java line 68
    );
}
```

**Reality:**
- `Rating.getCreatedAt()` returns `java.time.Instant` (verified, entity line 68).
- `java.time.Instant` is NOT assignable to `java.time.OffsetDateTime`. **Compile error.**
- `Rating.getStars()` returns `Short`. Auto-unbox to `short` then widen to `int` works implicitly in record-constructor calls — that part is fine.

**Fix (two clean options):**

**Option A — keep the DTO field as `Instant` (recommended, matches the entity):**
```java
public record RateOrderResponse(
    Long ratingId,
    UUID orderId,
    int stars,
    String comment,
    Instant createdAt    // changed from OffsetDateTime
) { ... }
```
and update the import.

**Option B — convert at the boundary:**
```java
rating.getCreatedAt().atOffset(ZoneOffset.UTC)
```
This is OK but introduces a timezone choice that wasn't motivated.

**Severity:** BLOCKER. `mvn compile` fails on TASK 14.

---

## WARNINGS (should fix; execution may work but quality degrades)

### W1 — SUBSCRIBE allowlist for admin overrides P6 hardening contract — but the test doesn't cover what P6 broke
**File / line:** plan TASK 8, lines **2469-2480** and TASK 10 lines **3071-3091**.

P6 hardened the `SUBSCRIBE` interceptor (existing `WebSocketConfig` line 78-84) to reject every destination except `/user/**` and `/queue/**` — *including* `/topic/**`. This was an intentional defence-in-depth choice for the Mini App audience.

The P9 plan introduces `/topic/admin/**` as an allowed prefix for the new `AdminPrincipalWrapper`, which is correct (the broadcaster pushes to `/topic/admin/orders`). The `isSubscribeAllowed` branch (line 2469-2480) is the right structural answer.

**But:** the IT in TASK 10 doesn't test the previously-hardened cases. Specifically:
1. The test only covers `AdminPrincipalWrapper` subscribing to `/topic/admin/orders` (positive) and `/topic/orders` (negative). It does NOT verify that a `TelegramUserPrincipal` is **still** rejected on `/topic/admin/orders` (the cross-principal isolation, which is the actual security property).
2. The test doesn't cover the regression that a `TelegramUserPrincipal` is still rejected on a generic `/topic/foo` (the P6 behaviour).

**Fix (recommend adding two tests to TASK 10, Step 1):**

```java
@Test @DisplayName("TelegramUserPrincipal CANNOT subscribe to /topic/admin/orders")
void telegramPrincipalBlockedFromAdminTopic() throws Exception {
    // Connect with X-Telegram-Init-Data instead of JWT
    // ... build a real or stubbed initData; subscribe → expect drop
}

@Test @DisplayName("Admin principal still cannot subscribe to /topic/foo (defence in depth)")
void adminPrincipalRejectedOnGenericTopic() throws Exception {
    // JWT CONNECT OK, SUBSCRIBE /topic/foo → silently dropped
    // (Same shape as `unauthorizedDestinationBlocked` already there)
}
```

The first test is the load-bearing one — without it, a regression that accidentally allowed Telegram clients onto `/topic/admin/orders` would slip through.

**Severity:** WARNING. Not blocking execution, but the test gap means the most-important security invariant (cross-principal topic isolation) is uncovered.

---

### W2 — Frontend Vite config drops `host: true` from miniapp
**File / line:** plan TASK 2, lines **524-544** (miniapp `vite.config.ts` rewrite).

Current `frontend/miniapp/vite.config.ts` has `host: true` in `server`, which allows the Mini App dev server to be reached from a phone on the same LAN (needed for the P5/P6 live-location demo with two phones). The plan's rewrite drops `host: true`. Reviewers running the RUNBOOK P5 step that uses `pnpm dev` over a LAN tunnel would suddenly find the Mini App unreachable from the second phone.

**Fix:** add `host: true` back into the `server` block of miniapp's rewritten config.

**Severity:** WARNING. Not blocking the docker-compose demo path, but breaks the `pnpm dev` workflow that the RUNBOOK / README "Dev workflow" section advertises.

---

### W3 — `useAdminOrdersSocket` imports from `@/store/authStore` — that path does not exist
**File / line:** plan TASK 9, line **2762**.

```ts
import { useAuthStore } from '@/store/authStore';
```

**Reality:** the actual store lives at `frontend/webadmin/src/stores/auth-store.ts` (plural directory, kebab-case filename). Its surface is:

```ts
export const useAuthStore = create<AuthState>((set) => ({
  auth: authStorage.load(),
  isAuthenticated: ...,
  setAuth: ...,
  ...
}));
```

There is NO `accessToken` selector at the top level — the token is inside `auth.accessToken`.

So the plan's `useAuthStore((s) => s.accessToken)` returns `undefined` always → the effect's `if (!token) return;` short-circuits → **WS never connects**.

**Fix:** correct the import path AND the selector:

```ts
import { useAuthStore } from '@/stores/auth-store';
// ...
const token = useAuthStore((s) => s.auth?.accessToken);
```

**Severity:** WARNING. Compile (well, tsc passes if `@/store/authStore` had been aliased — it's not, so it's actually a TS resolve error). Either way, the hook is dead-on-arrival without the fix. The fallback 30s polling will still keep the orders page roughly live, but the headline P9 deliverable "new order pushes within 2s" silently fails. Bumping to BLOCKER if you consider "WS-driven live update" load-bearing for the demo.

---

### W4 — `application-prod.yml` doesn't re-declare `vnpay.pay-url`, but plan removes the base default
**File / line:** plan TASK 3, lines **915-921** vs. base `application.yml` line 65-66.

Plan's prod block:
```yaml
vnpay:
  tmn-code:        ${VNPAY_TMN_CODE}
  hash-secret:     ${VNPAY_HASH_SECRET}
  pay-url:         https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
  return-url:      ${VNPAY_RETURN_URL:http://localhost/api/payment/vnpay/return}
  ipn-url:         ${VNPAY_IPN_URL:http://localhost/api/payment/vnpay/ipn}
  timeout-minutes: 15
```

This is fine in isolation — both `pay-url` and `timeout-minutes` are hard-coded in the prod profile.

But note: `VnpayProperties` has `@NotBlank String payUrl` and `@Positive int timeoutMinutes` (verified at `backend/modules/payment/src/main/java/com/shop/delivery/payment/config/VnpayProperties.java` lines 19, 23). The plan's prod yml provides both, so validation passes. No issue.

**Severity:** N/A — false alarm worth recording. Marked here because at first read it looks like the plan deletes the `pay-url` default and never replaces it; rechecked and it IS present.

---

### W5 — `OrderCreatedEvent` field order vs. payload Map ordering
**File / line:** plan TASK 9 step 1 (line 2581-2586) and step 3 broadcaster (line 2682-2693).

`OrderCreatedEvent(UUID orderId, String orderCode, Long customerId, String paymentMethod)` — but `OrderConfirmedEvent` (existing, shared module) is `(UUID orderId, String orderCode, String paymentMethod)` — different positional order and missing `customerId`.

The broadcaster handles the divergence with two separate listeners. That's fine. But future consumers reading the two events side-by-side will trip over the parameter order. Worth a header comment in `OrderCreatedEvent` explaining the divergence.

**Severity:** NIT, almost-warning.

---

### W6 — Nginx `client_max_body_size 10m` set on the outer proxy, but inner SPA nginx not configured
**File / line:** plan TASK 6, line **1502** vs. TASK 2 lines **617-628** (inner nginx).

`client_max_body_size 10m` is on the outer nginx but the inner webadmin/miniapp nginx containers default to 1 MB. If a future product-image upload route ever proxies through the inner SPA nginx (it doesn't currently — uploads go to `/api/products` which terminates at backend), there'd be a silent 413. Currently irrelevant; flag for future-proofing.

**Severity:** NIT.

---

## NITS (suggestions for improvement)

### N1 — `delivery_assignment` BIGSERIAL ids confusion in the seed (anti-join unnecessary)
**File / line:** TASK 7, lines **2019, 2034**.

The plan generates UUIDs explicitly (`b0000000-...-NN`) so `ON CONFLICT (id) DO NOTHING` would work. The plan uses `NOT EXISTS (SELECT 1 FROM delivery_assignment da WHERE da.order_id = o.id)` instead — fine because `order_id` is also UNIQUE. The choice is documented in the design notes (line 2152), but the design note says `delivery_assignment` has BIGSERIAL PK — it does NOT, it has `UUID PRIMARY KEY` (verified at V6 line 18). Minor doc drift; functionally OK.

### N2 — RUNBOOK "Payment method radio: select COD" while V11 already seeds COD orders
**File / line:** plan TASK 12, line **3507**.

In the RUNBOOK demo flow, Phone A places a real order via Mini App. The seed already has 5 PENDING COD demo orders, which is enough for the live demo. The instruction to actually place an order via Mini App is what makes the flow demo-able — but the COD path duplicates seeded data. Not a defect, just an observation. Keep as-is.

### N3 — Mermaid diagram includes `customer` and `shipper` separately from owner — minor visual asymmetry
**File / line:** TASK 11, line **3200-3225**. The customer/shipper edges arrive at nginx but the diagram puts Telegram bot polling as a direct arrow from shipper to backend (correct: long-polling). Customer→nginx (via Mini App webview) is also correct. Just verify in mermaid.live before commit.

### N4 — Backend Dockerfile's "test the jar exists" line is redundant with the next stage's COPY
**File / line:** TASK 1 line **342**.

```dockerfile
RUN test -f app/target/delivery-app.jar
```
Cosmetic; if the build succeeded, the jar exists. If not, the `mvn package` line above already failed. Doesn't hurt; remove for cleaner Dockerfile.

### N5 — `Task 6 Step 3` WebSocket curl test uses `/ws/info` with Upgrade headers — confused mental model
**File / line:** TASK 6 lines **1637-1641**.

`/ws/info` is the SockJS HTTP info endpoint, not the WS upgrade endpoint. The `Sec-WebSocket-*` headers are ignored; server returns regular JSON. Test passes but for the wrong reason. Rewrite as a simple `curl -s http://localhost/ws/info` (no upgrade headers) — same outcome with clearer intent.

---

## Detailed dimension notes

### Dim 1 — `docker compose up` smoke chain
Healthcheck chain is correct: postgres `pg_isready` → backend `/actuator/health` `"status":"UP"` → nginx via `depends_on: service_healthy`. Single exposed host port (80). Net effect: reviewer runs one command, waits ~90s, sees 5 `(healthy)` rows. **PASS.**

### Dim 2 — Backend Dockerfile vs. multi-module Maven
The plan's Dockerfile correctly copies 10 pom.xml files (parent + 9 modules + app/pom.xml). The `-pl app -am` rebuild flag walks the dep graph upward. `app/pom.xml` declares `finalName=delivery-app` (verified: existing jar at `backend/app/target/delivery-app.jar`). Spring Boot Maven Plugin produces an executable jar — `java -jar /app/app.jar` works. **PASS.**

Two design choices worth highlighting:
1. **No mvnw bundled into runtime layer** — correct; only the built jar is copied. ✓
2. **Builder uses `mvn` directly, not `mvnw`** — fine since the builder pre-installs Maven 3.9.9. The mvnw wrapper inside the repo is only needed for hosts without Maven; container doesn't need it.

### Dim 3 — Frontend Dockerfiles vs. pnpm workspace
- `pnpm-workspace.yaml` lists `["shared", "miniapp", "webadmin"]` (verified).
- The Dockerfile context is `frontend/` (not `frontend/miniapp/`) which is correct because pnpm needs the workspace root to resolve `@shop/shared`.
- The two-pass COPY (first the `package.json` files for install cache, then the source) is the canonical pnpm pattern. ✓
- `--filter @shop/miniapp...` (with `...`) means "this package and its workspace dependencies" — covers `@shop/shared`. ✓
- Vite `base` is set via `({ mode })` factory so `pnpm dev` still serves at `/`. ✓
- `--frozen-lockfile` enforces lockfile-package.json consistency. ✓
- `corepack prepare pnpm@9.12.0 --activate` matches the root `packageManager: "pnpm@9.12.0"` (verified). ✓

**PASS.** The only friction is W2 (missing `host: true` in the miniapp rewrite).

### Dim 4 — `application-prod.yml` env-var contract
Every required secret (`BOT_TOKEN`, `BOT_USERNAME`, `JWT_SECRET`, `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `DB_*`) has no default → Spring fail-fast. `ddl-auto: validate` + `flyway.baseline-on-migrate: false` prevent silent drift. `forward-headers-strategy: framework` correctly handles X-Forwarded-* from nginx. `management.endpoint.health.show-details: never` prevents Flyway/DB leakage. `info.app.name: ${spring.application.name}` chains correctly to base profile. **PASS.**

### Dim 5 — `.env.example`
Every var the prod yml references appears in `.env.example`. Plan's Step 9 of TASK 15 (line 4072-4080) is a great defensive check — it `comm`'s `application-prod.yml` `${VAR}` against `.env.example` keys. Worth retaining.

Nit: `.gitignore` already has `.env` (verified line `.env`). Plan's Step 3 of TASK 4 (line 1132-1140) is a no-op append-if-absent; safe.

**PASS.**

### Dim 6 — V11 demo seed
- Schema: `admin_user` ✓, `product` ✓, `telegram_user` ✓, `user_role` ✓, `orders` ✓, `order_item` ✓, `status_history` ✗ (column name), `delivery_assignment` ✗ (enum), `rating` ✓, `payment` ✓, `shipper_profile` ✓.
- Foreign keys: customer_id → telegram_user (✓ — seeded users 9000000001..3), shipper_id → telegram_user (✓ — seeded 9000000101..3), order_id → orders (✓ via demo UUIDs), product_id resolved by name subquery (✓).
- Idempotency: ON CONFLICT keys match existing UNIQUE/PK columns (`admin_user.email` UNIQUE ✓, `product.name` is made UNIQUE by the migration's DO $$ block ✓, `telegram_user.id` PK ✓, `user_role(telegram_user_id, role)` UNIQUE ✓, `orders.id` PK ✓, `rating.order_id` UNIQUE ✓, `payment.id` PK ✓).
- BCrypt hash matches V5's hash (verified V5 line 36): `$2a$10$8tbM0mvZZFQuz9KVhA6lOOZQfM2GxHIgmTWXbcVQ2ml353wyiYktO` → password `admin123`. Same password, different email → OK.
- No PK collision with V5 admin (`admin@shop.local` vs. `shop@example.com`).
- Coords valid Vietnam (21.0285, 105.8542 = Hoàn Kiếm).
- Sums: `total = subtotal + delivery_fee` ✓ via expression.

**FAIL** on (B1) AssignmentStatus enum, (B2) STARTED unique index, (B3) `status_history` column name. The seed is otherwise of high quality and the design (deterministic UUIDs, idempotent on conflicts, status spread across phases) is thoughtful.

### Dim 7 — Admin WS dual-auth CONNECT
`JwtService.tryParse` returns `Optional<JwtClaims(adminUserId, email, role)>` (verified at `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/JwtService.java`). The plan's CONNECT path uses `tryParse(token)` + `claims.role()` + role gate against `SHOP_OWNER` constant. `AdminPrincipal(Long adminUserId, String email)` record exists (verified). `AdminPrincipalWrapper` implements `Principal` correctly with disambiguating `getName()` ("admin-N"). **PASS** with W1 (test gap).

### Dim 8 — `OrderCreatedEvent` + broadcaster
- `OrderCreatedEvent` correctly placed in `backend/modules/shared/src/main/java/com/shop/delivery/shared/event/`. ✓
- `OrderService.create` is already `@Transactional` (verified line 73). Publishing inside it + `@TransactionalEventListener(AFTER_COMMIT)` is the canonical pattern. ✓
- `OrderConfirmedEvent` already exists (verified) — the broadcaster's second listener wires it correctly.
- `SimpMessagingTemplate` is auto-bean'd by `@EnableWebSocketMessageBroker`. ✓
- Broadcaster try/catch prevents AFTER_COMMIT broker failures from poisoning subsequent listeners. ✓
- Payload `Map.of(...)` keys are 7 distinct strings → no Map.of arity issue (Java's Map.of supports up to 10 pairs). ✓

**PASS.**

### Dim 9 — `WebSocketAdminAuthIT`
- `PostgresTestContainer` JVM-singleton base exists at `backend/app/src/test/java/com/shop/delivery/support/PostgresTestContainer.java` (verified). ✓
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@LocalServerPort` is correct. ✓
- `WebSocketStompClient` + `SockJsClient` is the canonical Spring test client for `withSockJS()` endpoints. ✓
- `JwtService.issueAccessToken(1L, "admin@shop.local")` matches the existing method signature. ✓
- 4 tests cover the JWT happy path, missing header, garbage token, and disallowed-destination silent drop. ✓
- The "session stays alive after disallowed SUBSCRIBE" assertion correctly captures the `preSend` return-null contract. ✓

**PASS** with W1 (missing cross-principal isolation test).

### Dim 10 — README + RUNBOOK
- Mermaid diagram syntax is valid `flowchart LR`; 7 nodes + 2 external; GitHub renders natively. ✓
- README quick-start is 3 commands. ✓
- Demo accounts table includes both seed admin and V5 baseline admin (avoids reviewer confusion). ✓
- RUNBOOK covers P1-P8 with concrete CLI commands. NCB test card details are accurate per VNPay sandbox docs. ✓
- Defense Q&A section is genuinely useful (7 questions with crisp answers).

**PASS.**

### Dim 11 — Lazy-load `/reports`
- `React.lazy + Suspense` is canonical. ✓
- `ReportsPage` is a named export (verified line 19 of `frontend/webadmin/src/pages/ReportsPage.tsx`), so the `.then(mod => ({ default: mod.ReportsPage }))` re-shape is required and the plan does it. ✓
- Suspense fallback is inline; matches existing webadmin style. ✓
- `DashboardPage` is NOT lazy-loaded (intentional — it's the post-login landing page and recharts is needed there immediately).

**PASS.**

### Dim 12 — `RateOrderResponse` DTO
- DTO record shape is good (omits PII customerId/shipperId).
- `from(Rating)` factory **does not compile** due to B4 above.
- Test update correctly asserts `customerId.doesNotExist()` / `shipperId.doesNotExist()` (PII regression guard). ✓
- No FE consumer changes needed (the bot is the only consumer; verified by absence of `Rating` references in `frontend/shared/src/`).

**FAIL** on B4.

### Dim 13 — Task atomicity
14 tasks → 14 commits (TASK 15 is verification-only, no commit). All commits are scoped to one logical change:
- T1: backend Dockerfile + dockerignore (~90 lines)
- T2: 2 frontend Dockerfiles + 2 vite configs + 1 dockerignore (~120 lines)
- T3: prod yml rewrite (~70 lines)
- T4: env.example + gitignore tweak (~80 lines)
- T5: compose.yml (~130 lines)
- T6: nginx.conf (~100 lines)
- T7: V11 SQL (~250 lines)
- T8: WebSocketConfig + AdminPrincipalWrapper (~150 lines + 30 lines)
- T9: 5 files (event + service + broadcaster + hook + page) — borderline ~250 lines spread but all one "live admin feed" feature
- T10: WebSocketAdminAuthIT (~150 lines)
- T11: README (~200 lines)
- T12: RUNBOOK (~300 lines)
- T13: App.tsx (~40 lines)
- T14: DTO + controller + test (~80 lines)

No commit is >500 lines after the V11 fix. T9 is the only borderline-large commit (touches 5 files across 3 modules), but they form one coherent feature. **PASS.**

### Dim 14 — Acceptance criteria
Each task ends with a concrete bulleted "Acceptance" list. Criteria are mostly testable (exit codes, file existence, HTTP status codes, image sizes, test counts). **PASS.**

---

## Recommended commit sequence (after fixes)

The 4 BLOCKERS can all be folded into their existing tasks (no new commits needed):
1. **B1+B2** → patch TASK 7's SQL inside the V11 file before commit
2. **B3** → patch TASK 7's status_history INSERT before commit
3. **B4** → patch TASK 14's DTO file before commit

W1 (test gap) → expand TASK 10's IT (still one commit).
W2 (vite host) → fold into TASK 2's vite.config.ts edit.
W3 (store path) → patch TASK 9's hook file before commit.

After these mechanical patches, P9 ships in 14 commits as planned.

---

## Final note

The plan is unusually thorough — the design notes catch many edge cases that lesser plans would miss (BuildKit cache mounts, nginx `map $http_upgrade` placement at http level, SPA fallback semantics through nginx prefix stripping, Telegram ID safe range for demo seed, deferred-debt closure for admin WS auth, sub-2s WS-driven dashboard refresh). The blockers are all mechanical schema/type mismatches with the actual codebase, not architectural failures. After patching them, **P9 will achieve the phase goal: a clean `docker compose up` → 5 healthy containers → reviewer-ready demo in ~3 minutes.**

🎯 **Verdict: NEEDS REVISION (4 BLOCKERS, 6 WARNINGS, 5 NITS — all mechanical; no waves need restructuring)**
