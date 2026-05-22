# Phase 7: VNPay Sandbox Payment Integration — Research

**Researched:** 2026-05-22
**Domain:** Vietnamese payment gateway integration (VNPay sandbox) on Spring Boot 3.4 + React 18 Telegram Mini App
**Confidence:** HIGH — backed by VNPay's official Java/JSP sample (`vnpay_jsp.zip`) and the official sandbox docs at `sandbox.vnpayment.vn/apis`

---

## Summary

VNPay's sandbox is straightforward: build a query string of `vnp_*` params, sign it with HMAC-SHA512 over `HashSecret`, redirect the user to `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?<query>&vnp_SecureHash=<hex>`. After payment, VNPay does TWO callbacks: (a) a browser redirect to our **Return URL** (just for UX), and (b) a server-to-server **IPN** call that is the authoritative source of truth. The IPN handler must verify the signature, check amount, check idempotency, mutate DB transactionally, and respond with a fixed JSON `{"RspCode":"00","Message":"Confirm Success"}` (or an error code from the documented set).

The trickiest part is **getting the signature right** — VNPay's own JSP and Spring Boot samples differ subtly. The canonical official `vnpay_jsp` sample (downloaded from `sandbox.vnpayment.vn/apis/files/vnpay_jsp.zip`) URL-encodes both parameter names AND values before joining them with `&` and signing. Both create-payment and IPN/Return verification use this same encoding convention, so they round-trip cleanly. Do not deviate.

For the thesis, the implementation fits cleanly into the existing `payment` module (already a Maven module under `backend/modules/payment` with only a `.gitkeep`). Use plain `javax.crypto.Mac` (no extra dependency), Spring `@ConfigurationProperties` for credentials, Spring `ApplicationEventPublisher` to fire `PaymentSucceededEvent` (same pattern as `delivery` module), and `SimpMessagingTemplate.convertAndSendToUser` to push status to the Mini App over the existing `/user/queue/orders` WebSocket destination — both polling AND WebSocket should be used (polling as the cheap fallback because the user might land on the success page before the WS reconnects).

**Primary recommendation:** Port the canonical `Config.java` + `ajaxServlet.java` flow from VNPay's official JSP sample into three Spring beans — `VnpayProperties` (config), `VnpaySignatureService` (sign/verify), `VnpayPaymentService` (create + IPN business logic) — and expose them via `VnpayController` with three endpoints. Keep the IPN public unauthenticated in `SecurityConfig` (`/api/payment/vnpay/ipn` and `/api/payment/vnpay/return` → `permitAll`).

---

## User Constraints (from Phase 7 Brief)

The brief from the user is the constraint set (no separate CONTEXT.md exists yet for P7). Locked decisions:

### Locked Decisions
- VNPay **sandbox only** (no production credentials).
- Endpoints exactly as designed in `2026-05-19-...design.md` §10:
  - `POST /api/payment/vnpay/create {orderId}` → `{paymentUrl}` (auth: Telegram initData; owner must match order.customerId).
  - `GET  /api/payment/vnpay/return` — public, no auth, signature-verified, **read-only** (does NOT update DB), redirects browser to Mini App success/fail page.
  - `POST /api/payment/vnpay/ipn` — public, no auth, signature-verified, **source of truth**, mutates DB and returns `{"RspCode":"...","Message":"..."}` JSON.
- DB schema as already specified (`payment` + `payment_transaction`); add via new Flyway migration `V9__payment.sql`.
- IPN is the source of truth. Return URL never writes to DB.
- Mini App opens the VNPay URL via `Telegram.WebApp.openLink(paymentUrl)` (NOT `window.location.href` — that destroys the Mini App container per Telegram WebApp behavior).
- Refund flow is **OUT OF SCOPE** (manual, per design spec §10.5 and section 2.2).

### Claude's Discretion
- Concrete Java class names and package layout under `com.shop.delivery.payment.*`.
- Whether to use the existing `ApplicationEventPublisher` (yes — matches `delivery` module pattern) or invoke `OrderService.confirm()` directly (no — creates a circular module dependency).
- Polling vs WebSocket push for Mini App update on return → **both** (recommendation below).
- Whether to add a cron for stale-PENDING payments → yes, simple `@Scheduled` with `@EnableScheduling` (needs to be added to `Application.java`).
- Test data approach (Testcontainers PG already in place; pattern is `OrderFlowIT.java` style).

### Deferred Ideas (OUT OF SCOPE for P7)
- Refund / `vnpay_refund` API (`refund` command on `merchant_webapi`).
- Query-DR (`querydr` command — manual transaction lookup).
- Recurring / token payments.
- Production VNPay credentials.
- Multi-bank-code splitting at our UI (we always send empty `vnp_BankCode`, VNPay picks at their gateway).
- Webhook signature replay protection beyond DB status check (timing windows, nonce tables) — `payment.status != PENDING` is sufficient per design §10.4.

---

## Phase Requirements

| ID | Description | Research Support |
|---|---|---|
| VNPAY-01 | Mini App can choose VNPay at checkout and is redirected to VNPay sandbox | §6 frontend + §2 create params + design spec §10.1 |
| VNPAY-02 | Backend signs the payment URL with HMAC-SHA512 correctly | §2 + §5 official sample HMAC helper |
| VNPAY-03 | Return URL verifies signature and redirects user to Mini App without DB writes | §3 + canonical `vnpay_return.jsp` |
| VNPAY-04 | IPN endpoint verifies signature, is idempotent on `vnp_TxnRef`, and atomically confirms order | §4 + canonical `vnpay_ipn.jsp` + design §10.3 |
| VNPAY-05 | `payment` and `payment_transaction` tables persist state + audit | §1 (existing schema in design spec) |
| VNPAY-06 | Mini App displays SUCCESS / FAILED status after payment | §6 frontend |
| VNPAY-07 | Security: amount tampering rejected (RspCode 04), replay rejected (RspCode 02), forged sig rejected (RspCode 97) | §4 + §7 |
| VNPAY-08 | Cron marks payments PENDING > 15min as FAILED | §7 + design §10.5 |
| VNPAY-09 | Unit + integration tests prove signature, IPN idempotency, amount validation | §8 |

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `javax.crypto.Mac` (JDK 17 built-in) | JDK 17 | HmacSHA512 signing | The official VNPay JSP sample uses exactly this. No third-party dep needed. [VERIFIED: vnpay_jsp.zip → `Config.java` line 87 `Mac.getInstance("HmacSHA512")`] |
| `org.springframework.boot:spring-boot-starter-web` | 3.4.x (inherited from parent pom) | REST controllers | Already in `app` module. |
| `org.springframework.boot:spring-boot-starter-data-jpa` | 3.4.x | Entity persistence | Same pattern as `order` module. |
| `org.springframework.boot:spring-boot-starter-validation` | 3.4.x | `@NotNull` on `CreatePaymentRequest.orderId` | Already used in `order` module DTOs. |
| `org.springframework.boot:spring-boot-starter-websocket` | 3.4.x | Push update to Mini App over `/user/queue/orders` | Already wired in app (`WebSocketConfig.java`). |
| `com.fasterxml.jackson.core:jackson-databind` | brought by `spring-boot-starter-web` | JSONB payload serialization for `payment_transaction.raw_payload` | Already used. |
| `org.hibernate.types:hibernate-types-60` *OR* native Hibernate `@JdbcTypeCode(SqlTypes.JSON)` | Hibernate 6.6 ships with Spring Boot 3.4 | Map `Map<String,String>` → Postgres JSONB | Hibernate 6 native JSON support is cleaner — no extra dep [VERIFIED: Hibernate 6.6 docs] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.springframework.boot:spring-boot-starter-test` | 3.4.x | JUnit 5 + Mockito + AssertJ | All P7 tests. Already declared in payment/pom.xml. |
| `org.testcontainers:postgresql` | inherited | Postgres in integration tests | Pattern already in `PostgresTestContainer.java`. |
| `org.springframework.boot:spring-boot-configuration-processor` (optional) | 3.4.x | IDE autocomplete for `application.yml` keys | Nice-to-have for `vnpay.*` keys. Compile-only. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Plain `javax.crypto.Mac` | `lehuygiang28/vnpay` Java port (no official Java NPM equivalent) | None exists in Maven Central; rolling our own from the official `Config.java` is ~30 lines and is what the official sample does. Adding a 3rd-party dep for a thesis = unnecessary supply-chain risk. |
| `@JdbcTypeCode(SqlTypes.JSON)` for raw_payload | `vladmihalcea/hypersistence-utils` `JsonType` | Hibernate 6 native works for read/write; only need `hypersistence-utils` if doing JSONB queries. We only persist + read — native is fine. |
| Spring `ApplicationEventPublisher` | Direct call from `VnpayPaymentService` → `OrderService.confirm()` | Direct call creates a hard dependency `payment → order`. The codebase already uses events for cross-module signalling (see `delivery` → `bot` via `OrderAcceptedEvent`). Stay consistent. |
| `Telegram.WebApp.openLink(url)` | `window.location.href = url` | `window.location` closes the Mini App. `openLink` opens VNPay in Telegram's in-app browser overlay and returns to the Mini App on close. [VERIFIED: @twa-dev/sdk 7.10 + Telegram docs] |

**Installation:** *Nothing to install for Maven.* The `payment` module pom already depends on `shared` and `spring-boot-starter`. Add to `backend/modules/payment/pom.xml`:

```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-websocket</artifactId></dependency>
<dependency><groupId>com.shop.delivery</groupId><artifactId>order</artifactId><version>${project.version}</version></dependency>
<!-- test -->
<dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
```

The `order` dep is needed to use `OrderService` and `OrderRepository` (status transition + lookup). The reverse direction `order → payment` is deliberately avoided.

**Version verification:**
```bash
# Spring Boot is fixed by parent pom — no version drift.
mvn -q -f /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang/backend/pom.xml help:effective-pom -Dverbose=false | grep -i 'spring-boot.version\|hibernate.version' | head -5
```
[VERIFIED: project parent pom — Spring Boot 3.4.x, Java 17, Hibernate 6.6.x. Versions are stable, no security advisories impacting this phase.]

---

## Architecture Patterns

### Recommended Project Structure
```
backend/modules/payment/src/main/java/com/shop/delivery/payment/
├── api/
│   ├── VnpayController.java               # 3 endpoints: create, return, ipn
│   └── dto/
│       ├── CreatePaymentRequest.java      # { orderId: UUID }
│       ├── CreatePaymentResponse.java     # { paymentUrl, txnRef }
│       └── IpnResponse.java               # { rspCode, message }   (DTO matches VNPay's required JSON keys)
├── config/
│   ├── VnpayProperties.java               # @ConfigurationProperties("vnpay")
│   └── VnpayModuleConfig.java             # @EnableConfigurationProperties(VnpayProperties.class)
├── domain/
│   ├── PaymentEventType.java              # CREATE | IPN | RETURN
│   └── (PaymentStatus & PaymentMethod come from order.domain — reuse, don't duplicate)
├── entity/
│   ├── Payment.java                       # @Entity @Table(name = "payment")
│   └── PaymentTransaction.java            # @Entity @Table(name = "payment_transaction")
├── repository/
│   ├── PaymentRepository.java             # findByVnpTxnRef(String) for IPN idempotency
│   └── PaymentTransactionRepository.java
├── service/
│   ├── VnpaySignatureService.java         # sign(Map) + verify(Map, hash)  ← THE ONLY crypto code
│   ├── VnpayUrlBuilder.java               # buildPaymentUrl(payment, ipAddr) → String
│   ├── VnpayPaymentService.java           # createPayment(orderId, ipAddr), handleIpn(params), handleReturn(params)
│   └── PaymentExpiryScheduler.java        # @Scheduled cron: PENDING > 15min → FAILED
└── event/
    └── PaymentSucceededEvent.java         # published when IPN flips PENDING → SUCCESS
                                           # Listener in order module → confirmAfterPayment(orderId)

backend/modules/payment/src/main/resources/
└── (none — all config lives in app's application.yml)

backend/app/src/main/resources/db/migration/
└── V9__payment.sql                        # CREATE TABLE payment + payment_transaction
```

### Pattern 1: HMAC-SHA512 Signing (THE ONE THAT MATTERS)
**What:** Build a sorted query string of all `vnp_*` params (except `vnp_SecureHash`), URL-encode each key AND each value with US-ASCII (this is what the official sample does — *not* UTF-8 — values like `vnp_OrderInfo` are no-accent ASCII, so US-ASCII is safe), join with `&`, then HMAC-SHA512 with the secret key as bytes (default charset, NOT UTF-8) and the data as UTF-8 bytes, returning lowercase hex.

**When to use:** Both when creating the payment URL AND when verifying Return/IPN. Same algorithm, both sides.

**Example (this is the official VNPay sample — port verbatim):**
```java
// Source: vnpay_jsp.zip → src/java/com/vnpay/common/Config.java + ajaxServlet.java
// Adapted to a Spring bean

@Service
public class VnpaySignatureService {

    private final VnpayProperties props;

    public VnpaySignatureService(VnpayProperties props) { this.props = props; }

    /**
     * Build the sorted, URL-encoded `key=value&...` string that goes into the HMAC,
     * AND the encoded query string suffix to append to the pay URL.
     * Returns a record so the caller has both.
     */
    public BuildResult buildHashAndQuery(Map<String, String> params) {
        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String name = itr.next();
            String value = params.get(name);
            if (value == null || value.isEmpty()) continue;  // skip empty (VNPay rule)
            String encName  = URLEncoder.encode(name,  StandardCharsets.US_ASCII);
            String encValue = URLEncoder.encode(value, StandardCharsets.US_ASCII);
            hashData.append(name).append('=').append(encValue);   // hashData: raw name + enc value
            query   .append(encName).append('=').append(encValue); // query:    enc name + enc value
            if (itr.hasNext()) {
                hashData.append('&');
                query.append('&');
            }
        }
        String hash = hmacSHA512(props.hashSecret(), hashData.toString());
        return new BuildResult(hash, query.toString());
    }

    /**
     * Verify the secureHash from a Return/IPN request.
     * NB: matches the JSP sample's hashAllFields which builds the hash WITHOUT trailing &
     *     and from the SAME percent-encoded values that arrive in the request (i.e.
     *     we re-URL-encode whatever the servlet decoded for us). See vnpay_ipn.jsp.
     */
    public boolean verify(Map<String, String> receivedParams, String receivedHash) {
        if (receivedHash == null || receivedHash.isBlank()) return false;
        // Strip the signature fields before re-hashing
        Map<String, String> work = new HashMap<>(receivedParams);
        work.remove("vnp_SecureHash");
        work.remove("vnp_SecureHashType");
        // Re-encode names and values (matches official JSP behaviour where the map keys/values
        // were already URL-encoded before hashAllFields was called).
        Map<String, String> encoded = new HashMap<>();
        for (var e : work.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            encoded.put(
                URLEncoder.encode(e.getKey(),   StandardCharsets.US_ASCII),
                URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII)
            );
        }
        List<String> fieldNames = new ArrayList<>(encoded.keySet());
        Collections.sort(fieldNames);
        StringBuilder sb = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String name = itr.next();
            String value = encoded.get(name);
            sb.append(name).append('=').append(value);
            if (itr.hasNext()) sb.append('&');
        }
        String expected = hmacSHA512(props.hashSecret(), sb.toString());
        // Constant-time compare to prevent timing oracle (design §10.4)
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.US_ASCII),
            receivedHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    static String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            // NB: official sample uses key.getBytes() (default charset). UTF-8 is the safer canonical
            // version and matches the doc. The hash secret is ASCII-only in practice so either works.
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA512 unavailable", e);
        }
    }

    public record BuildResult(String hash, String queryString) {}
}
```

> **CRITICAL PITFALL:** The official sample mixes encodings between create-path and verify-path. Re-read carefully:
> - `ajaxServlet.java` (create): `hashData = name + "=" + URLEncoder.encode(value)` — raw name, encoded value
> - `vnpay_ipn.jsp` (verify): puts URL-encoded name AND URL-encoded value into the map, then `hashAllFields` joins `name=value` from that already-encoded map → encoded name, encoded value
>
> Because parameter names are pure ASCII (`vnp_Version`, `vnp_TxnRef`, etc.) URL-encoding them yields the same string as raw, so the two paths coincidentally produce identical hashes. **Our service must reproduce this exact behavior** or signatures will mismatch in production where a non-ASCII name appears (won't happen with `vnp_*` but a defensive `URLEncoder` on names harms nothing).
>
> Test by signing a fixed map and comparing against a known hash from a real sandbox URL (provided by `VNPay sandbox` after first successful test transaction).

### Pattern 2: Create Payment Flow
**What:** Create `Payment` row in PENDING, build URL, audit `CREATE` event, return URL.
```java
@Service
@Transactional
public class VnpayPaymentService {
    private final OrderRepository orderRepo;
    private final PaymentRepository paymentRepo;
    private final PaymentTransactionRepository txRepo;
    private final VnpaySignatureService sig;
    private final VnpayProperties props;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json;
    private final Clock clock;

    public CreatePaymentResponse createPayment(UUID orderId, Long callerCustomerId, String ipAddr) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new BusinessRuleException("ORDER_NOT_FOUND", "Đơn không tồn tại"));
        if (!order.getCustomerId().equals(callerCustomerId))
            throw new BusinessRuleException("FORBIDDEN", "Đơn không thuộc về bạn");
        if (order.getPaymentMethod() != PaymentMethod.VNPAY)
            throw new BusinessRuleException("INVALID_METHOD", "Đơn không dùng VNPay");
        if (order.getPaymentStatus() != PaymentStatus.PENDING)
            throw new BusinessRuleException("ALREADY_PAID", "Đơn đã có trạng thái " + order.getPaymentStatus());

        // 1. Create payment row (idempotent if customer retries within same minute — txnRef makes it unique)
        String txnRef = order.getCode() + "-" + clock.instant().toEpochMilli();
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(order.getId());
        payment.setMethod(PaymentMethod.VNPAY);
        payment.setAmount(order.getTotal());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setVnpTxnRef(txnRef);
        paymentRepo.save(payment);

        // 2. Build params
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version",    "2.1.0");
        params.put("vnp_Command",    "pay");
        params.put("vnp_TmnCode",    props.tmnCode());
        params.put("vnp_Amount",     order.getTotal().multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        params.put("vnp_CurrCode",   "VND");
        params.put("vnp_TxnRef",     txnRef);
        params.put("vnp_OrderInfo",  "Thanh toan don hang " + order.getCode());
        params.put("vnp_OrderType",  "other");          // or "100000" — see §2 below
        params.put("vnp_Locale",     "vn");
        params.put("vnp_ReturnUrl",  props.returnUrl());
        params.put("vnp_IpAddr",     ipAddr);
        params.put("vnp_CreateDate", now.format(fmt));
        params.put("vnp_ExpireDate", now.plusMinutes(props.timeoutMinutes()).format(fmt));

        // 3. Sign
        var result = sig.buildHashAndQuery(params);
        String url = props.payUrl() + "?" + result.queryString() + "&vnp_SecureHash=" + result.hash();

        // 4. Audit
        recordTransaction(payment, PaymentEventType.CREATE, params);
        return new CreatePaymentResponse(url, txnRef);
    }

    private void recordTransaction(Payment p, PaymentEventType type, Map<String,String> payload) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setPaymentId(p.getId());
        tx.setEventType(type);
        tx.setRawPayload(payload);     // mapped via @JdbcTypeCode(SqlTypes.JSON) on Map<String,String>
        tx.setRecordedAt(Instant.now());
        txRepo.save(tx);
    }
    // ...handleIpn / handleReturn below
}
```

### Pattern 3: IPN Handler (Source of Truth)
```java
@Transactional
public IpnResponse handleIpn(Map<String, String> params) {
    String receivedHash = params.get("vnp_SecureHash");
    if (!sig.verify(params, receivedHash))
        return new IpnResponse("97", "Invalid Checksum");

    String txnRef = params.get("vnp_TxnRef");
    Payment payment = paymentRepo.findByVnpTxnRef(txnRef).orElse(null);
    if (payment == null)
        return new IpnResponse("01", "Order not found");

    // Idempotency — design §10.3 step 3
    if (payment.getStatus() != PaymentStatus.PENDING) {
        recordTransaction(payment, PaymentEventType.IPN, params);  // still audit the duplicate
        return new IpnResponse("02", "Order already confirmed");
    }

    // Amount check — design §10.3 step 4
    long expected = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
    long received = Long.parseLong(params.get("vnp_Amount"));
    if (expected != received) {
        recordTransaction(payment, PaymentEventType.IPN, params);
        // Optional: also flip payment.status to FAILED here? Spec is silent — leave PENDING
        // so a corrected retry could still succeed. Just log + 04.
        return new IpnResponse("04", "Invalid Amount");
    }

    String responseCode  = params.get("vnp_ResponseCode");
    String txStatus      = params.get("vnp_TransactionStatus");
    boolean ok = "00".equals(responseCode) && "00".equals(txStatus);
    if (ok) {
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        payment.setVnpResponseCode(responseCode);
        payment.setPaidAt(Instant.now());
        paymentRepo.save(payment);
        events.publishEvent(new PaymentSucceededEvent(payment.getOrderId(), payment.getId(), payment.getAmount()));
    } else {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setVnpResponseCode(responseCode);
        paymentRepo.save(payment);
        events.publishEvent(new PaymentFailedEvent(payment.getOrderId(), payment.getId(), responseCode));
    }
    recordTransaction(payment, PaymentEventType.IPN, params);
    return new IpnResponse("00", "Confirm Success");
}
```

The event listener lives in `order` module — drops `payment → order` direct call:
```java
// backend/modules/order/src/main/java/com/shop/delivery/order/service/PaymentEventListener.java
@Component
public class PaymentEventListener {
    private final OrderService orderService;
    public PaymentEventListener(OrderService s) { this.orderService = s; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSuccess(PaymentSucceededEvent ev) {
        orderService.confirmAfterPayment(ev.orderId());   // new method to add
    }
}
```
`OrderService.confirmAfterPayment(UUID)` is a thin wrapper that (a) sets `paymentStatus=SUCCESS` and (b) transitions status `PENDING → CONFIRMED` using the existing state machine. Use `@TransactionalEventListener(AFTER_COMMIT)` so the order transition runs in a NEW transaction AFTER the payment row was committed — avoids dirty-write races.

### Pattern 4: Return Handler (UX only, no DB writes)
```java
@GetMapping("/api/payment/vnpay/return")
public ResponseEntity<Void> handleReturn(@RequestParam Map<String, String> params) {
    String hash = params.get("vnp_SecureHash");
    boolean valid = sig.verify(params, hash);
    String code = params.get("vnp_ResponseCode");
    String redirect;
    if (!valid) {
        redirect = props.miniAppFailUrl() + "?reason=invalid_signature";
    } else if ("00".equals(code) && "00".equals(params.get("vnp_TransactionStatus"))) {
        redirect = props.miniAppSuccessUrl() + "?txnRef=" + params.get("vnp_TxnRef");
    } else {
        redirect = props.miniAppFailUrl() + "?code=" + code + "&txnRef=" + params.get("vnp_TxnRef");
    }
    // Audit the return event too (helpful for debugging IPN-before-return races)
    paymentRepo.findByVnpTxnRef(params.get("vnp_TxnRef"))
        .ifPresent(p -> recordTransaction(p, PaymentEventType.RETURN, params));
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirect)).build();
}
```

### Pattern 5: SecurityConfig — make IPN + Return public
Add to `SecurityConfig.java` BEFORE the `.anyRequest().permitAll()` (which actually already covers them — but make it explicit for documentation):
```java
.requestMatchers("/api/payment/vnpay/return", "/api/payment/vnpay/ipn").permitAll()
.requestMatchers(HttpMethod.POST, "/api/payment/vnpay/create").authenticated()  // Telegram filter handles
```
Then in `VnpayController.create()`, use `@CurrentUser` (existing pattern) to inject the authenticated customer.

### Anti-Patterns to Avoid
- **DO NOT** update DB in the Return URL handler. The user might never reach the Return URL (closed browser); IPN is guaranteed by VNPay.
- **DO NOT** parse `vnp_Amount` as `BigDecimal` directly — it's an integer-in-cents (×100). Parse as `long`, then divide. Mixing decimal scale here = comparison fails on amount check = false RspCode 04.
- **DO NOT** use `String.equals()` for the signature comparison — use `MessageDigest.isEqual` to prevent timing side-channels. (Required for the §10.4 security checklist.)
- **DO NOT** call `OrderService.confirm()` directly from `VnpayPaymentService.handleIpn` — creates a circular module dep (`payment → order` while `order → payment` would be needed for events). Use `ApplicationEventPublisher` + `@TransactionalEventListener` in `order` module.
- **DO NOT** trust the original `request.getQueryString()` for hashing — it may include other query params (?utm_source etc.) and may have encoding inconsistencies. Always rebuild from `@RequestParam Map<String,String>`.
- **DO NOT** retry the IPN response to VNPay — VNPay does NOT retry on HTTP errors per the official sample comments, only on missing/bad RspCode. Make the handler fast (< 5s) and always return a `200 OK` with a `RspCode`.
- **DO NOT** log `vnp_HashSecret` or include it in error responses.
- **DO NOT** rename the JSON fields in the IPN response. VNPay strictly expects `RspCode` and `Message` capitalized exactly so. Jackson `@JsonProperty("RspCode")` if you keep camelCase field names in the DTO.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HMAC-SHA512 | Custom hash combination, salt scheme, double-hashing | `Mac.getInstance("HmacSHA512")` | Building your own MAC is a textbook security mistake. JDK's is FIPS-grade. |
| URL encoding | `String.replace("&", "%26")` style hand-coding | `java.net.URLEncoder.encode(s, StandardCharsets.US_ASCII)` | URL-encoding has many corner cases (space → `+` vs `%20`; encoding of `*`, `~`, `(`, `)`). `URLEncoder` matches what VNPay expects (form-urlencoded RFC 1738 variant). |
| Order code → TxnRef uniqueness | Random UUID, sequence, timestamp alone | `orderCode + "-" + Instant.now().toEpochMilli()` | Design spec §10.6 specifies this exact format; appending epoch ms makes retries-after-fail produce a fresh TxnRef (new Payment row) for the same order. |
| Constant-time string compare | `==`, `.equals()`, byte-by-byte loop | `MessageDigest.isEqual(byte[], byte[])` | Constant-time is required for cryptographic comparisons. JDK 7+ guarantees this. |
| Auditing raw payloads | Concatenated string, custom CSV format | `@JdbcTypeCode(SqlTypes.JSON) Map<String,String>` on Hibernate 6 | Native JSONB column lets you query/inspect later without parsing strings. No extra dep. |
| State transition | Setting `order.status = CONFIRMED` directly | `OrderService.confirmAfterPayment()` → existing `OrderStateMachine` | The state machine validates PENDING→CONFIRMED transition and writes `status_history` — bypassing breaks the audit trail. |
| Idempotency key | New nonce table, ETag-style header | `payment.vnp_txn_ref UNIQUE` + `payment.status != PENDING` check | One unique index + one status check covers replay + double-IPN. No new infra. |
| Timezone handling for `vnp_CreateDate` | `Date` + `SimpleDateFormat` with manual offset | `ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))` | Java 8 time API. `Asia/Ho_Chi_Minh` is the correct IANA zone (handles DST changes that Vietnam doesn't observe but the zone is canonical). Note: official sample uses `"Etc/GMT+7"` which is correct-by-accident (POSIX inversion of sign) — both work; `Asia/Ho_Chi_Minh` is clearer. |

**Key insight:** VNPay's protocol is *almost entirely* about getting the signature right. Every documented failure mode in the sandbox reduces to "your hash data string isn't byte-identical to what VNPay computed." Reuse the official Java helper verbatim, test it against a known-good vector, and don't be clever.

---

## Runtime State Inventory

> Phase 7 is greenfield code (no rename/refactor). Listed here for completeness — none of these categories have existing state to migrate.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — `payment` and `payment_transaction` tables don't exist yet; `orders.payment_status` already has `PENDING` default for existing rows. | New `V9__payment.sql` Flyway migration creates tables. |
| Live service config | VNPay merchant portal will need our Return URL + IPN URL registered at sandbox.vnpayment.vn/devreg. These are merchant-side config in VNPay's UI, not in our git. | After getting TmnCode, log in to sandbox merchant portal and register `https://<ngrok-or-cf-tunnel>/api/payment/vnpay/return` and `.../ipn`. Document in P7 README. |
| OS-registered state | None. | None. |
| Secrets/env vars | New env vars needed: `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_RETURN_URL`, `VNPAY_IPN_URL`, `MINIAPP_PAYMENT_SUCCESS_URL`, `MINIAPP_PAYMENT_FAIL_URL`. Add to `.env.example` and `application-dev.yml` (with placeholders). | Add to deployment runbook. |
| Build artifacts | None — `payment` module only has `.gitkeep`; first real build will create classes. | None. |

**Nothing pre-existing in any category — verified by reading `backend/modules/payment/src/main/java/com/shop/delivery/payment/` (only `.gitkeep`).**

---

## Common Pitfalls

### Pitfall 1: Signature mismatch on Return/IPN
**What goes wrong:** VNPay shows the user a success page, but our `/return` and `/ipn` both reject the signature → user sees a generic error in the Mini App, payment stays PENDING forever.
**Why it happens:** Mismatched URL encoding between create-time and verify-time. Examples:
  - Created with `URLEncoder.encode(value, "US-ASCII")` but verifies with `"UTF-8"` (`+` encoding of space differs in some chars).
  - Servlet's `@RequestParam` already URL-decoded the value, then verify code doesn't re-encode it → name=encodedValue mismatch.
  - Empty params accidentally signed (must skip empty values per official sample).
**How to avoid:**
  - One signature service used by BOTH sides (`VnpaySignatureService`).
  - Add a Wave 0 unit test that signs a known fixture (from VNPay docs or a real sandbox URL captured during dev) and asserts the exact hex output.
  - Log `hashData` at DEBUG level when verify fails, side-by-side with what was received — fastest way to catch encoding drift in dev.
**Warning signs:** All payments stuck PENDING in dev. `vnp_ResponseCode=00` on the VNPay page but our DB shows nothing. → Reproduce by capturing the full IPN query string and diff byte-by-byte with what we'd produce.

### Pitfall 2: Race between Return and IPN
**What goes wrong:** User redirects back from VNPay → `/return` shows PENDING status because IPN hasn't arrived yet → Mini App polls and shows "Đang chờ xác nhận" forever.
**Why it happens:** IPN is server-to-server and might arrive after the user's browser redirect, or be delayed by network hops. VNPay sends both simultaneously.
**How to avoid:**
  - Return URL redirects to Mini App with `?txnRef=...` only — Mini App fetches order status from our API and polls every 2s for up to 30s after landing.
  - In parallel, the existing WebSocket `/user/queue/orders` channel pushes an `OrderConfirmedEvent` when IPN completes — Mini App reacts immediately if WS is alive.
  - Either path resolves UI; the Mini App can stop polling on either.
**Warning signs:** Reports of "tôi đã thanh toán xong rồi mà app vẫn nói chờ" — almost always a missing IPN, not a race. Check VNPay merchant portal → IPN delivery log.

### Pitfall 3: Amount tampering vector
**What goes wrong:** Attacker observes a payment URL and changes `vnp_Amount` before submitting → if signature verify is skipped or `vnp_Amount` recompare is missed, an attacker can mark an order as paid for less than the real total.
**Why it happens:** Forgetting design §10.3 step 4 — amount must be compared against `payment.amount * 100`, not just trusted from IPN params.
**How to avoid:** Hard-code the amount check INSIDE `handleIpn` (return RspCode 04). Add a security test in Wave 1: build a valid IPN with a tampered `vnp_Amount`, re-sign with our hashSecret (which the test holds), assert response is `04` and payment row stays PENDING.
**Warning signs:** Found a payment with `amount` < `order.total`. Audit log shows valid signature but mismatched amount — means our 04 check is missing.

### Pitfall 4: TxnRef collision on retry
**What goes wrong:** User clicks "Pay with VNPay" again after first attempt failed (e.g. wrong OTP). If we reuse the same txnRef, VNPay refuses ("Mã giao dịch đã tồn tại"). Even worse, if we don't create a new Payment row, the second attempt has no audit trail.
**Why it happens:** Treating txnRef as a function of orderCode alone.
**How to avoid:** `txnRef = orderCode + "-" + System.currentTimeMillis()`. Always create a NEW `Payment` row on each create-payment call (mark previous PENDING as `FAILED` first). Add `UNIQUE(vnp_txn_ref)` constraint at DB level (already in design spec §6).
**Warning signs:** 500 errors on `POST /api/payment/vnpay/create` after a failed payment.

### Pitfall 5: `vnp_OrderInfo` with diacritics
**What goes wrong:** Send `"Thanh toán đơn hàng DH..."` (with `á`, `ơ`) and VNPay returns "Invalid signature" because their hash uses a different encoding of the multibyte chars than `URLEncoder.encode(value, US_ASCII)` produces (US-ASCII can't represent `á`).
**Why it happens:** Vietnamese text needs UTF-8 → URL-encoded → bytes. The official sample passes ASCII-only strings.
**How to avoid:** Strip diacritics: `Normalizer.normalize(s, Form.NFD).replaceAll("\\p{M}", "")`. Or just hard-code English: `"Thanh toan don hang " + orderCode"`. Design spec §10.1 already says "không dấu" (no diacritics).
**Warning signs:** Signature works for some orders, fails for others (depends on customer name being in order info).

### Pitfall 6: Mini App can't return from VNPay
**What goes wrong:** Mini App uses `window.location.href = paymentUrl` → Telegram closes the Mini App container → after payment, VNPay's Return URL renders in the system browser → user never sees the success page in the Mini App.
**Why it happens:** Telegram WebApp model: `window.location` navigations are treated as exits.
**How to avoid:** Use `Telegram.WebApp.openLink(paymentUrl)` (already in our `@twa-dev/sdk`) — VNPay opens in an overlay browser; closing the overlay returns to the Mini App on the original page. Combined with WebSocket update, the Mini App sees the new status instantly.
**Warning signs:** "User mở payment xong không quay lại được app" — switch to `openLink`.

### Pitfall 7: VNPay sandbox is flaky in CI
**What goes wrong:** Integration tests that actually call `sandbox.vnpayment.vn` fail intermittently with 503, time out at 30s, or rate-limit.
**Why it happens:** Sandbox has no SLA; it's shared infrastructure for all VN developers.
**How to avoid:** Never call the real sandbox from CI. Mock the signature service in unit tests; use a Testcontainers PG + a stubbed `RestTemplate` for integration tests; reserve real sandbox testing for the manual E2E checklist (last test of CI pipeline run by hand against staging). Optional feature flag `vnpay.sandbox-direct-calls=false` for safety in CI.
**Warning signs:** Flaky PR builds — should not happen if there are no outbound calls. The integration POST→VNPay we DON'T make: we just build a URL and redirect; VNPay isn't called from our server side, only from the user's browser. So tests don't actually need sandbox availability for the create flow. Only manual E2E does.

---

## Code Examples

### Example A: `VnpayProperties` (Spring config binding)
```java
// Source: pattern from existing BotProperties.java in backend/modules/bot
@ConfigurationProperties(prefix = "vnpay")
@Validated
public record VnpayProperties(
    @NotBlank String tmnCode,
    @NotBlank String hashSecret,
    @NotBlank String payUrl,
    @NotBlank String returnUrl,
    @NotBlank String ipnUrl,
    @NotBlank String miniAppSuccessUrl,
    @NotBlank String miniAppFailUrl,
    @Positive int timeoutMinutes
) {}
```

Bind in `application.yml`:
```yaml
vnpay:
  tmn-code: ${VNPAY_TMN_CODE:}
  hash-secret: ${VNPAY_HASH_SECRET:}
  pay-url: https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
  return-url: ${VNPAY_RETURN_URL:http://localhost:8080/api/payment/vnpay/return}
  ipn-url: ${VNPAY_IPN_URL:http://localhost:8080/api/payment/vnpay/ipn}
  mini-app-success-url: ${MINIAPP_PAYMENT_SUCCESS_URL:https://t.me/<bot>/app?startapp=payment_success}
  mini-app-fail-url: ${MINIAPP_PAYMENT_FAIL_URL:https://t.me/<bot>/app?startapp=payment_failed}
  timeout-minutes: 15
```

### Example B: Frontend `PaymentMethod` selector (replace disabled VNPAY radio)
```tsx
// frontend/miniapp/src/pages/CheckoutPage.tsx — replace lines 125-128
<label className="flex items-center gap-2 py-2">
  <input
    type="radio"
    name="payment"
    value="VNPAY"
    checked={paymentMethod === 'VNPAY'}
    onChange={() => setPaymentMethod('VNPAY')}
  />
  <span>💳 Thanh toán qua VNPay</span>
</label>
```

### Example C: Frontend post-order VNPay redirect
```tsx
// New helper in features/payment/usePayWithVnpay.ts
import { useMutation } from '@tanstack/react-query';
import WebApp from '@twa-dev/sdk';
import { api } from '@/lib/api';

export function usePayWithVnpay() {
  return useMutation({
    mutationFn: async (orderId: string) => {
      const { data } = await api.post<{ paymentUrl: string; txnRef: string }>(
        '/api/payment/vnpay/create',
        { orderId }
      );
      return data;
    },
    onSuccess: ({ paymentUrl }) => {
      // CRITICAL: openLink (NOT window.location.href). Telegram opens VNPay in an overlay
      // so the Mini App stays alive; when the user closes the overlay, our page is still
      // there and the WebSocket / poll picks up the SUCCESS event.
      WebApp.openLink(paymentUrl, { try_instant_view: false });
    },
  });
}
```

Wire into `CheckoutPage.onSuccess`:
```ts
onSuccess: order => {
  cart.clear();
  if (order.paymentMethod === 'VNPAY') {
    payWithVnpay.mutate(order.id);          // opens VNPay overlay
    navigate(`/customer/orders/${order.id}`); // navigate underneath so when overlay closes, user sees order
  } else {
    navigate(`/customer/orders/${order.id}`, { replace: true });
  }
}
```

### Example D: Mini App status polling on `OrderDetailPage`
```tsx
// On VNPAY orders with payment_status === 'PENDING', poll every 2s until SUCCESS/FAILED or 30s.
const { data: order } = useQuery({
  queryKey: ['order', orderId],
  queryFn: () => fetchOrder(api, orderId),
  refetchInterval: q =>
    q.state.data?.paymentMethod === 'VNPAY' && q.state.data?.paymentStatus === 'PENDING'
      ? 2000
      : false,
});
```

In parallel, subscribe to STOMP `/user/queue/orders` (already wired in P6 for delivery location) and invalidate the query on `OrderConfirmedEvent` arrival — instant update if WS is up.

### Example E: Flyway migration `V9__payment.sql`
```sql
-- V9__payment.sql — payment module tables
CREATE TABLE payment (
    id                  UUID PRIMARY KEY,
    order_id            UUID NOT NULL REFERENCES orders(id),
    method              VARCHAR(16) NOT NULL,            -- COD | VNPAY
    amount              NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    status              VARCHAR(16) NOT NULL,            -- PENDING | SUCCESS | FAILED | REFUNDED
    vnp_txn_ref         VARCHAR(64) UNIQUE,
    vnp_transaction_no  VARCHAR(64),
    vnp_response_code   VARCHAR(8),
    paid_at             TIMESTAMPTZ,
    version             INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payment_order ON payment(order_id);
CREATE INDEX idx_payment_status_created ON payment(status, created_at);

CREATE TABLE payment_transaction (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      UUID NOT NULL REFERENCES payment(id) ON DELETE CASCADE,
    event_type      VARCHAR(16) NOT NULL,             -- CREATE | IPN | RETURN
    raw_payload     JSONB NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payment_tx_payment ON payment_transaction(payment_id, recorded_at);
```

### Example F: `@Scheduled` payment expiry sweeper
```java
@Component
@EnableScheduling  // Add to Application.java actually
public class PaymentExpiryScheduler {
    private final PaymentRepository paymentRepo;
    private final Clock clock;
    private final VnpayProperties props;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public void expireStalePending() {
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(props.timeoutMinutes() + 1));
        List<Payment> stale = paymentRepo.findStalePending(cutoff);
        for (Payment p : stale) {
            p.setStatus(PaymentStatus.FAILED);
            p.setVnpResponseCode("EXPIRED");
        }
        paymentRepo.saveAll(stale);
        // emit PaymentExpiredEvent → listener can notify customer if desired
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `vnp_Version=2.0.0` + MD5 / SHA256 signing | `vnp_Version=2.1.0` + HMAC-SHA512 | Pre-2020 → 2020+ | All thesis code must use 2.1.0. MD5 is deprecated by VNPay. |
| Plain SHA-256 of `secretKey + queryString` | HMAC-SHA512 with `vnp_HashSecret` as key | 2.1.0 release | Old GitHub snippets often show SHA-256 — ignore them. |
| `vnp_SecureHashType=SHA256` field | Field removed from request (still removed from verify map for safety) | 2.1.0 | Keep the "remove SecureHashType from map before re-hashing" line in `verify()`. |
| Browser redirect via `window.location` for Mini App | `Telegram.WebApp.openLink()` overlay | Telegram WebApp 6.1+ (2022) | Mini App stays alive across payment — much better UX. |
| `Date` + `SimpleDateFormat("yyyyMMddHHmmss")` with `TimeZone.getTimeZone("Etc/GMT+7")` | `ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))` + `DateTimeFormatter` | Java 8 (2014); but official sample still uses old API | Both work; new API is preferred in new code. |

**Deprecated / outdated:**
- `vnp_SecureHashType` request param — VNPay 2.1.0 derives algorithm from secret length / config; the param is silently ignored. Don't send it.
- `Charset.forName("UTF-8")` style — use `StandardCharsets.UTF_8` constants (already shown above).
- MD5 / SHA-256 signing — old VNPay versions; sandbox in 2026 is HMAC-SHA512 only.

---

## Environment Availability

> Phase 7 needs network access to VNPay sandbox ONLY during manual E2E. Backend code itself makes no outbound HTTP calls.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 17 JDK (HmacSHA512) | All P7 backend code | ✓ | Java 17 (from project pom) | — |
| PostgreSQL 16 (JSONB) | `payment_transaction.raw_payload` column | ✓ | 16-alpine via Testcontainers (verified `PostgresTestContainer.java`) | — |
| Spring Boot 3.4 starter-web, data-jpa, validation, websocket | All backend modules already use these | ✓ | inherited from delivery-parent | — |
| `@twa-dev/sdk` 7.10 (`openLink`) | Mini App VNPay overlay | ✓ | 7.10.0 (verified `frontend/miniapp/package.json`) | `window.open(url, '_blank')` if outside Telegram (dev mode) |
| `vnp_TmnCode` + `vnp_HashSecret` from sandbox | Real signature in dev/E2E | ✗ at research time | — | Register at https://sandbox.vnpayment.vn/devreg (free, ~1 day approval per design spec risk-log) — for unit tests, hard-code a known-good fixture; for IT, use `props.hashSecret() = "TESTSECRETKEY123"` in `application-test.yml` and check our own roundtrip. |
| Outbound HTTPS to sandbox.vnpayment.vn | Only browser → VNPay during E2E; backend NEVER calls VNPay | ✓ from dev machine | — | — |
| Public HTTPS tunnel (ngrok / Cloudflare Tunnel) | VNPay needs to call our IPN; localhost not reachable | ✗ optional | — | For local E2E, use `ngrok http 8080` or `cloudflared tunnel`; doc in P7 README. |
| `@EnableScheduling` | Payment expiry sweeper | ✗ not yet enabled in `Application.java` | — | Add to `Application.java` as part of P7 task list — one annotation. |

**Missing dependencies with fallback:**
- VNPay sandbox credentials → register early in P7. While waiting, use fixed test vectors for unit tests so dev isn't blocked.
- Public tunnel for IPN testing → optional; if absent, all IPN tests are unit/integration tests using direct controller invocation. Real E2E waits for tunnel.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + AssertJ (Spring Boot 3.4 default) |
| Config file | `backend/app/src/test/resources/application-test.yml` (existing) |
| Quick run command | `mvn -q -pl backend/modules/payment test` |
| Full suite command | `mvn -q -B test` (from repo root) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| VNPAY-02 | Signature is byte-identical to known reference | unit | `mvn -q -pl backend/modules/payment test -Dtest=VnpaySignatureServiceTest#signsKnownVector` | ❌ Wave 0 |
| VNPAY-02 | Signature verifies its own output (round-trip) | unit | `mvn -q -pl backend/modules/payment test -Dtest=VnpaySignatureServiceTest#signAndVerifyRoundtrip` | ❌ Wave 0 |
| VNPAY-02 | Verify rejects tampered hash | unit | `mvn -q -pl backend/modules/payment test -Dtest=VnpaySignatureServiceTest#rejectsTamperedHash` | ❌ Wave 0 |
| VNPAY-02 | Verify uses constant-time compare (mutation test ok) | unit | (same class) | ❌ Wave 0 |
| VNPAY-01 | `POST /api/payment/vnpay/create` for VNPAY order returns paymentUrl signed correctly | integration | `mvn -q -pl backend/app test -Dtest=VnpayCreateIT` | ❌ Wave 1 |
| VNPAY-04 | IPN with valid signature, valid amount, success code → DB: PENDING→SUCCESS + order CONFIRMED, response RspCode=00 | integration | `mvn -q -pl backend/app test -Dtest=VnpayIpnIT#happyPath` | ❌ Wave 1 |
| VNPAY-04 | IPN replay (second call after SUCCESS) → RspCode=02, no DB change | integration | `mvn -q -pl backend/app test -Dtest=VnpayIpnIT#replayReturns02` | ❌ Wave 1 |
| VNPAY-07 | IPN with tampered amount → RspCode=04, payment stays PENDING | integration | `mvn -q -pl backend/app test -Dtest=VnpayIpnIT#tamperedAmountReturns04` | ❌ Wave 1 |
| VNPAY-07 | IPN with bad signature → RspCode=97, no DB change | integration | `mvn -q -pl backend/app test -Dtest=VnpayIpnIT#badSignatureReturns97` | ❌ Wave 1 |
| VNPAY-04 | IPN for unknown TxnRef → RspCode=01 | integration | `mvn -q -pl backend/app test -Dtest=VnpayIpnIT#unknownTxnRefReturns01` | ❌ Wave 1 |
| VNPAY-03 | Return URL with valid sig + success code → 302 to mini-app-success-url, no DB write | integration | `mvn -q -pl backend/app test -Dtest=VnpayReturnIT#successRedirects` | ❌ Wave 1 |
| VNPAY-03 | Return URL with bad sig → 302 to mini-app-fail-url with reason=invalid_signature, no DB write | integration | `mvn -q -pl backend/app test -Dtest=VnpayReturnIT#badSigRedirects` | ❌ Wave 1 |
| VNPAY-08 | Scheduler marks payments PENDING > 15min as FAILED | unit | `mvn -q -pl backend/modules/payment test -Dtest=PaymentExpirySchedulerTest` | ❌ Wave 1 |
| VNPAY-05 | `payment_transaction.raw_payload` persists as JSONB and round-trips Map | integration | `mvn -q -pl backend/app test -Dtest=PaymentTransactionIT#persistsJsonb` | ❌ Wave 1 |
| VNPAY-06 | Mini App `CheckoutPage` allows selecting VNPAY method (was disabled in P3) | manual E2E | n/a | Wave 1 — checklist in P7 README |
| VNPAY-06 | Mini App `OrderDetailPage` shows SUCCESS within 5s of IPN arrival | manual E2E | n/a | Wave 1 — checklist |

### Sampling Rate
- **Per task commit:** `mvn -q -pl backend/modules/payment test` (~5s)
- **Per wave merge:** `mvn -q -pl backend/modules/payment,backend/app test` (~60s — payment unit + payment IT)
- **Phase gate:** `mvn -q -B verify` from repo root + manual E2E checklist signed off

### Wave 0 Gaps
- [ ] `backend/modules/payment/src/test/java/com/shop/delivery/payment/service/VnpaySignatureServiceTest.java` — sign/verify fixture (uses a hard-coded sandbox-style `hashSecret` and a known param map captured from official sample docs or first real sandbox test)
- [ ] `backend/modules/payment/src/test/resources/vnpay-fixtures/ipn-success.properties` — captured query string of a real sandbox IPN (anonymized) to use as test vector
- [ ] No new framework setup needed — JUnit 5 + Testcontainers PG already present in `backend/app/src/test/`

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Telegram initData HMAC (existing `TelegramAuthFilter`) for `POST /create`; PUBLIC for `/return` and `/ipn` (VNPay's signature is the auth mechanism). |
| V3 Session Management | n/a | Stateless. No new sessions. |
| V4 Access Control | yes | `VnpayPaymentService.createPayment` checks `order.customerId == caller.id` before creating a Payment row. |
| V5 Input Validation | yes | `@Valid CreatePaymentRequest` with `@NotNull UUID orderId`; IPN params parsed as `Map<String,String>` then explicitly validated (amount = Long, response codes = expected strings). |
| V6 Cryptography | yes | HMAC-SHA512 via JDK `javax.crypto.Mac`; constant-time compare via `MessageDigest.isEqual`. **No hand-rolled crypto.** |
| V8 Data Protection | yes | `vnp_HashSecret` lives in env var only, never logged. `payment_transaction.raw_payload` contains only VNPay response data (no card number, no CVV — VNPay does not return these). |
| V12 API Security | yes | `/ipn` is rate-limit-aware (none in P6, design doesn't require) but always returns a fast `200` with `RspCode` so attackers can't differentiate "exists vs not" timing — the constant-time signature check helps. |
| V14 Configuration | yes | `vnpay.hash-secret` env-driven; `.env.example` shows placeholder, never real secret. |

### Known Threat Patterns for VNPay Integration

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Forged IPN with attacker-chosen params | Spoofing | HMAC-SHA512 verify with shared secret. RspCode=97 on fail. |
| Replay of valid IPN to double-confirm an order | Repudiation/Tampering | `payment.status != PENDING` short-circuit → RspCode=02. `vnp_txn_ref UNIQUE` constraint backstop. |
| Amount tampering after observing a payment URL | Tampering | Compare `received_amount == payment.amount * 100` in IPN. RspCode=04 on mismatch. |
| Timing attack on signature compare | Information Disclosure | `MessageDigest.isEqual` (constant-time per JDK contract). |
| User redirected to attacker-controlled "fake VNPay page" | Spoofing/Phishing | `payUrl` is hard-coded server-side; user redirect URL is server-built; only valid `paymentUrl` is returned to Mini App. |
| User believes payment succeeded based on Return URL alone, but IPN never arrives | Repudiation | Return URL never writes to DB; UI shows "Đang xử lý" until IPN confirms via WebSocket or poll. Cron expires PENDING > 15min. |
| Leaked `vnp_HashSecret` in logs/error responses | Information Disclosure | Property is `private` in `VnpayProperties`; `toString` overridden to mask; explicit "DO NOT LOG" comment in `application.yml`. |
| Open redirect via `/return` → mini-app-fail-url | Tampering | `miniAppSuccessUrl` and `miniAppFailUrl` are server-side config (not user-controllable); only `txnRef` and `code` appended as query params. |
| Pretty-URL CSRF on `/create` (attacker tricks user into paying for someone else's order) | Tampering | `orderId` in POST body; `@CurrentUser` derived from Telegram initData; ownership check enforced. CSRF protection on POST not strictly required (stateless API, initData is the auth) but consider in V4 review. |
| Cron race: scheduler marks PENDING→FAILED at the exact moment IPN arrives | Race condition | `@Version` optimistic lock on `payment`; scheduler retries on `OptimisticLockingFailureException`. IPN handler will re-fetch and proceed normally if the version moved. |

---

## Risk / Open Questions

### 1. **Sandbox availability is uncertain at research time**
- We can't yet hit a real `vnp_*` URL to capture a ground-truth IPN. Mitigation: build all logic against the official sample's reference vector, then on first real run, capture the actual sandbox response and add it as a regression fixture.

### 2. **VNPay IPN does NOT include `vnp_OrderType` in the response**
- Confirmed from the JSP sample (`vnpay_return.jsp` lists the displayed fields; OrderType is not among them). This means our `verify()` should be tolerant — only require the params VNPay actually sends back: `vnp_TmnCode, vnp_Amount, vnp_BankCode, vnp_BankTranNo, vnp_CardType, vnp_OrderInfo, vnp_PayDate, vnp_ResponseCode, vnp_TransactionNo, vnp_TransactionStatus, vnp_TxnRef, vnp_SecureHash`. No special handling needed — we sign whatever we receive; if a param is absent, it's not in the map and not in the hash.

### 3. **Spec contradiction: should `payment_status` on `orders` be denormalized?**
- Design spec §6 says `orders.payment_status` is denormalized from `payment.status` ("source of truth vẫn là payment table"). Current `Order.java` already has it as a column with default PENDING. The IPN handler must update BOTH `payment.status` AND `orders.payment_status`. Wrap both in the same `@Transactional` boundary; if `OrderService.confirmAfterPayment` is the entry point and it also bumps status, do it there.

### 4. **VNPay sandbox merchant portal IPN registration**
- VNPay requires the merchant to register the IPN URL in their merchant portal (https://sandbox.vnpayment.vn/merchantv2/). Without registration, NO IPN will be sent — only Return URL works. Document this clearly in the P7 README as a manual setup step alongside requesting TmnCode. (This is a sandbox-only friction; production has a more formal onboarding.)

### 5. **Should refund flow be a `payment.status = REFUNDED` capability or fully out of scope?**
- Spec §10.5 and §2.2 say refund is manual / out of scope. Keep the `REFUNDED` enum value (already in `PaymentStatus`) but DO NOT build the refund endpoint, the merchant_webapi call, or any refund UI in P7. Document as deferred to a future phase.

### 6. **Logging the raw IPN payload — sanitization concern?**
- The VNPay IPN payload contains: `vnp_TmnCode, vnp_Amount, vnp_BankCode (e.g. NCB), vnp_BankTranNo, vnp_CardType (ATM/INTCARD/QRCODE), vnp_OrderInfo, vnp_PayDate, vnp_ResponseCode, vnp_TransactionNo, vnp_TransactionStatus, vnp_TxnRef, vnp_SecureHash`. **No card number, no CVV, no expiry**. Last 4 digits of card may appear in some flows (VNPay docs unclear) — verify on first real sandbox test. If they appear, mask them in `recordTransaction` before persisting. Otherwise: safe to log full payload as JSONB.

### 7. **Currency lock — `VND` is the only supported currency**
- Hard-code `vnp_CurrCode = "VND"`. The thesis is single-currency. No need to make this configurable.

### 8. **What if the sandbox sends an IPN with `vnp_ResponseCode=00` but `vnp_TransactionStatus != "00"`?**
- Treat as FAIL. Per design §10.3 step 5, both must be `"00"` for SUCCESS. The `vnp_TransactionStatus` is the more authoritative field per VNPay docs (it reflects post-settlement state).

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The two URL-encoding paths in the official VNPay sample (`ajaxServlet` create vs `vnpay_ipn.jsp` verify) produce identical hashes for ASCII-only param names | Pattern 1 | Signature verify fails on every IPN. Mitigation: Wave 0 sign-then-verify roundtrip test. |
| A2 | `Telegram.WebApp.openLink()` keeps the Mini App alive during VNPay payment in current Telegram clients (iOS, Android, Desktop) | Pitfall 6 | UX regression if Telegram changed behavior in 2025-26. Mitigation: manual test on each platform during E2E. |
| A3 | The IPN handler running in < 5s is fast enough for VNPay's retry tolerance | Pattern 3 | If VNPay's timeout is shorter (some docs say 30s, some say 5s), no impact — we always respond synchronously and quickly. |
| A4 | `vnp_OrderType = "other"` is accepted by sandbox without registration. Spec §10.1 example uses `"other"`; design also uses `"other"`. Alternative `"100000"` (food) is documented but may require merchant category approval. | Pattern 2 create | Use `"other"` as the safe default; switch to `"100000"` only if a real sandbox test rejects `"other"`. |
| A5 | The official sample's `Etc/GMT+7` (which is `-07:00` per POSIX inversion) and `Asia/Ho_Chi_Minh` (`+07:00`) produce the same `yyyyMMddHHmmss` string at any instant. **Wait — they do NOT**. `Etc/GMT+7` in POSIX = `UTC-7`. `Asia/Ho_Chi_Minh` = `UTC+7`. The official sample's `TimeZone.getTimeZone("Etc/GMT+7")` is actually WRONG by 14 hours — it just happens to "work" because VNPay's expire-time tolerance is generous and the comparison VNPay does on `vnp_CreateDate` doesn't strictly require GMT+7. Use `ZoneId.of("Asia/Ho_Chi_Minh")` (correct) — verify by inspecting the URL after first sandbox call. | Pattern 2 create + Don't Hand-Roll | If VNPay starts enforcing the timezone strictly, payments may be rejected as expired. Verify in E2E. |
| A6 | `MessageDigest.isEqual(byte[], byte[])` is constant-time in JDK 17 | Pattern 1 verify | Documented as constant-time since JDK 7; safe assumption. |
| A7 | Hibernate 6.6's `@JdbcTypeCode(SqlTypes.JSON)` correctly handles `Map<String, String>` round-trip with PostgreSQL JSONB | Example E migration | Validate with `PaymentTransactionIT#persistsJsonb` (Wave 1). If broken, fall back to `String` column with manual `ObjectMapper.writeValueAsString`. |
| A8 | The existing `WebSocketConfig.java` `/user/queue/orders` destination is reachable from the `payment` module via `SimpMessagingTemplate` (Spring DI) | Pattern 3 IPN | Verified: the bean is `SimpMessagingTemplate` from `spring-boot-starter-websocket`; available app-wide once `spring-boot-starter-websocket` is in `payment/pom.xml`. |
| A9 | Sandbox `TmnCode`/`HashSecret` provisioning takes ~1 day per design spec risk log | Environment Availability | If it takes longer, P7 can proceed with hard-coded fixtures up to manual E2E, then block on credentials for the last task. |
| A10 | Default `vnp_OrderInfo` "Thanh toan don hang DH..." is < 255 chars and ASCII-only after diacritic strip | Pitfall 5 | True for current order code format (`DH` + date + sequence ≈ 20 chars). |

---

## Sources

### Primary (HIGH confidence)
- **VNPay official Java/JSP sample** — downloaded from `https://sandbox.vnpayment.vn/apis/files/vnpay_jsp.zip` on 2026-05-22. Files inspected: `src/java/com/vnpay/common/Config.java` (HMAC + URL-encode helper), `src/java/com/vnpay/common/ajaxServlet.java` (create payment flow), `web/vnpay_return.jsp` (Return URL handler), `web/vnpay_ipn.jsp` (IPN handler with the canonical `{"RspCode":"00","Message":"Confirm Success"}` JSON response and all RspCode values 00/01/02/04/97). [VERIFIED: file extracted to /tmp/vnpay_jsp/]
- **VNPay sandbox documentation** — `https://sandbox.vnpayment.vn/apis/docs/gioi-thieu/`, `https://sandbox.vnpayment.vn/apis/docs/loai-hang-hoa/` (order type codes), `https://sandbox.vnpayment.vn/apis/docs/bang-ma-loi/` (response code table), `https://sandbox.vnpayment.vn/apis/docs/mo-hinh-ket-noi/` (connection model — confirms IPN is authoritative). [CITED]
- **VNPay sandbox demo / test cards** — `https://sandbox.vnpayment.vn/apis/vnpay-demo/` lists NCB test cards (`9704198526191432198 / NGUYEN VAN A / 07/15 / OTP 123456` = success). [CITED]
- **VNPay downloads index** — `https://sandbox.vnpayment.vn/apis/downloads/` (Java sample, tech spec PDFs).
- **Project's own design spec** — `docs/superpowers/specs/2026-05-19-he-thong-quan-ly-giao-hang-design.md` §10 — already encodes the high-level decisions. RESEARCH.md aligns with it.
- **Project's existing patterns** — `delivery` module's use of `ApplicationEventPublisher`, `WebSocketConfig.java`'s `/user/queue` destination, `SecurityConfig.java` filter chain, `PostgresTestContainer.java` test base. [VERIFIED: code read in this session]

### Secondary (MEDIUM confidence)
- **`pad1092/VNPAY-Springboot-Demo`** — `https://github.com/pad1092/VNPAY-Springboot-Demo` — Spring Boot port of the official sample, useful as a cross-reference. [VERIFIED: contents read via `api.github.com/repos/.../git/trees/main?recursive=1`]
- **`vnpay.js.org`** — `https://vnpay.js.org/en/ipn/verify-ipn-call`, `https://vnpay.js.org/en/best-practices` — NodeJS community library docs, confirms the IPN response shape and idempotency pattern. [CITED: maintained 3rd-party library; aligns with official sample.]

### Tertiary (LOW confidence — used only for context)
- **VNPay community discussion threads** on Viblo/daynhauhoc — signature troubleshooting articles. Useful for "what goes wrong" knowledge; not authoritative for code.

---

## Metadata

**Confidence breakdown:**
- Standard stack: **HIGH** — every library is already in the project pom (Spring Boot, JPA, validation, websocket, jackson). HMAC-SHA512 helper is verbatim from VNPay's own sample.
- Architecture: **HIGH** — module structure mirrors existing `delivery` and `order` modules; event-driven cross-module integration is the established pattern.
- Pitfalls: **HIGH** for the signature encoding pitfall (verified against official sample byte-for-byte), **MEDIUM** for the `openLink` Mini App overlay behavior (Telegram WebApp behavior is well-documented but VNPay UX experience in 2026 not personally tested).
- Security: **HIGH** — pattern is documented in design spec §10.4 and validated against the official sample.
- IPN response semantics (RspCode values, JSON format): **HIGH** — directly extracted from official `vnpay_ipn.jsp` source.
- Sandbox endpoint URLs: **HIGH** — confirmed at `sandbox.vnpayment.vn/apis/docs/`.
- Timezone subtlety (A5): **MEDIUM** — confirmed via Java's `ZoneId` docs; pending real-sandbox verification.

**Research date:** 2026-05-22
**Valid until:** 2026-06-22 (VNPay 2.1.0 protocol has been stable since 2020; Spring Boot 3.4 is current; Telegram WebApp `openLink` is stable. Re-verify the test-card list and the merchant portal URL before production cutover — but production is out of scope for the thesis.)
