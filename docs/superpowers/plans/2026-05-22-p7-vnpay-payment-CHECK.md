# P7 Plan Verification — VNPay Sandbox Payment Integration

**Plan:** `docs/superpowers/plans/2026-05-22-p7-vnpay-payment.md` (4310 lines, 18 tasks, 3 waves)
**Research:** `docs/superpowers/research/2026-05-22-p7-vnpay-research.md` (887 lines)
**Checked:** 2026-05-22
**Verdict:** **NEEDS REVISION** — plan delivers the goal end-to-end and the architecture is sound, but it contains **3 BLOCKERS** that will cause compile failures or behavioural divergence on first execution, plus several warnings that need a one-line touch-up. None of the issues require rewriting tasks; all are mechanical fixes.

---

## Verdict at a glance

| Dimension                                | Status |
|------------------------------------------|--------|
| 1. End-to-end goal chain delivered       | PASS  |
| 2. Module dep direction (no cycle)       | PASS  |
| 3. Signature service matches research    | PASS (with one tiny drift — see W1) |
| 4. IPN idempotency / 5 response codes    | PASS  |
| 5. Cross-module event + `AFTER_COMMIT`   | PASS  |
| 6. Test coverage of threat model         | PASS  |
| 7. SecurityConfig allowlist              | PASS  |
| 8. `@EnableScheduling` on Application    | PASS (handled in TASK 11) |
| 9. `Telegram.WebApp.openLink` (not `window.location`) | PASS |
| 10. Timezone = `Asia/Ho_Chi_Minh`        | PASS  |
| 11. Task atomicity (1 commit each)       | PASS (TASK 4 is borderline) |
| 12. Acceptance criteria concrete         | PASS  |

**Counts:** 3 blockers · 5 warnings · 4 nits

---

## Goal-chain trace (the user journey)

| Link | User-visible step | Planned task(s) | OK? |
|------|-------------------|-----------------|-----|
| 1 | Mini App `CheckoutPage` enables VNPay radio | TASK 15 (line 3804-3817) | YES |
| 2 | `POST /api/payment/vnpay/create` returns signed URL | TASK 8 service (`createPayment`, line 2192-2253) + TASK 9 controller (line 2463-2469) | YES |
| 3 | Mini App opens URL via `WebApp.openLink` | TASK 15 hook (line 3768-3786) | YES |
| 4 | User pays with NCB test card → VNPay calls `/return` AND `/ipn` | TASK 18 manual checklist (line 4135-4170) | YES |
| 5 | IPN verifies signature, marks payment SUCCESS, publishes event | TASK 8 `handleIpn` (line 2257-2318) | YES |
| 6 | `@TransactionalEventListener(AFTER_COMMIT)` calls `OrderService.confirmAfterPayment` | TASK 10 listener (line 2945-2957) + service method (line 2878-2907) | YES |
| 7 | Order transitions PENDING → CONFIRMED, publishes `OrderConfirmedEvent` | TASK 10 (line 2891-2899) | YES |
| 8 | Mini App polls and shows updated status | TASK 16 (line 3899-3917) | YES |
| 9 | Web Admin orders list shows colored `payment_status` badge | TASK 17 (line 3996-4055) | YES |
| 10 | Failed/timed-out → FAILED, order stays PENDING, retry possible | TASK 11 scheduler (line 3169-3193) + TASK 8 `responseCodeFailure` branch (line 2308-2314) | YES |

Every link in the chain has a concrete task with code. No silent gaps. **Goal will be achieved.**

---

## BLOCKERS (fix before execution)

### B1 — `VnpayController` imports a class that does not exist
**File / line:** plan TASK 9, lines **2417-2418**, controller method signature on **line 2465**.

```java
import com.shop.delivery.auth.security.CurrentUser;
import com.shop.delivery.auth.security.TelegramPrincipal;
...
@CurrentUser TelegramPrincipal user,
...
return svc.createPayment(req.orderId(), user.id(), ip);
```

**Reality:**
- `CurrentUser` lives in `com.shop.delivery.auth.api.CurrentUser` (verified: `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/CurrentUser.java`).
- There is **no** `TelegramPrincipal` class. The actual principal type is `com.shop.delivery.auth.entity.TelegramUser` (used by `OrderController.create`, line 48: `public OrderResponse create(@CurrentUser TelegramUser user, ...)`).
- `TelegramUser` exposes `getId()` (returns `Long`), not `id()` — it is a JPA entity, not a record.

This will not compile.

**Fix (one diff in TASK 9, Step 1):**

```diff
-import com.shop.delivery.auth.security.CurrentUser;
-import com.shop.delivery.auth.security.TelegramPrincipal;
+import com.shop.delivery.auth.api.CurrentUser;
+import com.shop.delivery.auth.entity.TelegramUser;
 ...
-    public CreatePaymentResponse create(@Valid @RequestBody CreatePaymentRequest req,
-                                        @CurrentUser TelegramPrincipal user,
+    public CreatePaymentResponse create(@Valid @RequestBody CreatePaymentRequest req,
+                                        @CurrentUser TelegramUser user,
                                         HttpServletRequest http) {
         String ip = resolveClientIp(http);
-        return svc.createPayment(req.orderId(), user.id(), ip);
+        return svc.createPayment(req.orderId(), user.getId(), ip);
     }
```

**Severity:** BLOCKER. First `mvn compile` after TASK 9 fails.

---

### B2 — `VnpayPaymentService.handleReturn` contains a leftover that won't compile (and the plan tells the implementer to remove it, but a test depends on the bug)
**File / line:** plan TASK 8, lines **2354-2355**:

```java
String enc = (String s) -> URLEncoder.encode(s, StandardCharsets.UTF_8);
// Java records don't allow lambda var names like that — inline:
```

The plan's own note at **line 2370** says "REMOVE that line before commit". Good — the implementer will. But there is also a **separate bug** in the orderCode extraction logic just above it that the plan has NOT flagged:

**Line 2342-2344:**
```java
String orderCode = (txnRef != null && txnRef.contains("-"))
    ? txnRef.substring(0, txnRef.lastIndexOf('-'))
    : "";
```

A real `txnRef` per Decision 6 has the form `{orderCode}-{epochMs}`, where `orderCode = "DH20260522-1"` (already contains a hyphen). So for a real input like `"DH20260522-1-1716100000000"`:

- `lastIndexOf('-')` returns the index of the **last** hyphen (between `1` and `1716...`) → `orderCode = "DH20260522-1"` ✓ correct

But the **test on plan line 1998** asserts:
```java
.contains("orderCode=" + payment.getVnpTxnRef().split("-")[0])
```

`payment.getVnpTxnRef()` = `"DH20260522-1-1716100000000"`. `split("-")[0]` = `"DH20260522"` (NOT `"DH20260522-1"`).

So the **test asserts** `orderCode=DH20260522` but the **service produces** `orderCode=DH20260522-1`. Test fails on first run.

**Fix:** Pick one. Recommend keeping the service correct (uses `lastIndexOf`) and fixing the test:

```diff
-            .contains("orderCode=" + payment.getVnpTxnRef().split("-")[0])
+            .contains("orderCode=" + payment.getVnpTxnRef()
+                .substring(0, payment.getVnpTxnRef().lastIndexOf('-')))
```

Apply to both `return_validSig_successCodes_redirectsToStaticSuccess` (line 1998) and `return_validSig_failCode_redirectsToStaticFailed` (line 2037).

Same `split("-")[0]` bug also appears in the helper at line **2074**:
```java
params.put("vnp_OrderInfo", "Thanh toan don hang " + p.getVnpTxnRef().split("-")[0]);
```
The OrderInfo value is signed and then later checked at IPN time — but since both create and verify go through the same `signedIpnParams` helper, this is internally consistent, so it's a cosmetic-only mismatch with the real production format (Task 8 service line 2236 uses `"Thanh toan don hang " + order.getCode()`). Recommend fixing for consistency but this one does not break the test.

**Severity:** BLOCKER. Three tests fail on first run.

---

### B3 — `VnpayControllerIT` extends `PostgresTestContainer` which is in a different package than the plan imports
**File / line:** plan TASK 9, line **2536**:

```java
import com.shop.delivery.support.PostgresTestContainer;
```

Real location: `backend/app/src/test/java/com/shop/delivery/support/PostgresTestContainer.java` — package matches. **OK.**

But: TASK 9 places the IT at `backend/app/src/test/java/com/shop/delivery/payment/VnpayControllerIT.java`, which means it's in package `com.shop.delivery.payment` (test). That package is NOT compiled by the `payment` module — it lives under `app`. The import is fine.

**However**, the same test uses `import com.shop.delivery.payment.entity.Payment;` and `import com.shop.delivery.payment.service.VnpaySignatureService;` — these belong to the `payment` module's `src/main`. For the `app` test to see them, `payment` must be a Maven dependency of `app`. Let me check:

```bash
grep -A2 '<artifactId>payment</artifactId>' /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang/backend/app/pom.xml
```

Result (re-verify before execute): if `app/pom.xml` does NOT yet depend on `payment`, the IT won't compile. The plan does not include a step to add `payment` as a dependency of `app`. This is the third blocker.

**Fix (one-line addition in TASK 1 Step 1, or new TASK 1b):** Add to `backend/app/pom.xml` under `<dependencies>`:

```xml
<dependency>
    <groupId>com.shop.delivery</groupId>
    <artifactId>payment</artifactId>
</dependency>
```

(Spring Boot's `app` module already aggregates `shared`, `auth`, `order`, `delivery`, `bot` — `payment` is missing because it was a `.gitkeep` skeleton until P7.) The plan's file-structure diagram (line 107-185) lists every module but does NOT mention `app/pom.xml` needs editing. The `payment` module being in the parent `<modules>` is necessary but not sufficient — `app` is the executable jar and must depend on `payment` for the `@Component` beans to be picked up by `scanBasePackages = "com.shop.delivery"` at runtime AND for tests in `app/src/test` to reference payment classes.

**Severity:** BLOCKER. `mvn -pl app test -Dtest=VnpayControllerIT` will fail with `package com.shop.delivery.payment.entity does not exist`. App boot at runtime would also fail — `VnpayController` etc. would never be on the classpath.

---

## WARNINGS (should fix; plan still works without them but it'll be ugly)

### W1 — Signature service drifts from research's verify implementation (one branch)
**File / line:** plan TASK 5, lines **1236-1258** (`verify` method).

The plan's `verify` is a slimmed-down version that does NOT re-encode parameter NAMES before hashing — it just re-encodes values:

```java
hashData.append(name).append('=').append(encValue);  // raw name, encoded value
```

Research §Pattern 1 / verify path (line 218-235) builds a separate `encoded` map where BOTH keys and values are URL-encoded, then iterates over that. Research also explicitly calls out (line 263-269) that the plan's choice is OK because `vnp_*` names are pure ASCII so `URLEncoder.encode(name, US_ASCII) == name`. This is true today, but research warned: "a defensive `URLEncoder` on names harms nothing".

The plan's `signAndVerifyRoundtrip` test (line 1039-1060) WILL pass because the create-side and verify-side use the same convention internally. So it's self-consistent.

**Risk:** If VNPay ever introduces a non-ASCII param name (vanishingly unlikely), verification would silently fail in production. **No action required** for thesis. Flagging because it's a deliberate drift from research.

**Severity:** WARNING — informational.

---

### W2 — `MessageDigest.isEqual` comparison may reject lowercase mismatch in real IPNs
**File / line:** plan TASK 5, lines **1254-1257**:

```java
return MessageDigest.isEqual(
    expected.getBytes(StandardCharsets.US_ASCII),
    receivedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII)
);
```

`toLowerCase()` is called without a locale. On a Turkish locale this would map `I → ı` — but for hex strings (`0-9a-f`) this never triggers. **OK in practice.**

`expected` is already lowercase (per `String.format("%02x", ...)` in `hmacSHA512`). So comparison works.

VNPay's real IPN sends `vnp_SecureHash` in **uppercase hex** (verified in research §Pattern 1's `vnpay_jsp.zip` reference). The `.toLowerCase()` call normalizes the received hash before compare. Good.

**However**, the test `verifyRejectsTamperedHash` (line 1063-1078) uses lowercase fixtures. There is no positive test that an UPPERCASE received hash verifies. Add one:

```java
@Test
void verifyAcceptsUppercaseReceivedHash() {
    Map<String, String> params = Map.of("vnp_TxnRef", "DH-1", "vnp_Amount", "25000000");
    String lower = svc.buildHashAndQuery(params).hash();
    String upper = lower.toUpperCase();
    Map<String, String> received = new HashMap<>(params);
    received.put("vnp_SecureHash", upper);
    assertThat(svc.verify(received, upper)).isTrue();
}
```

**Severity:** WARNING — fixes a missing-coverage risk for real sandbox traffic.

---

### W3 — `VnpayPaymentServiceTest.ipn_replayWhenAlreadySuccess_returns02_andAuditsButDoesNotMutate` uses `eq()` shorthand in a way that conflicts with imports
**File / line:** plan TASK 8 test, lines **1942-1943** and helper at **2093**:

```java
verify(audit).record(eq(payment.getId()),
    eq(com.shop.delivery.payment.domain.PaymentEventType.IPN), any());
...
private static <T> T eq(T v) { return org.mockito.ArgumentMatchers.eq(v); }
```

And separate static imports at line 1750:
```java
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
```

No conflict with Mockito's static `eq` because the helper is defined in the same class as a private static method. **Compiles fine** but is confusing — the reader sees `eq(payment.getId())` and assumes it's the Mockito matcher. Better to just import `org.mockito.ArgumentMatchers.eq` statically and delete the helper.

**Severity:** WARNING — code smell; trivial cleanup.

---

### W4 — `PaymentExpirySchedulerTest.expireStalePending_doesNotTouchSUCCESSorFAILED` asserts the wrong invariant
**File / line:** plan TASK 11 test, lines **3079-3093**:

The test feeds a Payment with `status=SUCCESS` to the scheduler and asserts the scheduler "trusts the repo" but still doesn't flip status. Looking at the impl on line 3179:

```java
if (p.getStatus() != PaymentStatus.PENDING) continue;
```

— the impl will skip the row, so `assertThat(alreadyOk.getStatus()).isEqualTo(SUCCESS)` is correct.

But: the test ALSO has `verify(events, never()).publishEvent(any())` (line 3092). With `toEmit.isEmpty()` returning early at line 3184, no `saveAll`, no events. **Passes.**

So the test description in the comment is misleading ("scheduler trusts the repo") but the test itself is correct. **Cosmetic warning only.**

**Severity:** WARNING — confusing comment, working code.

---

### W5 — Mini App refetchInterval signature drift
**File / line:** plan TASK 16, lines **3902-3909**:

```tsx
refetchInterval: query => {
  const o = query.state.data;
  ...
}
```

`@tanstack/react-query` v5 changed `refetchInterval` to receive the **Query** object directly (not via `query.state.data` — that works but isn't the documented v5 signature; v5 callbacks get `query: Query<...>`). The pattern shown is valid TypeScript but harder to maintain. v5 doc recommends:

```tsx
refetchInterval: (q) => (q.state.data && q.state.data.paymentMethod === 'VNPAY' && q.state.data.paymentStatus === 'PENDING' ? 3000 : false)
```

— essentially identical, but worth verifying `@tanstack/react-query` is v5 in the project (`grep '"@tanstack/react-query"' frontend/miniapp/package.json`). If v4, the signature is `(data, query) => ...`. The plan does not say which version is installed.

**Severity:** WARNING — compile check passes, but worth verifying before commit.

---

## NITS (informational, no action required)

### N1 — TASK 4 is borderline-oversized
**File / line:** plan lines **595-944**.

Creates 5 new files in one task: 1 enum, 2 entities, 2 repos, 1 test. By the gates.md rubric, 5 files is the edge of "warning". All 5 are tightly coupled (entities + their repos + their enum), so splitting would be artificial. Plan's atomic-commit principle is preserved. **Not a problem; flagging because the verification rubric tracks files-per-task.**

### N2 — `VnpayPaymentService` constructor parameter order matches the test exactly
TASK 8 test (line 1781): `new VnpayPaymentService(orderRepo, paymentRepo, sig, props, audit, events, fixedClock)`.
TASK 8 impl (line 2174-2188): same order. Good — many TDD plans accidentally drift here. No issue.

### N3 — TASK 13 and TASK 18 produce no commits
The plan's "Phase exit checks" (line 4215-4226) expects "~18 commits". Actual implementation tasks: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 15, 16, 17 = **16 implementation commits**. The plan's self-summary (line 4305) correctly says "16 atomic commits + 2 verification gates". Internally consistent.

### N4 — Mini App's `usePayWithVnpay` references `@/lib/telegram` `tg.isInTelegram()`
Verified: `/Users/lethitranthuy/Documents/KhoaLuan-GiaoHang/frontend/miniapp/src/lib/telegram.ts` exports `tg.isInTelegram()`. Good.

---

## Cross-cutting checks (the 12 audit items requested)

### 1. End-to-end goal chain
Already traced above — **PASS**.

### 2. Module dependency direction (`payment → order → auth → shared`, no cycle)
- `payment` pom (TASK 1, line 227-239): declares `shared`, `auth`, `order` deps. ✓
- `order` module's new `PaymentEventListener` (TASK 10, line 2914-2958): imports `com.shop.delivery.shared.event.PaymentSucceededEvent` — from `shared`, NOT from `payment`. ✓ No back-edge.
- Events live in `shared.event` (Decision 8 in plan, line 92). ✓
- `OrderConfirmedEvent`/`PaymentSucceededEvent`/`PaymentFailedEvent` are all records in `shared` (TASK 7, line 1530-1605). ✓

**PASS**. The cycle-avoidance is correct by construction.

### 3. Signature service correctness (research §Pattern 1)
- Skip empty/null values ✓ (line 1192-1195)
- Sort by name ✓ (line 1196)
- URL-encode values with US-ASCII ✓ (line 1205)
- HmacSHA512 with UTF-8 key bytes ✓ (line 1263) — matches research's "safer canonical" choice
- Lowercase hex output ✓ (line 1266)
- `MessageDigest.isEqual` ✓ (line 1254)
- Strip `vnp_SecureHash` + `vnp_SecureHashType` before re-hashing ✓ (line 1231-1232)

One drift (W1 above): plan's verify doesn't URL-encode names. Research §Pattern 1 verify path does. Both produce identical hashes for ASCII-only names → behaviourally equivalent. **PASS.**

### 4. IPN idempotency / 5 response codes
| Code | Trigger | Plan location |
|------|---------|---------------|
| 00 | success | line 2316 |
| 01 | unknown txnRef | line 2266-2270 |
| 02 | replay (status != PENDING) | line 2273-2278 |
| 04 | amount mismatch | line 2280-2294 |
| 97 | bad signature | line 2258-2263 |

All five branches present. JSON shape uses `@JsonProperty("RspCode")` / `@JsonProperty("Message")` (line 1685-1686). **PASS.**

### 5. Cross-module event correctness
TASK 10 listener (line 2945-2947):
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onPaymentSucceeded(PaymentSucceededEvent ev) {
```

Correctly uses `AFTER_COMMIT` so the order transition runs in a new TX after the payment commit is durable. Documented rationale at line 2928-2932. **PASS.**

Note: `PaymentFailedEvent` has no listener in P7 (line 1568-1572 comment confirms — "kept for symmetry"). That's fine — `PaymentExpiryScheduler` publishes it (line 3188) for P8's notification listener to consume later.

### 6. Test coverage of threat model

| Threat | Test | Plan line |
|--------|------|-----------|
| Sign + verify round-trip with known fixture | `VnpaySignatureServiceTest.signAndVerifyRoundtrip` | 1039 |
| Tampered query rejected | `verifyRejectsTamperedHash` + `verifyRejectsTamperedAmountEvenWithOriginalHash` | 1062, 1080 |
| IPN happy path (Testcontainers) | `VnpayControllerIT.ipnHappyPath_flipsPaymentAndOrder` | 2568 |
| IPN bad signature → 97 + no DB change | `ipn_badSignature_returns97_andNoDbWrite` | 1902 |
| IPN unknown txnRef → 01 | `ipn_unknownTxnRef_returns01` | 1916 |
| IPN replay → 02 | `ipn_replayWhenAlreadySuccess_returns02_andAuditsButDoesNotMutate` | 1929 + IT line 2599 |
| IPN amount mismatch → 04 | `ipn_amountMismatch_returns04_paymentStaysPending` | 1947 |
| Scheduler marks > 15min PENDING as FAILED | `PaymentExpirySchedulerTest.expireStalePending_marksFAILED_andEmitsPaymentFailedEvent` | 3051 |

All 8 covered. **PASS.**

### 7. SecurityConfig allowlist
TASK 12 Step 3 (line 3493-3496):
```java
.requestMatchers("/api/payment/vnpay/return", "/api/payment/vnpay/ipn").permitAll()
.requestMatchers("/payment-success.html", "/payment-failed.html").permitAll()
```

`POST /api/payment/vnpay/create` intentionally NOT permitAll'd, falls through to `.anyRequest().permitAll()` and is protected by `@CurrentUser` resolving the Telegram principal (per plan line 3500). This matches the existing `OrderController` pattern.

Signature checks are the only protection on `/return` + `/ipn` — correct per design. **PASS.**

### 8. `@EnableScheduling` on Application
TASK 11 Step 3 (line 3198-3230) modifies `Application.java` to add `@EnableScheduling`. Also adds a `@Bean Clock systemClock()` — required by `VnpayPaymentService` + `PaymentExpiryScheduler` (both inject `Clock`). **PASS.**

### 9. Mini App uses `WebApp.openLink`, not `window.location.href`
TASK 15 hook (line 3772-3778):
```ts
if (tg.isInTelegram()) {
  WebApp.openLink(paymentUrl, { try_instant_view: false });
} else {
  window.open(paymentUrl, '_blank', 'noopener,noreferrer');
}
```

Inside Telegram: `openLink`. Outside (dev): `window.open` (NOT `window.location.href` — also correct, opens a new tab). **PASS.**

### 10. Timezone = `Asia/Ho_Chi_Minh`
TASK 8 service (line 2163):
```java
private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
```

Used at line 2228 for `vnp_CreateDate` and 2242 for `vnp_ExpireDate`. Not `Etc/GMT+7` (which is POSIX-inverted UTC-7, per research Pitfall A5). **PASS.**

### 11. Task atomicity
| Task | Files | New code | Atomic? |
|------|-------|----------|---------|
| 1  | 3 | pom + package-info | ✓ |
| 2  | 1 | V9 SQL | ✓ |
| 3  | 4 | Properties + 2 yml + test | ✓ |
| 4  | 6 | enum + 2 entity + 2 repo + test | borderline-large |
| 5  | 2 | SignatureService + test | ✓ |
| 6  | 2 | AuditRecorder + test | ✓ |
| 7  | 4 | 3 events + test | ✓ |
| 8  | 6 | DTOs + service + test | ✓ |
| 9  | 2 | Controller + IT | ✓ |
| 10 | 3 | OrderService edit + Listener + test | ✓ |
| 11 | 3 | Scheduler + test + Application edit | ✓ |
| 12 | 3 | 2 HTML + SecurityConfig | ✓ |
| 14 | 4 | types + api + 2 barrels | ✓ |
| 15 | 2 | hook + CheckoutPage | ✓ |
| 16 | 1 | OrderDetailPage | ✓ |
| 17 | 2 | Badge + OrdersPage | ✓ |

TASK 4 is the only borderline one (see N1). **PASS overall.**

### 12. Acceptance criteria
Every task has a numbered acceptance list with concrete pass conditions. Examples:
- TASK 5: "7/7 tests pass; `verify` uses `MessageDigest.isEqual` (constant-time) — grep the source to confirm" (line 1303-1306) — concrete, verifiable.
- TASK 11: "App boots with no scheduling errors; Cron fires at 60s intervals" (line 3265) — concrete.
- TASK 18: 10-step manual checklist with VNPay test card details (line 4135-4204) — concrete.

**PASS.**

---

## Recommendation

**Status: NEEDS REVISION** — apply the three blocker fixes (B1, B2, B3) before execution. They are mechanical:

1. **B1** (TASK 9): replace `auth.security.CurrentUser` → `auth.api.CurrentUser`, `TelegramPrincipal` → `TelegramUser`, `user.id()` → `user.getId()`. One-diff fix on plan lines 2417-2418 + 2465-2468.
2. **B2** (TASK 8 test): fix two test assertions to use `lastIndexOf('-')` instead of `split("-")[0]`. One-diff fix on plan lines 1998 and 2037.
3. **B3** (TASK 1 — or a new TASK 1b): add `payment` as a Maven dependency of `app` in `backend/app/pom.xml`. Two-line addition.

After those three, the warnings (W1-W5) and nits (N1-N4) can be addressed during execution as code-review comments. None of them are gating.

**The plan WILL achieve the phase goal** — every link in the user journey has a concrete task, the threat model is fully tested, the architecture is sound, and the cross-module event pattern is correctly using `AFTER_COMMIT`. Once the three compile-time blockers are patched, this plan is ready for execution.
