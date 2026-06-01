# P7 Code Review — VNPay Sandbox Payment Integration

**Reviewed:** 2026-06-01
**Commit range:** `3b99fa9^..42e2431` (16 commits)
**Reviewer:** Claude (gsd-code-reviewer)

## Summary

- Files reviewed: 27
  - Backend (Java): 16 (payment module) + 4 (cross-module: order listener, OrderService change, shared events, SecurityConfig, Application)
  - Backend (other): V9__payment.sql, application.yml, 2 static HTML pages, 1 integration test
  - Frontend: 5 (shared types/api, miniapp use-pay-with-vnpay, OrderDetailPage, CheckoutPage diff, webadmin OrdersPage, PaymentStatusBadge)
- Critical: 1
- Important: 5
- Minor: 6
- **Overall verdict: NEEDS FIXES** — one Critical (audit gap on signature failure / unknown txnRef) is a deviation from the stated threat model that should be resolved before merge. Everything else is well-executed; the critical issue is narrow in scope and has a 5-line fix.

The signature service, IPN idempotency, cross-module event flow with `Propagation.REQUIRES_NEW` (executor's mid-flight fix was applied — confirmed at `OrderService.java:161`), and the Telegram `WebApp.openLink` redirect are all correctly implemented and match the design spec.

---

## Critical findings

### CR-01: IPN does not record audit trail on signature failure or unknown txnRef

**File:** `backend/modules/payment/src/main/java/com/shop/delivery/payment/service/VnpayPaymentService.java:156-168`

**Issue:** The threat model in the design spec (and the explicit review requirement) calls for `payment_transaction` rows to be appended on every IPN call — including invalid-signature probes — so we have evidence of probing attempts. The current implementation returns early **before** calling `audit.record(...)` in two cases:

1. **Invalid checksum (RspCode 97)** — line 158-161: `if (!sig.verify(...)) return IpnResponse.invalidChecksum();`
2. **Unknown txnRef (RspCode 01)** — line 165-168: `if (maybe.isEmpty()) return IpnResponse.orderNotFound();`

Only the `02` (replay), `04` (amount mismatch), and `00`/failure-code paths call `audit.record(...)`. This means an attacker probing the public IPN endpoint with random hashes leaves zero evidence in the database.

Note: the case where signature fails AND we have no Payment row to attach the audit to is a real limitation (the `payment_transaction.payment_id` column is `NOT NULL REFERENCES payment(id)` per V9 schema, so you cannot insert a free-floating audit row). So fixing this fully requires either (a) loosening the FK constraint to allow NULL `payment_id`, or (b) only auditing when a Payment match exists.

**Suggested fix:** Match-first, then verify. Rearrange so we look up the Payment row before signature check, and audit even on signature failure when the txnRef is known:

```java
@Transactional
public IpnResponse handleIpn(Map<String, String> params) {
    String txnRef = params.get("vnp_TxnRef");
    Optional<Payment> maybe = (txnRef != null)
        ? paymentRepo.findByVnpTxnRef(txnRef)
        : Optional.empty();

    // Always audit when we have a Payment match — even on bad signature.
    maybe.ifPresent(p -> audit.record(p.getId(), PaymentEventType.IPN, params));

    String receivedHash = params.get("vnp_SecureHash");
    if (!sig.verify(params, receivedHash)) {
        log.warn("VNPay IPN: invalid checksum (txnRef={})", txnRef);
        return IpnResponse.invalidChecksum();
    }
    if (maybe.isEmpty()) {
        log.warn("VNPay IPN: unknown txnRef={}", txnRef);
        return IpnResponse.orderNotFound();
    }
    Payment payment = maybe.get();

    // ... (rest unchanged; remove the redundant audit.record() calls
    //     in the 02/04/success paths since we now audit at the top)
}
```

If you want audit on truly unknown txnRefs too, add a V10 migration to make `payment_transaction.payment_id` nullable and adjust the entity, then audit unconditionally before any return. For the thesis demo, fixing it for known-txnRef + bad-sig is enough.

---

## Important findings

### IM-01: Telegram WebApp shows alert via `alert()` outside Telegram, breaking SSR/headless paths

**File:** `frontend/miniapp/src/features/payment/use-pay-with-vnpay.ts:27-31`

**Issue:** `onError` uses `alert(msg)` as a fallback when not running in Telegram. This is fine in a real browser but will break the Vitest unit test environment (jsdom has `alert` defined but throws or is unimplemented depending on setup). Same pattern exists in `OrderDetailPage.tsx:48-51`. This is consistent with the rest of the codebase, so flagging as Important rather than Critical, but consider adding a toast component instead.

**Suggested fix:** Use a project-wide toast util (e.g., `react-hot-toast`) for non-Telegram error UX. Or guard with `if (typeof window !== 'undefined' && window.alert)`.

### IM-02: PaymentExpiryScheduler will double-run on horizontally scaled deployments

**File:** `backend/modules/payment/src/main/java/com/shop/delivery/payment/service/PaymentExpiryScheduler.java:52`

**Issue:** `@Scheduled(fixedDelay = 60_000)` runs on every Spring Boot instance. In prod with N replicas, the sweep query runs N times every minute, and concurrent `saveAll(...)` could race (the `@Version` optimistic lock on `Payment` will make the second update fail, but that produces noisy `OptimisticLockingFailureException` logs).

This is **explicitly out of scope for the thesis** (single-instance demo), and the review brief itself flags it as deferred. Documenting here as Important so it's not lost when transitioning to multi-instance.

**Suggested fix (deferred to ops phase):** Use ShedLock (`net.javacrumbs.shedlock`) or a distributed lock backed by Postgres advisory locks. Pattern:
```java
@Scheduled(fixedDelay = 60_000)
@SchedulerLock(name = "expireStalePayments", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
public void expireStalePending() { ... }
```

### IM-03: `Long.parseLong(null)` masquerades as `NumberFormatException` — works today, fragile across JDKs

**File:** `backend/modules/payment/src/main/java/com/shop/delivery/payment/service/VnpayPaymentService.java:182`

**Issue:** `Long.parseLong(params.get("vnp_Amount"))` will pass `null` to `parseLong` if VNPay omits the `vnp_Amount` field (or an attacker does). On modern JDKs (8+) this throws `NumberFormatException: Cannot parse null string`, which is caught at line 183. But the JDK contract historically only guaranteed `NumberFormatException` for non-null inputs; relying on the implementation-specific null behaviour is fragile.

**Suggested fix:**
```java
String rawAmount = params.get("vnp_Amount");
if (rawAmount == null || rawAmount.isBlank()) {
    audit.record(payment.getId(), PaymentEventType.IPN, params);
    return IpnResponse.invalidAmount();
}
long received;
try { received = Long.parseLong(rawAmount); }
catch (NumberFormatException nfe) { /* same as today */ }
```

### IM-04: `BigDecimal.longValueExact()` throws on fractional amounts — IPN would 500

**File:** `backend/modules/payment/src/main/java/com/shop/delivery/payment/service/VnpayPaymentService.java:179`

**Issue:** `payment.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact()` — `longValueExact()` throws `ArithmeticException` if the result has a non-zero fractional part. The DB column is `NUMERIC(12,2)` so this should never happen in practice, but if a future migration or test seed stores `123.456`, the IPN handler crashes with an uncaught `ArithmeticException` → Spring returns HTTP 500 → VNPay treats as failed delivery and retries forever.

The controller contract states the IPN endpoint should **always** return HTTP 200; an uncaught exception breaks that contract.

**Suggested fix:** Either (a) catch `ArithmeticException` and return RspCode 04, or (b) drop `Exact` and use `longValue()` — but the latter silently truncates so prefer (a):
```java
long expected;
try {
    expected = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
} catch (ArithmeticException ae) {
    log.error("Payment {} has non-integer amount in VND-cents: {}", payment.getId(), payment.getAmount());
    audit.record(payment.getId(), PaymentEventType.IPN, params);
    return IpnResponse.invalidAmount();
}
```

### IM-05: `confirmAfterPayment` writes `payment_status=SUCCESS` even when already SUCCESS

**File:** `backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderService.java:172`

**Issue:** Line 172 unconditionally calls `order.setPaymentStatus(PaymentStatus.SUCCESS)`, then for already-CONFIRMED orders falls into the `else if (!isCancelled)` branch (line 184) which calls `orderRepo.save(order)`. JPA dirty-checking will detect no actual change and skip the UPDATE — but for the CANCELLED/RETURNED path (line 188-189), `payment_status` IS flipped from PENDING to SUCCESS even though the order is in a terminal state.

The comment at line 188 says "payment came late; flip flag for audit but no event/state change", which is intentional. But this means: a customer who cancelled then paid still gets `payment_status=SUCCESS`. The admin view will show "Hủy / Đã thanh toán" — confusing UX. Consider whether this should also trigger an automatic REFUND payment status flip, or at least surface a warning in the admin orders list.

**Suggested fix (defer for P8):** Add a `payment_pending_refund` flag or surface this case in the admin UI with a yellow warning badge. For now, document this in the runbook.

---

## Minor findings

### MN-01: `receivedHash.toLowerCase()` uses default locale

**File:** `backend/modules/payment/src/main/java/com/shop/delivery/payment/service/VnpaySignatureService.java:123`

**Issue:** `receivedHash.toLowerCase()` without an explicit Locale uses the JVM default. Hex chars `[0-9A-Fa-f]` are not affected by Turkish-I locale issue, so this works, but as a defensive coding practice prefer `toLowerCase(Locale.ROOT)`.

### MN-02: Vietnamese label phrasing inconsistent across pages

**Files:**
- `frontend/miniapp/src/pages/OrderDetailPage.tsx:172` — `'Đã thanh toán'`
- `frontend/webadmin/src/components/PaymentStatusBadge.tsx:5` — `'Thành công'`

**Issue:** Same `SUCCESS` status, two different Vietnamese labels. Pick one and use everywhere (suggest "Đã thanh toán" for both — it's clearer for end users). Similarly: `FAILED` is "Thanh toán thất bại" in miniapp but "Thất bại" in webadmin.

### MN-03: `orderCode` extraction from `txnRef` produces empty string on malformed input

**File:** `backend/modules/payment/src/main/java/com/shop/delivery/payment/service/VnpayPaymentService.java:240-242`

**Issue:** `txnRef.substring(0, txnRef.lastIndexOf('-'))` returns `""` when `txnRef` is `"-foo"` (starts with `-`). The redirect URL then contains `orderCode=`. Not a crash, just a degenerate display. Add a length-check guard if you care.

### MN-04: Hardcoded sandbox secret in `application.yml` default

**File:** `backend/app/src/main/resources/application.yml:65`

**Issue:** `hash-secret: ${VNPAY_HASH_SECRET:TESTSECRETKEY123}` — the fallback `TESTSECRETKEY123` matches the test fixture. Acceptable for sandbox/thesis but make sure prod profile (`application-prod.yml`) does NOT have this default. Verify by checking that profile.

### MN-05: `payment_transaction.raw_payload` stores params with potential PII

**File:** `backend/modules/payment/src/main/java/com/shop/delivery/payment/service/PaymentAuditRecorder.java:34`

**Issue:** The audit row stores the full VNPay param map as JSONB, including `vnp_OrderInfo` and `vnp_BankTranNo`. The current order info string is "Thanh toan don hang {orderCode}" which is benign, but if you ever include customer phone/name there, it ends up in audit. Worth noting in the data retention policy.

### MN-06: `findStalePending` JPQL fully-qualifies enum but uses unrelated module's package

**File:** `backend/modules/payment/src/main/java/com/shop/delivery/payment/repository/PaymentRepository.java:25`

**Issue:** The JPQL references `com.shop.delivery.order.domain.PaymentStatus.PENDING`. This works but is awkward — the `payment` module owns `Payment` but references an enum from `order.domain`. The cross-module enum sharing is fine (single source of truth), just ugly. Consider re-exporting via `shared` if `order.domain` becomes a name everyone has to know.

---

## What was done well

1. **`Propagation.REQUIRES_NEW` on `confirmAfterPayment` is correctly applied** (`OrderService.java:161`). The executor's mid-flight deviation fix landed in the committed code — verified.
2. **Module dependency direction is one-way** (`payment → order`, not vice versa). `grep -r "com.shop.delivery.payment" backend/modules/order/src/main/` returns empty. The cross-module flow goes through `shared.event.PaymentSucceededEvent`, which is the architecturally correct pattern.
3. **Signature verification is byte-correct**:
   - Strips both `vnp_SecureHash` and `vnp_SecureHashType` (line 98-99)
   - Sorts keys with `Collections.sort` (line 108)
   - Skips null/empty values (line 105)
   - Uses `MessageDigest.isEqual` for constant-time compare (line 121)
   - Handles uppercase-from-VNPay via `receivedHash.toLowerCase()` (line 123)
   - `US_ASCII` URL-encoding matches VNPay's official Java sample
4. **IPN idempotency is correct**: PENDING-check (line 172) ensures replays return `02` without DB writes or event publish — verified by `VnpayPaymentServiceTest.ipn_replayWhenAlreadySuccess_returns02_andAuditsButDoesNotMutate`.
5. **Endpoint contract**: `/ipn` is `permitAll` (line 42 of SecurityConfig), CSRF disabled globally (line 34), and the controller always returns JSON with `{"RspCode":"...","Message":"..."}` (verified by `IpnResponse` record's `@JsonProperty` annotations).
6. **Frontend redirect uses `WebApp.openLink`** (`use-pay-with-vnpay.ts:21`), NOT `window.location.href`. Plain-browser fallback uses `window.open(...)` with `noopener,noreferrer`. Both are correct.
7. **Polling has a 60s timeout** with a separate `pollingExpired` state showing a user-friendly fallback message — better UX than infinite polling.
8. **The `@TransactionalEventListener(AFTER_COMMIT)` annotation is correct** (`PaymentEventListener.java:31`). Combined with `REQUIRES_NEW` on the order side, this avoids the dirty-write race documented in the research notes.
9. **Hashing secret is masked in `VnpayProperties.toString()`** (line 27); no log line writes the secret anywhere I could find via grep.
10. **15 unit tests + 2 integration tests** cover the happy path, replay, signature tampering, amount tampering, ownership rejection, COD rejection, already-paid rejection, expiry sweeper, and full IPN→order flow via `MockMvc`. Test quality is high.
11. **`Clock` injection** is consistent across `VnpayPaymentService` and `PaymentExpiryScheduler` — enables deterministic tests with `Clock.fixed`.
12. **V9 migration has appropriate constraints**: `CHECK (amount >= 0)`, `UNIQUE` on `vnp_txn_ref`, `ON DELETE CASCADE` for transaction → payment FK, indexes on `(status, created_at)` for the sweeper query.
13. **Static return pages use `textContent`, not `innerHTML`** — no XSS risk even if VNPay sent crafted `orderCode` query params.
14. **`PaymentEventListener` wraps `orderService.confirmAfterPayment` in `try/catch`** and logs loudly on failure — prevents AFTER_COMMIT handler exceptions from corrupting the IPN ack response.

---

_Reviewed: 2026-06-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
