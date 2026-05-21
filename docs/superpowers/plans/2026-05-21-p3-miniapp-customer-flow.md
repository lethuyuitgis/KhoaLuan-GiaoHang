# P3 — Mini App Foundation + Customer COD Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách hàng đặt được đơn COD end-to-end từ Telegram Mini App. Backend thay placeholder `X-Customer-Id` bằng Telegram `initData` HMAC verification. Frontend monorepo (pnpm workspace) + Mini App React + Vite + Tailwind. Sau P3, gõ `/start` cho bot → bấm nút "Đặt hàng" → Telegram mở Mini App → khách browse catalog → add to cart → checkout (COD) → order vào DB.

**Architecture:** Backend thêm `TelegramAuthFilter` (OncePerRequestFilter) verify HMAC của `initData` từ header `X-Telegram-Init-Data` → bind `TelegramUser` vào request → controllers nhận qua `@CurrentUser`. `OrderController` refactor bỏ `X-Customer-Id` header. Frontend monorepo dùng **pnpm workspace** với 2 package: `shared` (types + API client + utils) và `miniapp` (Vite + React + TS app). Mini App dùng `@twa-dev/sdk` để integrate Telegram WebApp API (initData, MainButton, BackButton, theme), TanStack Query cho server state, Zustand cho cart. Backend chạy port 8080, Mini App dev server port 5173 với Vite proxy `/api → :8080`. Để test trên Telegram thật cần HTTPS — dùng **ngrok** hoặc **cloudflare tunnel** expose Vite dev server.

**Tech Stack (mới so với P2):**
- Frontend: pnpm 9, Vite 5, React 18, TypeScript 5, TailwindCSS 3, React Router 6, TanStack Query 5, Zustand 4, Axios 1, `@twa-dev/sdk`, `@twa-dev/types`, date-fns 3
- Backend mới (nhỏ): chỉ thêm test fixtures (no new deps — HMAC dùng `javax.crypto` đã có)

---

## Bối cảnh từ P2

Sau P2 + P2.1:
- 39 commits, 74 tests pass, BUILD SUCCESS
- 4 modules code (shared, auth, bot, order) + 5 module shells (delivery, payment, notification, miniapp, webadmin)
- REST API ổn: `/api/products`, `/api/orders/*`, `/api/admin/*`
- `GlobalExceptionHandler` map đầy đủ domain exceptions sang HTTP status code
- Smoke test live: order flow PENDING→CONFIRMED→CANCELLED hoạt động qua `X-Customer-Id` header
- DB: Flyway V1-V4 đã apply; có sẵn dữ liệu test (customer 1234567, products)

**Constraints quan trọng:**
- Local JDK = Java 25 (ByteBuddy 1.17.7 override)
- Port 8080 — backend
- Port 8089 — fallback nếu 8080 bận
- Port 5173 — Mini App dev server (mới)
- Postgres dev container `shop_delivery_postgres_dev` keep running
- App run command: `cd backend/app && ../mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- File `.env` ở root đã có `BOT_TOKEN` và `BOT_USERNAME` (gitignored, perms 600)

**Decision dùng trong P3:**

1. **Auth filter — plain `OncePerRequestFilter`, KHÔNG dùng Spring Security.** P4 sẽ add Spring Security cho JWT admin. Lý do: tránh sớm complexity của Spring Security chain; P3 chỉ cần 1 path qua filter.

2. **HMAC verification theo spec Telegram chính thức** (https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app):
   - Parse `initData` thành key-value pairs (URL decode)
   - Tách `hash` ra
   - Sort các pair theo key alphabetically
   - Build `data_check_string` = "key=value\nkey=value\n..."
   - `secret_key = HMAC-SHA256("WebAppData", bot_token)` (key = "WebAppData", message = bot_token)
   - `expected_hash = HMAC-SHA256(secret_key, data_check_string)` hex
   - So sánh constant-time với `hash` nhận được

3. **Auth flow code:**
   - Header: `X-Telegram-Init-Data: <raw initData string>`
   - Filter verify → parse user JSON → `TelegramUserService.registerOrUpdate` (idempotent) → bind `TelegramUser` vào `HttpServletRequest` attribute "currentUser"
   - `@CurrentUser` annotation + argument resolver extract từ request attribute
   - Path không cần auth: `/api/products/*`, `/actuator/*`, `/api/bot/webhook`, `/api/payment/vnpay/return`, `/api/payment/vnpay/ipn`, admin paths (sẽ secure ở P4)
   - Path cần auth: `/api/me`, `/api/orders/*` (trừ admin), `/api/shipper/*` (sẽ ở P5)

4. **`/api/me` response:** trả Telegram user info + roles (mảng `["CUSTOMER"]` v.v.). Mini App dùng để biết user là khách hay shipper → route phù hợp.

5. **Frontend monorepo dùng pnpm** (không yarn/npm) — gọn, hỗ trợ workspace tốt:
   ```
   frontend/
   ├── pnpm-workspace.yaml
   ├── package.json (root, scripts orchestrate)
   ├── shared/
   │   ├── package.json (@shop/shared)
   │   └── src/
   │       ├── types/         # TypeScript types matching backend DTOs
   │       ├── api/           # Axios client
   │       └── utils/         # format VND, dates, ...
   └── miniapp/
       ├── package.json (@shop/miniapp)
       ├── vite.config.ts
       ├── tailwind.config.ts
       ├── tsconfig.json
       └── src/
           ├── main.tsx
           ├── App.tsx
           ├── lib/
           │   ├── telegram.ts
           │   ├── api.ts
           │   └── auth.ts
           ├── features/
           │   ├── catalog/
           │   ├── cart/      # Zustand store
           │   ├── checkout/
           │   └── orders/
           ├── pages/
           │   ├── SplashPage.tsx
           │   ├── CatalogPage.tsx
           │   ├── CartPage.tsx
           │   ├── CheckoutPage.tsx
           │   ├── OrdersPage.tsx
           │   └── OrderDetailPage.tsx
           ├── components/
           │   ├── Layout.tsx
           │   ├── ProductCard.tsx
           │   ├── CartButton.tsx
           │   └── OrderStatusBadge.tsx
           └── styles/
               └── globals.css
   ```

6. **Customer COD only trong P3.** VNPay button hiển thị "Sắp có" hoặc disabled. Full VNPay integration ở P7.

7. **HTTPS cho Telegram testing:** Mini App phải chạy qua HTTPS để Telegram WebView load. Dev options (chọn 1):
   - **ngrok** (recommended): `ngrok http 5173` → URL `https://abc.ngrok-free.app` → set Bot Menu Button = URL này qua BotFather (`/setmenubutton`)
   - **cloudflared**: `cloudflared tunnel --url http://localhost:5173`
   - localtunnel: `npx localtunnel --port 5173`

   Plan sẽ document ngrok. Trong dev mode browser thường (không Telegram), Mini App vẫn load nhưng auth fail (no initData) — sẽ có "dev mode banner" nhắc.

8. **BotFather Menu Button setup** (manual, 1 lần):
   - `@BotFather` → `/mybots` → chọn `@shop_giaohang_bot` → "Bot Settings" → "Menu Button"
   - Title: "🛒 Đặt hàng"
   - URL: `https://abc.ngrok-free.app` (mỗi lần restart ngrok URL đổi → cần update)

9. **`StartHandler` update**: Welcome message phải thêm Mini App button ngoài text. Bot API có `KeyboardButton.web_app` cho ReplyKeyboardMarkup hoặc `InlineKeyboardButton.web_app` cho inline. Mình dùng `ReplyKeyboard` hiển thị nút dưới input. Cần `BotProperties.miniappUrl`.

---

## File Structure (sau khi P3 hoàn thành)

```
KhoaLuan-GiaoHang/
├── backend/
│   ├── modules/
│   │   ├── auth/
│   │   │   └── src/main/java/com/shop/delivery/auth/
│   │   │       ├── api/
│   │   │       │   ├── CurrentUser.java                              (TASK 2)
│   │   │       │   ├── CurrentUserArgumentResolver.java              (TASK 2)
│   │   │       │   ├── TelegramAuthFilter.java                       (TASK 2)
│   │   │       │   ├── WebMvcConfig.java                             (TASK 2)
│   │   │       │   ├── MeController.java                             (TASK 3)
│   │   │       │   └── dto/
│   │   │       │       └── MeResponse.java                           (TASK 3)
│   │   │       └── service/
│   │   │           └── TelegramInitDataVerifier.java                 (TASK 1)
│   │   ├── bot/
│   │   │   └── src/main/java/com/shop/delivery/bot/
│   │   │       ├── config/BotProperties.java                         (modify — add miniappUrl)
│   │   │       └── handler/common/StartHandler.java                  (modify — add MiniApp button)
│   │   └── order/
│   │       └── src/main/java/com/shop/delivery/order/api/
│   │           └── OrderController.java                              (modify — @CurrentUser)
│   └── app/src/main/resources/
│       ├── application.yml                                            (modify — bot.miniapp-url)
│       ├── application-dev.yml                                        (modify)
│       └── application-prod.yml                                       (modify)
├── frontend/
│   ├── pnpm-workspace.yaml                                            (TASK 5)
│   ├── package.json                                                   (TASK 5)
│   ├── .npmrc                                                         (TASK 5)
│   ├── .gitignore                                                     (TASK 5)
│   ├── shared/
│   │   ├── package.json                                               (TASK 5)
│   │   ├── tsconfig.json                                              (TASK 5)
│   │   └── src/
│   │       ├── index.ts                                               (TASK 5)
│   │       ├── types/
│   │       │   ├── product.ts                                         (TASK 5)
│   │       │   ├── order.ts                                           (TASK 5)
│   │       │   ├── user.ts                                            (TASK 5)
│   │       │   └── api-error.ts                                       (TASK 5)
│   │       ├── api/
│   │       │   ├── client.ts                                          (TASK 8)
│   │       │   ├── products.ts                                        (TASK 8)
│   │       │   ├── orders.ts                                          (TASK 8)
│   │       │   └── me.ts                                              (TASK 8)
│   │       └── utils/
│   │           ├── format-money.ts                                    (TASK 5)
│   │           └── format-date.ts                                     (TASK 5)
│   └── miniapp/
│       ├── package.json                                               (TASK 6)
│       ├── tsconfig.json                                              (TASK 6)
│       ├── tsconfig.node.json                                         (TASK 6)
│       ├── vite.config.ts                                             (TASK 6)
│       ├── tailwind.config.ts                                         (TASK 6)
│       ├── postcss.config.js                                          (TASK 6)
│       ├── index.html                                                 (TASK 6)
│       └── src/
│           ├── main.tsx                                               (TASK 6)
│           ├── App.tsx                                                (TASK 9)
│           ├── styles/globals.css                                     (TASK 6)
│           ├── lib/
│           │   ├── telegram.ts                                        (TASK 7)
│           │   ├── api.ts                                             (TASK 8)
│           │   └── auth.ts                                            (TASK 7)
│           ├── providers/
│           │   ├── QueryProvider.tsx                                  (TASK 8)
│           │   └── TelegramProvider.tsx                               (TASK 7)
│           ├── components/
│           │   ├── Layout.tsx                                         (TASK 9)
│           │   ├── ProductCard.tsx                                    (TASK 10)
│           │   ├── CartButton.tsx                                     (TASK 11)
│           │   ├── ErrorBoundary.tsx                                  (TASK 9)
│           │   └── OrderStatusBadge.tsx                               (TASK 13)
│           ├── features/cart/
│           │   ├── cart-store.ts                                      (TASK 11)
│           │   └── use-cart.ts                                        (TASK 11)
│           └── pages/
│               ├── SplashPage.tsx                                     (TASK 9)
│               ├── CatalogPage.tsx                                    (TASK 10)
│               ├── CartPage.tsx                                       (TASK 11)
│               ├── CheckoutPage.tsx                                   (TASK 12)
│               ├── OrdersPage.tsx                                     (TASK 13)
│               ├── OrderDetailPage.tsx                                (TASK 13)
│               └── NotFoundPage.tsx                                   (TASK 9)
└── infra/
    └── README.md                                                       (modify — add ngrok note)
```

**File mới:** ~50 frontend + ~10 backend = ~60
**File modify:** 6 yml/properties/Java files

---

## TASK 1: TelegramInitDataVerifier (TDD, 1 commit)

**Files:**
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/TelegramInitDataVerifier.java`
- Test: `backend/modules/auth/src/test/java/com/shop/delivery/auth/service/TelegramInitDataVerifierTest.java`

- [ ] **Step 1: Write failing test**

Test must include both happy path (valid HMAC) and failure path (tampered data). To compute valid test data, we use a known bot token and pre-compute the expected hash with a Python/JS script offline, OR build the verifier first as a stub and use it to generate the hash for the happy path test.

Better approach: hardcode known good initData computed offline. Here's a known-good example using bot token `1111:TEST_TOKEN_FOR_HMAC_TEST_ONLY_NOT_REAL`:

```
auth_date=1700000000
query_id=AAH...
user={"id":1234567,"first_name":"Test","last_name":"User","username":"testuser","language_code":"vi"}
hash=<computed below>
```

For computing hash with this stub token, expected hash is deterministic. The test will generate it via the verifier itself (chicken-and-egg)... actually NO. We can't bootstrap that way for security — the test must use a hash NOT computed by code-under-test.

Approach: precompute the hash externally OR write a helper that uses raw `javax.crypto` independent of the verifier class. We'll use a helper in the test that uses `javax.crypto` directly to build the expected hash, separately from the verifier under test. The two implementations are independent paths.

Create `backend/modules/auth/src/test/java/com/shop/delivery/auth/service/TelegramInitDataVerifierTest.java`:

```java
package com.shop.delivery.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramInitDataVerifierTest {

    private static final String TEST_BOT_TOKEN = "1111:TEST_TOKEN_FOR_HMAC_TEST_ONLY";

    private TelegramInitDataVerifier verifier;

    @BeforeEach
    void setup() {
        verifier = new TelegramInitDataVerifier(TEST_BOT_TOKEN);
    }

    @Test
    void shouldAcceptValidInitData() {
        Map<String, String> data = new TreeMap<>();
        data.put("auth_date", "1700000000");
        data.put("query_id", "AAH123456");
        data.put("user", "{\"id\":1234567,\"first_name\":\"Test\",\"last_name\":\"User\",\"username\":\"testuser\",\"language_code\":\"vi\"}");

        String initData = buildSignedInitData(data, TEST_BOT_TOKEN);

        TelegramInitDataVerifier.VerifiedInitData result = verifier.verify(initData);

        assertThat(result.userId()).isEqualTo(1234567L);
        assertThat(result.firstName()).isEqualTo("Test");
        assertThat(result.username()).isEqualTo("testuser");
        assertThat(result.languageCode()).isEqualTo("vi");
    }

    @Test
    void shouldRejectTamperedInitData() {
        Map<String, String> data = new TreeMap<>();
        data.put("auth_date", "1700000000");
        data.put("user", "{\"id\":1234567,\"first_name\":\"Test\"}");
        String validInitData = buildSignedInitData(data, TEST_BOT_TOKEN);

        // Tamper: change user ID
        String tampered = validInitData.replace("1234567", "9999999");

        assertThat(verifier.tryVerify(tampered)).isEmpty();
    }

    @Test
    void shouldRejectMissingHashField() {
        // No hash at all
        String initData = "auth_date=1700000000&user=%7B%22id%22%3A1%7D";
        assertThat(verifier.tryVerify(initData)).isEmpty();
    }

    @Test
    void shouldRejectMalformedUserJson() {
        Map<String, String> data = new TreeMap<>();
        data.put("auth_date", "1700000000");
        data.put("user", "not-json");
        String initData = buildSignedInitData(data, TEST_BOT_TOKEN);

        assertThat(verifier.tryVerify(initData)).isEmpty();
    }

    @Test
    void shouldRejectWhenSignedWithDifferentToken() {
        Map<String, String> data = new TreeMap<>();
        data.put("auth_date", "1700000000");
        data.put("user", "{\"id\":1234567,\"first_name\":\"Test\"}");
        String initData = buildSignedInitData(data, "DIFFERENT_TOKEN_NOT_TEST");

        assertThat(verifier.tryVerify(initData)).isEmpty();
    }

    /**
     * Helper that builds a signed initData string from scratch, using INDEPENDENT
     * HMAC implementation (not the verifier under test). This way the test
     * verifies the verifier's logic against a separate reference impl.
     *
     * Algorithm per https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app
     */
    static String buildSignedInitData(Map<String, String> sortedData, String botToken) {
        // data_check_string = sorted key=value pairs joined by \n (NO url encoding here)
        StringBuilder checkString = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : sortedData.entrySet()) {
            if (!first) checkString.append('\n');
            checkString.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        // secret_key = HMAC_SHA256("WebAppData", bot_token)
        byte[] secretKey = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8),
            botToken.getBytes(StandardCharsets.UTF_8));
        byte[] hashBytes = hmacSha256(secretKey, checkString.toString().getBytes(StandardCharsets.UTF_8));
        String hash = toHex(hashBytes);

        // Build URL-encoded querystring with hash appended
        StringBuilder qs = new StringBuilder();
        for (Map.Entry<String, String> e : sortedData.entrySet()) {
            if (qs.length() > 0) qs.append('&');
            qs.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        qs.append("&hash=").append(hash);
        return qs.toString();
    }

    static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

- [ ] **Step 3: Implement `TelegramInitDataVerifier`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/TelegramInitDataVerifier.java`:

```java
package com.shop.delivery.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Verify Telegram Mini App initData per https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app
 *
 * Algorithm:
 *   secret_key  = HMAC_SHA256(key="WebAppData", msg=bot_token)
 *   hash_check  = HMAC_SHA256(key=secret_key, msg=data_check_string)
 *   where data_check_string = "key1=value1\nkey2=value2\n..." (alphabetically sorted, hash field excluded)
 */
@Service
public class TelegramInitDataVerifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramInitDataVerifier.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String botToken;

    public TelegramInitDataVerifier(@Value("${bot.token}") String botToken) {
        this.botToken = botToken;
    }

    public record VerifiedInitData(
        Long userId,
        String firstName,
        String lastName,
        String username,
        String languageCode,
        String rawUserJson
    ) {
    }

    /** Throws IllegalArgumentException if invalid. Use tryVerify for Optional-based handling. */
    public VerifiedInitData verify(String initData) {
        return tryVerify(initData)
            .orElseThrow(() -> new IllegalArgumentException("Invalid Telegram initData"));
    }

    public Optional<VerifiedInitData> tryVerify(String initData) {
        if (initData == null || initData.isBlank()) return Optional.empty();

        // 1. Parse querystring → TreeMap (sorted)
        TreeMap<String, String> params = new TreeMap<>();
        String receivedHash = null;
        for (String pair : initData.split("&")) {
            int idx = pair.indexOf('=');
            if (idx <= 0) continue;
            String key = pair.substring(0, idx);
            String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            if ("hash".equals(key)) {
                receivedHash = value;
            } else {
                params.put(key, value);
            }
        }

        if (receivedHash == null || params.isEmpty()) {
            log.debug("initData missing hash or empty");
            return Optional.empty();
        }

        // 2. Build data_check_string
        StringBuilder checkString = new StringBuilder();
        boolean first = true;
        for (var e : params.entrySet()) {
            if (!first) checkString.append('\n');
            checkString.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }

        // 3. secret_key = HMAC_SHA256("WebAppData", bot_token)
        byte[] secretKey = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8),
            botToken.getBytes(StandardCharsets.UTF_8));

        // 4. expected_hash = HMAC_SHA256(secret_key, data_check_string)
        byte[] expectedHashBytes = hmacSha256(secretKey,
            checkString.toString().getBytes(StandardCharsets.UTF_8));
        String expectedHash = toHex(expectedHashBytes);

        // 5. Constant-time compare
        if (!constantTimeEquals(receivedHash, expectedHash)) {
            log.debug("initData hash mismatch");
            return Optional.empty();
        }

        // 6. Extract user info from user JSON
        String userJson = params.get("user");
        if (userJson == null) {
            log.debug("initData missing user field");
            return Optional.empty();
        }

        try {
            JsonNode userNode = JSON.readTree(userJson);
            long userId = userNode.path("id").asLong(0);
            if (userId == 0) {
                log.debug("initData user.id is zero or missing");
                return Optional.empty();
            }
            return Optional.of(new VerifiedInitData(
                userId,
                textOrNull(userNode, "first_name"),
                textOrNull(userNode, "last_name"),
                textOrNull(userNode, "username"),
                textOrNull(userNode, "language_code"),
                userJson
            ));
        } catch (Exception ex) {
            log.debug("initData user JSON parse failed", ex);
            return Optional.empty();
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) return null;
        return child.asText();
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 failure", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
    }
}
```

Note: the constructor uses `@Value("${bot.token}")` directly so this verifier can live in `auth` module without depending on `bot` module's `BotProperties`. The token is the same value, just sourced via a different binding.

- [ ] **Step 4: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

Expected: 5 new tests pass (added to existing 11 auth tests + 2 IT = 18 total in auth module).

- [ ] **Step 5: Commit**

```bash
git add backend/modules/auth/src/main/java/com/shop/delivery/auth/service/TelegramInitDataVerifier.java \
        backend/modules/auth/src/test/java/com/shop/delivery/auth/service/TelegramInitDataVerifierTest.java
git commit -m "feat(auth): add TelegramInitDataVerifier for Mini App HMAC validation"
```

---

## TASK 2: TelegramAuthFilter + @CurrentUser (1 commit)

**Files:**
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/TelegramAuthFilter.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/CurrentUser.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/CurrentUserArgumentResolver.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/WebMvcConfig.java`

- [ ] **Step 1: Write `@CurrentUser` annotation**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/CurrentUser.java`:

```java
package com.shop.delivery.auth.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inject the authenticated TelegramUser into a controller method parameter.
 * Filter must have run first (TelegramAuthFilter). If no user, the resolver
 * returns null — controllers should handle by manual null-check or rely on
 * @AuthenticationRequired (future P3.1 if needed).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
```

- [ ] **Step 2: Write `TelegramAuthFilter`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/TelegramAuthFilter.java`:

```java
package com.shop.delivery.auth.api;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.service.TelegramInitDataVerifier;
import com.shop.delivery.auth.service.TelegramUserService;
import com.shop.delivery.auth.service.TelegramUserUpsertCommand;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Verify Telegram initData header on every request. On success, set
 * the resolved TelegramUser as request attribute "currentUser" so
 * controllers can access via @CurrentUser.
 *
 * Public paths (no initData required): /api/products, /actuator, /api/bot/webhook,
 * /api/admin/* (P4 will add JWT for admin), /api/payment/vnpay/*.
 *
 * Protected paths reading currentUser: /api/me, /api/orders/* (non-admin).
 */
@Component
public class TelegramAuthFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE_CURRENT_USER = "currentUser";
    public static final String HEADER_INIT_DATA = "X-Telegram-Init-Data";

    private static final Logger log = LoggerFactory.getLogger(TelegramAuthFilter.class);

    private final TelegramInitDataVerifier verifier;
    private final TelegramUserService userService;

    public TelegramAuthFilter(TelegramInitDataVerifier verifier,
                              TelegramUserService userService) {
        this.verifier = verifier;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String initData = request.getHeader(HEADER_INIT_DATA);
        if (initData != null && !initData.isBlank()) {
            verifier.tryVerify(initData).ifPresent(verified -> {
                // Upsert user to keep DB row fresh with latest Telegram info
                TelegramUser user = userService.registerOrUpdate(new TelegramUserUpsertCommand(
                    verified.userId(),
                    verified.username(),
                    verified.firstName(),
                    verified.lastName(),
                    verified.languageCode()
                ));
                request.setAttribute(ATTRIBUTE_CURRENT_USER, user);
                log.debug("Auth OK userId={}", user.getId());
            });
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Run for all paths — filter is a no-op when header is absent.
        // Controllers decide whether to require currentUser (via @CurrentUser usage).
        return false;
    }
}
```

- [ ] **Step 3: Write `CurrentUserArgumentResolver`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/CurrentUserArgumentResolver.java`:

```java
package com.shop.delivery.auth.api;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.shared.exception.NotFoundException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
            && TelegramUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest req = webRequest.getNativeRequest(HttpServletRequest.class);
        TelegramUser user = req != null
            ? (TelegramUser) req.getAttribute(TelegramAuthFilter.ATTRIBUTE_CURRENT_USER)
            : null;
        if (user == null) {
            throw new NotFoundException("UNAUTHENTICATED", "Cần xác thực Telegram để truy cập");
        }
        return user;
    }
}
```

Note: we throw `NotFoundException` mapped to 404 by `GlobalExceptionHandler`. For "no auth" cases this would technically be 401, but P3 doesn't have a `AuthenticationException` mapping yet. Adding a new exception type just for this feels overkill — using `NotFoundException` with a clear code is acceptable for now. **P4 will introduce proper 401/403** when Spring Security is added for admin.

Actually let me reconsider: 401 is the right HTTP status. Let me throw a `BusinessRuleException` with code `UNAUTHENTICATED` → 422. Hmm that's also wrong semantically.

**Decision: throw `RuntimeException` extending nothing and add explicit handler later, OR create `AuthenticationException` in shared module now.**

Cleaner: add `AuthenticationException` in shared now (very small change) and map to 401 in GlobalExceptionHandler.

Update plan: do this as part of TASK 2.

Modified Step 3 above already uses `NotFoundException`. Let me leave it as-is for simplicity — `UNAUTHENTICATED` code at 404 is a meaningful response that Mini App can interpret. P4 can refactor.

Actually let me add `AuthenticationException` for proper 401. It's a small addition:

Add to `backend/modules/shared/src/main/java/com/shop/delivery/shared/exception/AuthenticationException.java`:

```java
package com.shop.delivery.shared.exception;

public class AuthenticationException extends DomainException {
    public AuthenticationException(String code, String message) {
        super(code, message);
    }
}
```

Update `GlobalExceptionHandler.java` to handle it:

```java
@ExceptionHandler(AuthenticationException.class)
public ResponseEntity<ApiError> handleAuth(AuthenticationException e) {
    return status(HttpStatus.UNAUTHORIZED, e);
}
```

Update `CurrentUserArgumentResolver.resolveArgument` to throw `AuthenticationException` instead of `NotFoundException`:

```java
import com.shop.delivery.shared.exception.AuthenticationException;
// ...
if (user == null) {
    throw new AuthenticationException("UNAUTHENTICATED", "Cần xác thực Telegram để truy cập");
}
return user;
```

These 3 changes (1 new file + 2 edits) are part of TASK 2.

- [ ] **Step 4: Add `AuthenticationException` to shared module**

Create `backend/modules/shared/src/main/java/com/shop/delivery/shared/exception/AuthenticationException.java`:

```java
package com.shop.delivery.shared.exception;

public class AuthenticationException extends DomainException {
    public AuthenticationException(String code, String message) {
        super(code, message);
    }
}
```

- [ ] **Step 5: Update `GlobalExceptionHandler` to map AuthenticationException → 401**

Read `backend/modules/shared/src/main/java/com/shop/delivery/shared/api/GlobalExceptionHandler.java`, then add:

After the import block:
```java
import com.shop.delivery.shared.exception.AuthenticationException;
```

After the existing `@ExceptionHandler(NotFoundException.class)` method, add:
```java
@ExceptionHandler(AuthenticationException.class)
public ResponseEntity<ApiError> handleAuth(AuthenticationException e) {
    return status(HttpStatus.UNAUTHORIZED, e);
}
```

Add a unit test to `backend/modules/shared/src/test/java/com/shop/delivery/shared/api/GlobalExceptionHandlerTest.java` — append before the closing brace:

```java
@Test
void authenticationShouldReturn401() {
    ResponseEntity<ApiError> resp = handler.handleAuth(
        new AuthenticationException("UNAUTHENTICATED", "Need Telegram auth"));
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getBody().code()).isEqualTo("UNAUTHENTICATED");
}
```

Don't forget the import `import com.shop.delivery.shared.exception.AuthenticationException;` in the test.

- [ ] **Step 6: Write `WebMvcConfig`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/WebMvcConfig.java`:

```java
package com.shop.delivery.auth.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebMvcConfig(CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // For Mini App dev: allow any origin for /api during development.
        // P9 prod deploy will tighten this to specific domains.
        registry.addMapping("/api/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false);
    }
}
```

- [ ] **Step 7: Update `CurrentUserArgumentResolver` to use `AuthenticationException`**

(Already shown in Step 3 above — use `AuthenticationException` not `NotFoundException`.)

- [ ] **Step 8: Verify compile + run all tests**

```bash
cd backend && ./mvnw verify
```

Expected: BUILD SUCCESS, 74 + 5 (verifier) + 1 (auth exception) = 80 tests pass.

- [ ] **Step 9: Commit**

```bash
git add backend/modules/shared/src/main/java/com/shop/delivery/shared/exception/AuthenticationException.java \
        backend/modules/shared/src/main/java/com/shop/delivery/shared/api/GlobalExceptionHandler.java \
        backend/modules/shared/src/test/java/com/shop/delivery/shared/api/GlobalExceptionHandlerTest.java \
        backend/modules/auth/src/main/java/com/shop/delivery/auth/api/
git commit -m "feat(auth): add TelegramAuthFilter + @CurrentUser argument resolver"
```

---

## TASK 3: /api/me Endpoint (1 commit)

**Files:**
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/MeController.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/dto/MeResponse.java`
- Test: `backend/modules/auth/src/test/java/com/shop/delivery/auth/api/MeControllerTest.java`

- [ ] **Step 1: Write `MeResponse` DTO**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/dto/MeResponse.java`:

```java
package com.shop.delivery.auth.api.dto;

import com.shop.delivery.auth.domain.Role;

import java.util.Set;

public record MeResponse(
    Long id,
    String username,
    String firstName,
    String lastName,
    String languageCode,
    Set<Role> roles
) {
}
```

- [ ] **Step 2: Write failing test**

Create `backend/modules/auth/src/test/java/com/shop/delivery/auth/api/MeControllerTest.java`:

```java
package com.shop.delivery.auth.api;

import com.shop.delivery.auth.api.dto.MeResponse;
import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.service.RoleResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeControllerTest {

    @Mock RoleResolver roleResolver;

    @InjectMocks MeController controller;

    @Test
    void shouldReturnUserInfoAndRoles() {
        TelegramUser user = new TelegramUser();
        user.setId(12345L);
        user.setFirstName("Thúy");
        user.setLastName("Lê");
        user.setUsername("thuyltt");
        user.setLanguageCode("vi");

        when(roleResolver.activeRolesOf(12345L)).thenReturn(Set.of(Role.CUSTOMER));

        MeResponse resp = controller.me(user);

        assertThat(resp.id()).isEqualTo(12345L);
        assertThat(resp.firstName()).isEqualTo("Thúy");
        assertThat(resp.lastName()).isEqualTo("Lê");
        assertThat(resp.username()).isEqualTo("thuyltt");
        assertThat(resp.languageCode()).isEqualTo("vi");
        assertThat(resp.roles()).containsExactly(Role.CUSTOMER);
    }
}
```

- [ ] **Step 3: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

- [ ] **Step 4: Implement `MeController`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/MeController.java`:

```java
package com.shop.delivery.auth.api;

import com.shop.delivery.auth.api.dto.MeResponse;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.service.RoleResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final RoleResolver roleResolver;

    public MeController(RoleResolver roleResolver) {
        this.roleResolver = roleResolver;
    }

    @GetMapping
    public MeResponse me(@CurrentUser TelegramUser user) {
        return new MeResponse(
            user.getId(),
            user.getUsername(),
            user.getFirstName(),
            user.getLastName(),
            user.getLanguageCode(),
            roleResolver.activeRolesOf(user.getId())
        );
    }
}
```

- [ ] **Step 5: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

- [ ] **Step 6: Commit**

```bash
git add backend/modules/auth/src/main/java/com/shop/delivery/auth/api/MeController.java \
        backend/modules/auth/src/main/java/com/shop/delivery/auth/api/dto/MeResponse.java \
        backend/modules/auth/src/test/java/com/shop/delivery/auth/api/MeControllerTest.java
git commit -m "feat(auth): add /api/me endpoint returning user info + roles"
```

---

## TASK 4: Refactor OrderController to use @CurrentUser (1 commit)

**Files:**
- Modify: `backend/modules/order/src/main/java/com/shop/delivery/order/api/OrderController.java`
- Modify: `backend/app/src/test/java/com/shop/delivery/OrderFlowIT.java` — switch from `X-Customer-Id` to `X-Telegram-Init-Data`

- [ ] **Step 1: Update `OrderController`**

Read existing `backend/modules/order/src/main/java/com/shop/delivery/order/api/OrderController.java`. Replace with:

```java
package com.shop.delivery.order.api;

import com.shop.delivery.auth.api.CurrentUser;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.order.api.dto.CancelOrderRequest;
import com.shop.delivery.order.api.dto.CreateOrderRequest;
import com.shop.delivery.order.api.dto.OrderResponse;
import com.shop.delivery.order.api.dto.OrderSummary;
import com.shop.delivery.order.api.mapper.OrderMapper;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.service.OrderService;
import com.shop.delivery.order.service.command.CreateOrderCommand;
import com.shop.delivery.order.service.command.OrderLineCommand;
import com.shop.delivery.shared.exception.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Customer-facing order API. Authentication via TelegramAuthFilter (header X-Telegram-Init-Data).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;
    private final OrderMapper mapper;

    public OrderController(OrderService service, OrderMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@CurrentUser TelegramUser user,
                                @Valid @RequestBody CreateOrderRequest req) {
        CreateOrderCommand cmd = new CreateOrderCommand(
            user.getId(),
            firstNonNull(req.customerName(), user.getFirstName()),
            req.customerPhone(),
            req.deliveryAddress(), req.deliveryLat(), req.deliveryLng(),
            req.items().stream().map(i -> new OrderLineCommand(i.productId(), i.quantity())).toList(),
            req.paymentMethod(), req.note()
        );
        Order order = service.create(cmd);
        return mapper.toResponse(order);
    }

    @GetMapping("/mine")
    public Page<OrderSummary> mine(@CurrentUser TelegramUser user,
                                   @PageableDefault(size = 20) Pageable pageable) {
        return service.findMine(user.getId(), pageable).map(mapper::toSummary);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@CurrentUser TelegramUser user, @PathVariable UUID id) {
        Order order = service.findById(id);
        if (!order.getCustomerId().equals(user.getId())) {
            throw new NotFoundException(
                "ORDER_NOT_FOUND", "Đơn không tồn tại hoặc không thuộc về bạn");
        }
        return mapper.toResponse(order);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@CurrentUser TelegramUser user,
                                @PathVariable UUID id,
                                @RequestBody CancelOrderRequest req) {
        Order existing = service.findById(id);
        if (!existing.getCustomerId().equals(user.getId())) {
            throw new NotFoundException(
                "ORDER_NOT_FOUND", "Đơn không tồn tại hoặc không thuộc về bạn");
        }
        Order cancelled = service.cancel(id, user.getId(), req.reason());
        return mapper.toResponse(cancelled);
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
```

- [ ] **Step 2: Update `OrderFlowIT` to use signed initData**

The existing `OrderFlowIT` uses `X-Customer-Id: 5555` header. Refactor it to send signed `X-Telegram-Init-Data` instead. We need to import `TelegramInitDataVerifierTest.buildSignedInitData` helper OR duplicate it.

Cleaner: extract the helper to `backend/modules/auth/src/test/java/com/shop/delivery/auth/support/TelegramInitDataFixture.java` (move from the inner static methods in `TelegramInitDataVerifierTest`). Then app's `OrderFlowIT` can call it.

Actually for module-isolation simplicity: keep the helper inline in `OrderFlowIT` rather than cross-module dep. Just copy the methods.

Read existing `backend/app/src/test/java/com/shop/delivery/OrderFlowIT.java`. Replace with:

```java
package com.shop.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.shop.delivery.support.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql(statements = {
    "INSERT INTO telegram_user(id, first_name, language_code) VALUES (5555, 'Test', 'vi') ON CONFLICT DO NOTHING",
    "INSERT INTO product(name, price, stock, is_active) VALUES ('Test Product', 100000, 100, true) ON CONFLICT DO NOTHING"
})
class OrderFlowIT extends PostgresTestContainer {

    @Autowired TestRestTemplate rest;

    @Value("${bot.token}") String botToken;

    private HttpHeaders customerHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("X-Telegram-Init-Data", buildInitDataFor(5555L, "Test", "User", "testuser"));
        return h;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void shouldCreateOrderViaRestAndAdminConfirm() {
        ResponseEntity<JsonNode> productList = rest.getForEntity("/api/products", JsonNode.class);
        assertThat(productList.getStatusCode().is2xxSuccessful()).isTrue();
        Long productId = productList.getBody().get("content").get(0).get("id").asLong();

        Map<String, Object> body = Map.of(
            "customerName", "Test Customer",
            "customerPhone", "+84900111222",
            "deliveryAddress", "45 Bà Triệu",
            "deliveryLat", "21.0193",
            "deliveryLng", "105.8503",
            "items", List.of(Map.of("productId", productId, "quantity", 2)),
            "paymentMethod", "COD",
            "note", "test order"
        );

        ResponseEntity<JsonNode> createResp = rest.exchange(
            "/api/orders", HttpMethod.POST,
            new HttpEntity<>(body, customerHeaders()), JsonNode.class);

        assertThat(createResp.getStatusCode().value()).isEqualTo(201);
        JsonNode order = createResp.getBody();
        assertThat(order.get("status").asText()).isEqualTo("PENDING");
        assertThat(order.get("code").asText()).startsWith("DH");
        assertThat(order.get("subtotal").asLong()).isEqualTo(200000L);
        assertThat(order.get("items").size()).isEqualTo(1);

        String orderId = order.get("id").asText();

        ResponseEntity<JsonNode> mine = rest.exchange(
            "/api/orders/mine", HttpMethod.GET,
            new HttpEntity<>(customerHeaders()), JsonNode.class);
        assertThat(mine.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(mine.getBody().get("content").size()).isGreaterThanOrEqualTo(1);

        ResponseEntity<JsonNode> confirmed = rest.exchange(
            "/api/admin/orders/" + orderId + "/confirm", HttpMethod.POST,
            new HttpEntity<>(Map.of("note", "auto-confirm in test"), jsonHeaders()),
            JsonNode.class);

        assertThat(confirmed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(confirmed.getBody().get("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void unauthorizedRequestShouldReturn401() {
        ResponseEntity<JsonNode> resp = rest.getForEntity("/api/orders/mine", JsonNode.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    /** Build a signed initData using the test bot token. Independent reference impl. */
    private String buildInitDataFor(long userId, String firstName, String lastName, String username) {
        String userJson = String.format(
            "{\"id\":%d,\"first_name\":\"%s\",\"last_name\":\"%s\",\"username\":\"%s\",\"language_code\":\"vi\"}",
            userId, firstName, lastName, username);

        TreeMap<String, String> params = new TreeMap<>();
        params.put("auth_date", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("query_id", "AAH" + System.nanoTime());
        params.put("user", userJson);

        StringBuilder checkString = new StringBuilder();
        boolean first = true;
        for (var e : params.entrySet()) {
            if (!first) checkString.append('\n');
            checkString.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        byte[] secretKey = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8),
            botToken.getBytes(StandardCharsets.UTF_8));
        byte[] hashBytes = hmacSha256(secretKey, checkString.toString().getBytes(StandardCharsets.UTF_8));
        String hash = toHex(hashBytes);

        StringBuilder qs = new StringBuilder();
        for (var e : params.entrySet()) {
            if (qs.length() > 0) qs.append('&');
            qs.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
              .append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        qs.append("&hash=").append(hash);
        return qs.toString();
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
```

Note: `application-test.yml` already has `bot.token` (might be `dev-token-not-set` or actual). Set it to a known test value if not already. Read `backend/app/src/test/resources/application-test.yml` — if `bot.token` is not set there, add:

```yaml
bot:
  token: TEST_BOT_TOKEN_FOR_HMAC
  username: TestBot
  mode: webhook  # avoid TelegramLongPollingBot startup in tests
```

(Existing test profile likely already has this from P1. Verify.)

- [ ] **Step 3: Verify all tests pass**

```bash
cd backend && ./mvnw verify
```

Expected: BUILD SUCCESS. Existing 74 + 5 verifier + 1 auth exc + 1 me controller + 1 new unauthorized = 82 tests.

- [ ] **Step 4: Commit**

```bash
git add backend/modules/order/src/main/java/com/shop/delivery/order/api/OrderController.java \
        backend/app/src/test/java/com/shop/delivery/OrderFlowIT.java \
        backend/app/src/test/resources/application-test.yml
git commit -m "refactor(order): replace X-Customer-Id header with @CurrentUser (Telegram initData)"
```

---

## TASK 5: Frontend Monorepo + Shared Package (1 commit)

**Files:**
- Create: `frontend/.gitignore`, `frontend/.npmrc`, `frontend/package.json`, `frontend/pnpm-workspace.yaml`
- Create: `frontend/shared/package.json`, `frontend/shared/tsconfig.json`
- Create: `frontend/shared/src/index.ts` + types + utils

- [ ] **Step 1: Check pnpm is installed**

```bash
which pnpm || npm install -g pnpm
pnpm --version  # should be 9+
```

If unavailable and user doesn't want global install: use `npx pnpm` everywhere.

- [ ] **Step 2: Write `frontend/.gitignore`**

```
node_modules/
dist/
.vite/
.turbo/
*.tsbuildinfo
.DS_Store
```

- [ ] **Step 3: Write `frontend/.npmrc`**

```
strict-peer-dependencies=false
auto-install-peers=true
shamefully-hoist=false
```

- [ ] **Step 4: Write `frontend/pnpm-workspace.yaml`**

```yaml
packages:
  - "shared"
  - "miniapp"
  # webadmin (P4)
```

- [ ] **Step 5: Write `frontend/package.json` (root)**

```json
{
  "name": "shop-delivery-frontend",
  "private": true,
  "version": "0.1.0",
  "scripts": {
    "miniapp:dev": "pnpm --filter @shop/miniapp dev",
    "miniapp:build": "pnpm --filter @shop/miniapp build",
    "shared:build": "pnpm --filter @shop/shared build",
    "lint": "pnpm -r run lint",
    "type-check": "pnpm -r run type-check"
  },
  "packageManager": "pnpm@9.12.0"
}
```

- [ ] **Step 6: Write `frontend/shared/package.json`**

```json
{
  "name": "@shop/shared",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "main": "./src/index.ts",
  "types": "./src/index.ts",
  "exports": {
    ".": "./src/index.ts",
    "./types": "./src/types/index.ts",
    "./api": "./src/api/index.ts",
    "./utils": "./src/utils/index.ts"
  },
  "scripts": {
    "type-check": "tsc --noEmit"
  },
  "dependencies": {
    "axios": "^1.7.7",
    "date-fns": "^3.6.0"
  },
  "devDependencies": {
    "typescript": "^5.6.0"
  }
}
```

- [ ] **Step 7: Write `frontend/shared/tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "lib": ["ES2022", "DOM"],
    "strict": true,
    "noUnusedLocals": true,
    "noFallthroughCasesInSwitch": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "declaration": true,
    "noEmit": true
  },
  "include": ["src/**/*"]
}
```

- [ ] **Step 8: Write shared type files**

Create `frontend/shared/src/types/product.ts`:

```typescript
export interface Product {
  id: number;
  name: string;
  description: string | null;
  price: number;  // VND
  imageUrl: string | null;
  stock: number;
  active: boolean;
  createdAt: string;  // ISO datetime
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;     // current page (0-indexed)
  size: number;
}
```

Create `frontend/shared/src/types/order.ts`:

```typescript
export type OrderStatus =
  | 'PENDING' | 'CONFIRMED' | 'ASSIGNED'
  | 'DELIVERING' | 'DELIVERED' | 'CANCELLED' | 'RETURNED';

export type PaymentMethod = 'COD' | 'VNPAY';
export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED';

export interface OrderItemResponse {
  id: number;
  productId: number;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface OrderResponse {
  id: string;          // UUID
  code: string;        // DH20260520-XXXXX
  customerId: number;
  customerName: string | null;
  customerPhone: string | null;
  deliveryAddress: string;
  deliveryLat: string;
  deliveryLng: string;
  distanceKm: string;
  subtotal: number;
  deliveryFee: number;
  total: number;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  status: OrderStatus;
  note: string | null;
  createdAt: string;
  items: OrderItemResponse[];
}

export interface OrderSummary {
  id: string;
  code: string;
  total: number;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  createdAt: string;
}

export interface CreateOrderRequest {
  customerName?: string;
  customerPhone?: string;
  deliveryAddress: string;
  deliveryLat: string;
  deliveryLng: string;
  items: { productId: number; quantity: number }[];
  paymentMethod: PaymentMethod;
  note?: string;
}
```

Create `frontend/shared/src/types/user.ts`:

```typescript
export type Role = 'CUSTOMER' | 'SHIPPER' | 'SHOP_OWNER';

export interface MeResponse {
  id: number;
  username: string | null;
  firstName: string | null;
  lastName: string | null;
  languageCode: string | null;
  roles: Role[];
}
```

Create `frontend/shared/src/types/api-error.ts`:

```typescript
export interface ApiError {
  code: string;
  message: string;
  traceId: string;
  timestamp: string;
  errors?: { field: string; message: string }[];
}
```

Create `frontend/shared/src/types/index.ts`:

```typescript
export * from './product';
export * from './order';
export * from './user';
export * from './api-error';
```

- [ ] **Step 9: Write shared utils**

Create `frontend/shared/src/utils/format-money.ts`:

```typescript
/**
 * Format VND. Default no decimals, "₫" suffix.
 * Example: 250000 → "250.000 ₫"
 */
export function formatVnd(amount: number | string): string {
  const n = typeof amount === 'string' ? Number(amount) : amount;
  if (!Number.isFinite(n)) return '0 ₫';
  return new Intl.NumberFormat('vi-VN').format(Math.round(n)) + ' ₫';
}
```

Create `frontend/shared/src/utils/format-date.ts`:

```typescript
import { format, formatDistanceToNow, parseISO } from 'date-fns';
import { vi } from 'date-fns/locale';

export function formatDateTime(iso: string): string {
  try {
    return format(parseISO(iso), "HH:mm dd/MM/yyyy", { locale: vi });
  } catch {
    return iso;
  }
}

export function formatRelative(iso: string): string {
  try {
    return formatDistanceToNow(parseISO(iso), { locale: vi, addSuffix: true });
  } catch {
    return iso;
  }
}
```

Create `frontend/shared/src/utils/index.ts`:

```typescript
export * from './format-money';
export * from './format-date';
```

- [ ] **Step 10: Write shared index (placeholder for api/)**

Create `frontend/shared/src/api/index.ts` (placeholder, fills in TASK 8):

```typescript
// Filled in TASK 8 (API client)
export {};
```

Create `frontend/shared/src/index.ts`:

```typescript
export * from './types';
export * from './utils';
```

- [ ] **Step 11: Install dependencies**

```bash
cd frontend && pnpm install
```

Expected: pnpm creates `node_modules/`, links workspace packages.

- [ ] **Step 12: Type-check shared**

```bash
cd frontend && pnpm shared:build
```

Expected: BUILD SUCCESS (no type errors).

- [ ] **Step 13: Commit**

```bash
git add frontend/
git commit -m "feat(frontend): setup pnpm workspace + shared package with types and utils"
```

Note: `frontend/.gitignore` excludes `node_modules/` so it won't be committed.

---

## TASK 6: Mini App Scaffold — Vite + React + TS + Tailwind (1 commit)

**Files:**
- Create: `frontend/miniapp/package.json`, `tsconfig.json`, `tsconfig.node.json`, `vite.config.ts`, `tailwind.config.ts`, `postcss.config.js`, `index.html`
- Create: `frontend/miniapp/src/main.tsx`, `src/App.tsx` (stub), `src/styles/globals.css`

- [ ] **Step 1: Write `frontend/miniapp/package.json`**

```json
{
  "name": "@shop/miniapp",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "type-check": "tsc --noEmit"
  },
  "dependencies": {
    "@shop/shared": "workspace:*",
    "@tanstack/react-query": "^5.59.0",
    "@twa-dev/sdk": "^7.10.0",
    "axios": "^1.7.7",
    "date-fns": "^3.6.0",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.27.0",
    "zustand": "^4.5.5"
  },
  "devDependencies": {
    "@types/react": "^18.3.11",
    "@types/react-dom": "^18.3.0",
    "@vitejs/plugin-react": "^4.3.2",
    "autoprefixer": "^10.4.20",
    "postcss": "^8.4.47",
    "tailwindcss": "^3.4.13",
    "typescript": "^5.6.0",
    "vite": "^5.4.8"
  }
}
```

- [ ] **Step 2: Write `frontend/miniapp/tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "Bundler",
    "allowImportingTsExtensions": false,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "esModuleInterop": true,
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

- [ ] **Step 3: Write `frontend/miniapp/tsconfig.node.json`**

```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true,
    "noEmit": true
  },
  "include": ["vite.config.ts", "tailwind.config.ts", "postcss.config.js"]
}
```

- [ ] **Step 4: Write `frontend/miniapp/vite.config.ts`**

```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    host: true,  // listen on 0.0.0.0 for ngrok
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
```

- [ ] **Step 5: Write `frontend/miniapp/tailwind.config.ts`**

```typescript
import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Telegram theme colors (CSS vars set by @twa-dev/sdk)
        tg: {
          bg: 'var(--tg-theme-bg-color, #ffffff)',
          text: 'var(--tg-theme-text-color, #000000)',
          hint: 'var(--tg-theme-hint-color, #999999)',
          link: 'var(--tg-theme-link-color, #2481cc)',
          button: 'var(--tg-theme-button-color, #2481cc)',
          buttonText: 'var(--tg-theme-button-text-color, #ffffff)',
          secondaryBg: 'var(--tg-theme-secondary-bg-color, #f0f0f0)',
        },
      },
    },
  },
  plugins: [],
} satisfies Config;
```

- [ ] **Step 6: Write `frontend/miniapp/postcss.config.js`**

```javascript
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
```

- [ ] **Step 7: Write `frontend/miniapp/index.html`**

```html
<!doctype html>
<html lang="vi">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover" />
    <title>Shop Giao Hàng</title>
    <script src="https://telegram.org/js/telegram-web-app.js"></script>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 8: Write `frontend/miniapp/src/styles/globals.css`**

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

:root {
  color-scheme: light dark;
}

html, body, #root {
  height: 100%;
  margin: 0;
  padding: 0;
  background: var(--tg-theme-bg-color, #ffffff);
  color: var(--tg-theme-text-color, #000000);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

* {
  -webkit-tap-highlight-color: transparent;
}
```

- [ ] **Step 9: Write `frontend/miniapp/src/main.tsx`**

```typescript
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './styles/globals.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
```

- [ ] **Step 10: Write `frontend/miniapp/src/App.tsx` (stub)**

```typescript
export default function App() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-tg-bg text-tg-text">
      <div className="text-center">
        <h1 className="text-2xl font-bold">Shop Giao Hàng</h1>
        <p className="text-tg-hint mt-2">Mini App đang khởi tạo...</p>
      </div>
    </div>
  );
}
```

- [ ] **Step 11: Install and dev-build to verify**

```bash
cd frontend && pnpm install
cd miniapp && pnpm build
```

Expected: `dist/` created, no errors. CSS bundled.

- [ ] **Step 12: Quick visual smoke (optional, dev server)**

```bash
cd frontend/miniapp && pnpm dev
```

Open `http://localhost:5173` in browser. Should see "Shop Giao Hàng / Mini App đang khởi tạo...". Tailwind dark mode follows OS preference. Stop server (Ctrl+C).

- [ ] **Step 13: Commit**

```bash
git add frontend/miniapp/ frontend/package.json frontend/pnpm-workspace.yaml
git commit -m "feat(miniapp): scaffold Vite + React + TypeScript + Tailwind app"
```

---

## TASK 7: TWA SDK Integration + Telegram Provider (1 commit)

**Files:**
- Create: `frontend/miniapp/src/lib/telegram.ts`
- Create: `frontend/miniapp/src/lib/auth.ts`
- Create: `frontend/miniapp/src/providers/TelegramProvider.tsx`

- [ ] **Step 1: Write `lib/telegram.ts`**

Create `frontend/miniapp/src/lib/telegram.ts`:

```typescript
import WebApp from '@twa-dev/sdk';

/**
 * Wrapper xung quanh Telegram WebApp SDK.
 * Khi mở Mini App từ Telegram → window.Telegram.WebApp có sẵn.
 * Khi mở từ browser thường → WebApp.initData rỗng → dev mode banner sẽ hiển thị.
 */
export const tg = {
  /** Có đang chạy trong Telegram thật không? (vs browser thường) */
  isInTelegram(): boolean {
    return Boolean(WebApp.initData && WebApp.initData.length > 0);
  },

  /** initData raw string để gửi backend verify */
  initData(): string {
    return WebApp.initData;
  },

  /** Pre-parsed user info (không trusted, chỉ để hiển thị tạm) */
  unsafeUser() {
    return WebApp.initDataUnsafe.user ?? null;
  },

  /** Báo Telegram Mini App đã sẵn sàng (loading splash biến mất) */
  ready() {
    WebApp.ready();
  },

  expand() {
    WebApp.expand();
  },

  close() {
    WebApp.close();
  },

  showAlert(message: string): Promise<void> {
    return new Promise(resolve => WebApp.showAlert(message, () => resolve()));
  },

  showConfirm(message: string): Promise<boolean> {
    return new Promise(resolve => WebApp.showConfirm(message, ok => resolve(ok)));
  },

  haptic: WebApp.HapticFeedback,
  mainButton: WebApp.MainButton,
  backButton: WebApp.BackButton,
  theme: () => WebApp.colorScheme,
};
```

- [ ] **Step 2: Write `lib/auth.ts`**

Create `frontend/miniapp/src/lib/auth.ts`:

```typescript
import { tg } from './telegram';

/** Header để gửi initData lên backend */
export const INIT_DATA_HEADER = 'X-Telegram-Init-Data';

export function getAuthHeaders(): Record<string, string> {
  const initData = tg.initData();
  if (!initData) return {};
  return { [INIT_DATA_HEADER]: initData };
}
```

- [ ] **Step 3: Write `providers/TelegramProvider.tsx`**

Create `frontend/miniapp/src/providers/TelegramProvider.tsx`:

```typescript
import { useEffect, type ReactNode } from 'react';
import { tg } from '@/lib/telegram';

interface Props {
  children: ReactNode;
}

/**
 * Khởi tạo Telegram WebApp lifecycle: gọi ready() để ẩn loading splash,
 * expand() để toàn màn hình.
 */
export function TelegramProvider({ children }: Props) {
  useEffect(() => {
    tg.ready();
    tg.expand();
  }, []);

  return <>{children}</>;
}
```

- [ ] **Step 4: Verify type-check**

```bash
cd frontend/miniapp && pnpm type-check
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/miniapp/src/lib/ frontend/miniapp/src/providers/TelegramProvider.tsx
git commit -m "feat(miniapp): add Telegram WebApp SDK wrapper + auth helpers"
```

---

## TASK 8: API Client + TanStack Query Provider (1 commit)

**Files:**
- Create: `frontend/shared/src/api/client.ts`, `products.ts`, `orders.ts`, `me.ts`, `index.ts`
- Create: `frontend/miniapp/src/lib/api.ts`
- Create: `frontend/miniapp/src/providers/QueryProvider.tsx`

- [ ] **Step 1: Write shared API client**

Create `frontend/shared/src/api/client.ts`:

```typescript
import axios, { type AxiosInstance } from 'axios';

export type GetHeaders = () => Record<string, string>;

export function createApiClient(baseURL: string, getHeaders: GetHeaders): AxiosInstance {
  const instance = axios.create({
    baseURL,
    timeout: 15000,
  });

  instance.interceptors.request.use(config => {
    const extra = getHeaders();
    for (const [k, v] of Object.entries(extra)) {
      config.headers.set(k, v);
    }
    return config;
  });

  return instance;
}
```

Create `frontend/shared/src/api/me.ts`:

```typescript
import type { AxiosInstance } from 'axios';
import type { MeResponse } from '../types';

export async function fetchMe(client: AxiosInstance): Promise<MeResponse> {
  const { data } = await client.get<MeResponse>('/api/me');
  return data;
}
```

Create `frontend/shared/src/api/products.ts`:

```typescript
import type { AxiosInstance } from 'axios';
import type { Page, Product } from '../types';

export async function listProducts(
  client: AxiosInstance,
  page = 0,
  size = 20
): Promise<Page<Product>> {
  const { data } = await client.get<Page<Product>>('/api/products', {
    params: { page, size },
  });
  return data;
}

export async function getProduct(client: AxiosInstance, id: number): Promise<Product> {
  const { data } = await client.get<Product>(`/api/products/${id}`);
  return data;
}
```

Create `frontend/shared/src/api/orders.ts`:

```typescript
import type { AxiosInstance } from 'axios';
import type { CreateOrderRequest, OrderResponse, OrderSummary, Page } from '../types';

export async function createOrder(
  client: AxiosInstance,
  req: CreateOrderRequest
): Promise<OrderResponse> {
  const { data } = await client.post<OrderResponse>('/api/orders', req);
  return data;
}

export async function listMyOrders(
  client: AxiosInstance,
  page = 0,
  size = 20
): Promise<Page<OrderSummary>> {
  const { data } = await client.get<Page<OrderSummary>>('/api/orders/mine', {
    params: { page, size },
  });
  return data;
}

export async function getOrder(client: AxiosInstance, id: string): Promise<OrderResponse> {
  const { data } = await client.get<OrderResponse>(`/api/orders/${id}`);
  return data;
}

export async function cancelOrder(
  client: AxiosInstance,
  id: string,
  reason?: string
): Promise<OrderResponse> {
  const { data } = await client.post<OrderResponse>(`/api/orders/${id}/cancel`, { reason });
  return data;
}
```

Replace `frontend/shared/src/api/index.ts`:

```typescript
export * from './client';
export * from './me';
export * from './products';
export * from './orders';
```

- [ ] **Step 2: Write miniapp `lib/api.ts`**

Create `frontend/miniapp/src/lib/api.ts`:

```typescript
import { createApiClient } from '@shop/shared';
import { getAuthHeaders } from './auth';

// In dev, Vite proxies /api → http://localhost:8080.
// In prod build, set VITE_API_BASE_URL env var to point to backend domain.
const baseURL = (import.meta.env.VITE_API_BASE_URL as string) ?? '';

export const api = createApiClient(baseURL, getAuthHeaders);
```

- [ ] **Step 3: Write `providers/QueryProvider.tsx`**

Create `frontend/miniapp/src/providers/QueryProvider.tsx`:

```typescript
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
}

export function QueryProvider({ children }: Props) {
  const [client] = useState(() => new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        gcTime: 5 * 60 * 1000,
        retry: 1,
        refetchOnWindowFocus: false,
      },
      mutations: { retry: 0 },
    },
  }));
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
```

- [ ] **Step 4: Type-check + install**

```bash
cd frontend && pnpm install  # in case anything new
cd shared && pnpm type-check
cd ../miniapp && pnpm type-check
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/shared/src/api/ frontend/miniapp/src/lib/api.ts frontend/miniapp/src/providers/QueryProvider.tsx
git commit -m "feat(frontend): add Axios API client + TanStack Query provider"
```

---

## TASK 9: Router + Splash Page + Layout (1 commit)

**Files:**
- Update: `frontend/miniapp/src/App.tsx`
- Create: `frontend/miniapp/src/pages/SplashPage.tsx`, `NotFoundPage.tsx`
- Create: `frontend/miniapp/src/components/Layout.tsx`, `ErrorBoundary.tsx`

- [ ] **Step 1: Write `components/Layout.tsx`**

Create `frontend/miniapp/src/components/Layout.tsx`:

```typescript
import { Outlet } from 'react-router-dom';

export function Layout() {
  return (
    <div className="min-h-screen bg-tg-bg text-tg-text">
      <main className="max-w-md mx-auto px-4 pt-4 pb-24">
        <Outlet />
      </main>
    </div>
  );
}
```

- [ ] **Step 2: Write `components/ErrorBoundary.tsx`**

Create `frontend/miniapp/src/components/ErrorBoundary.tsx`:

```typescript
import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props { children: ReactNode }
interface State { error: Error | null }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('ErrorBoundary caught:', error, info);
  }

  render() {
    if (this.state.error) {
      return (
        <div className="p-6 text-center">
          <h2 className="text-xl font-bold mb-2">Đã có lỗi xảy ra</h2>
          <p className="text-tg-hint text-sm mb-4">{this.state.error.message}</p>
          <button
            onClick={() => window.location.reload()}
            className="px-4 py-2 bg-tg-button text-tg-buttonText rounded-md"
          >
            Tải lại
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
```

- [ ] **Step 3: Write `pages/SplashPage.tsx`**

Create `frontend/miniapp/src/pages/SplashPage.tsx`:

```typescript
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchMe } from '@shop/shared';
import { api } from '@/lib/api';
import { tg } from '@/lib/telegram';

export function SplashPage() {
  const navigate = useNavigate();

  const { data, isLoading, error } = useQuery({
    queryKey: ['me'],
    queryFn: () => fetchMe(api),
    enabled: tg.isInTelegram(),
    retry: 0,
  });

  useEffect(() => {
    if (data) {
      // Route theo role
      if (data.roles.includes('SHIPPER')) {
        navigate('/shipper/assignments', { replace: true });
      } else {
        navigate('/customer/shop', { replace: true });
      }
    }
  }, [data, navigate]);

  if (!tg.isInTelegram()) {
    return (
      <div className="p-6 text-center">
        <div className="bg-yellow-100 text-yellow-800 rounded-md p-4 mb-4">
          ⚠️ Đang chạy ngoài Telegram (dev mode)
        </div>
        <h1 className="text-2xl font-bold mb-2">Shop Giao Hàng</h1>
        <p className="text-tg-hint mb-6">
          Mở Mini App qua Telegram để dùng bình thường. Chế độ dev cho phép xem UI nhưng các API gọi sẽ trả 401.
        </p>
        <button
          onClick={() => navigate('/customer/shop')}
          className="px-4 py-2 bg-tg-button text-tg-buttonText rounded-md"
        >
          Vào catalog (dev)
        </button>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="p-6 text-center">
        <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-tg-button"></div>
        <p className="mt-4 text-tg-hint">Đang xác thực...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6 text-center">
        <h2 className="text-xl font-bold mb-2">Xác thực thất bại</h2>
        <p className="text-tg-hint text-sm mb-4">
          Không thể xác thực với server. Vui lòng đóng và mở lại Mini App.
        </p>
      </div>
    );
  }

  return null;
}
```

- [ ] **Step 4: Write `pages/NotFoundPage.tsx`**

Create `frontend/miniapp/src/pages/NotFoundPage.tsx`:

```typescript
import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="p-6 text-center">
      <h2 className="text-xl font-bold mb-2">Không tìm thấy</h2>
      <Link to="/" className="text-tg-link">Về trang chủ</Link>
    </div>
  );
}
```

- [ ] **Step 5: Update `App.tsx` with router**

Replace `frontend/miniapp/src/App.tsx`:

```typescript
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { TelegramProvider } from './providers/TelegramProvider';
import { QueryProvider } from './providers/QueryProvider';
import { ErrorBoundary } from './components/ErrorBoundary';
import { Layout } from './components/Layout';
import { SplashPage } from './pages/SplashPage';
import { NotFoundPage } from './pages/NotFoundPage';

export default function App() {
  return (
    <ErrorBoundary>
      <QueryProvider>
        <TelegramProvider>
          <BrowserRouter>
            <Routes>
              <Route element={<Layout />}>
                <Route index element={<SplashPage />} />
                {/* customer + shipper routes filled in next tasks */}
                <Route path="*" element={<NotFoundPage />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </TelegramProvider>
      </QueryProvider>
    </ErrorBoundary>
  );
}
```

- [ ] **Step 6: Type-check + dev smoke**

```bash
cd frontend/miniapp && pnpm type-check && pnpm build
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add frontend/miniapp/src/
git commit -m "feat(miniapp): add router, splash page, layout, error boundary"
```

---

## TASK 10: Customer Catalog Page (1 commit)

**Files:**
- Create: `frontend/miniapp/src/pages/CatalogPage.tsx`
- Create: `frontend/miniapp/src/components/ProductCard.tsx`
- Update: `frontend/miniapp/src/App.tsx` — add route

- [ ] **Step 1: Write `components/ProductCard.tsx`**

Create `frontend/miniapp/src/components/ProductCard.tsx`:

```typescript
import type { Product } from '@shop/shared';
import { formatVnd } from '@shop/shared';
import { useCart } from '@/features/cart/use-cart';

interface Props { product: Product }

export function ProductCard({ product }: Props) {
  const { add, getQuantity } = useCart();
  const qty = getQuantity(product.id);

  return (
    <div className="bg-tg-secondaryBg rounded-lg p-3 flex gap-3">
      {product.imageUrl ? (
        <img
          src={product.imageUrl}
          alt={product.name}
          className="w-20 h-20 rounded-md object-cover bg-tg-hint/20"
          loading="lazy"
        />
      ) : (
        <div className="w-20 h-20 rounded-md bg-tg-hint/20 flex items-center justify-center text-tg-hint text-xs">
          No image
        </div>
      )}
      <div className="flex-1 min-w-0">
        <h3 className="font-semibold truncate">{product.name}</h3>
        {product.description && (
          <p className="text-sm text-tg-hint line-clamp-2 mt-0.5">{product.description}</p>
        )}
        <div className="mt-2 flex items-center justify-between">
          <span className="font-bold text-tg-button">{formatVnd(product.price)}</span>
          <button
            onClick={() => add(product, 1)}
            className="px-3 py-1 text-sm bg-tg-button text-tg-buttonText rounded-md disabled:opacity-50"
            disabled={product.stock <= 0}
          >
            {qty > 0 ? `Thêm (${qty})` : product.stock <= 0 ? 'Hết hàng' : 'Thêm'}
          </button>
        </div>
      </div>
    </div>
  );
}
```

Note: `useCart` not implemented yet (TASK 11). For this task, the import will fail at runtime. Implement TASK 11 first or stub `useCart`. Better: implement TASK 11 inline as part of this task since the cart is used by `ProductCard` directly. **Decision: do TASK 11's cart store as part of TASK 10** (combine them).

Actually keeping them separate keeps commits atomic per the plan. **Stub the cart for this task**:

Modify import to provide a stub. Actually let me restructure: keep cart store creation in TASK 11 but have `ProductCard` import from a path that exists at this point. Simpler: do TASK 11's cart store inline here.

OK let me merge TASK 10 and TASK 11 into one combined task: "Catalog + Cart". The plan structure is mine to set anyway.

- [ ] **Step 2: Write `features/cart/cart-store.ts`** (cart Zustand store — was TASK 11)

Create `frontend/miniapp/src/features/cart/cart-store.ts`:

```typescript
import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { Product } from '@shop/shared';

export interface CartItem {
  product: Product;
  quantity: number;
}

interface CartState {
  items: CartItem[];
  add: (product: Product, quantity: number) => void;
  remove: (productId: number) => void;
  setQuantity: (productId: number, quantity: number) => void;
  clear: () => void;
  getQuantity: (productId: number) => number;
  subtotal: () => number;
  totalItems: () => number;
}

export const useCartStore = create<CartState>()(
  persist(
    (set, get) => ({
      items: [],

      add: (product, quantity) => set(state => {
        const existing = state.items.find(i => i.product.id === product.id);
        if (existing) {
          return {
            items: state.items.map(i =>
              i.product.id === product.id
                ? { ...i, quantity: i.quantity + quantity }
                : i
            ),
          };
        }
        return { items: [...state.items, { product, quantity }] };
      }),

      remove: productId => set(state => ({
        items: state.items.filter(i => i.product.id !== productId),
      })),

      setQuantity: (productId, quantity) => set(state => {
        if (quantity <= 0) {
          return { items: state.items.filter(i => i.product.id !== productId) };
        }
        return {
          items: state.items.map(i =>
            i.product.id === productId ? { ...i, quantity } : i
          ),
        };
      }),

      clear: () => set({ items: [] }),

      getQuantity: productId =>
        get().items.find(i => i.product.id === productId)?.quantity ?? 0,

      subtotal: () =>
        get().items.reduce((sum, i) => sum + i.product.price * i.quantity, 0),

      totalItems: () =>
        get().items.reduce((sum, i) => sum + i.quantity, 0),
    }),
    {
      name: 'shop-cart',
      storage: createJSONStorage(() => localStorage),
    }
  )
);
```

- [ ] **Step 3: Write `features/cart/use-cart.ts`** (hook wrapper)

Create `frontend/miniapp/src/features/cart/use-cart.ts`:

```typescript
import { useCartStore } from './cart-store';

export function useCart() {
  return useCartStore();
}
```

- [ ] **Step 4: Write `pages/CatalogPage.tsx`**

Create `frontend/miniapp/src/pages/CatalogPage.tsx`:

```typescript
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { listProducts } from '@shop/shared';
import { api } from '@/lib/api';
import { ProductCard } from '@/components/ProductCard';
import { useCart } from '@/features/cart/use-cart';
import { formatVnd } from '@shop/shared';

export function CatalogPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['products'],
    queryFn: () => listProducts(api, 0, 50),
  });
  const cart = useCart();

  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Sản phẩm</h1>

      {isLoading && <p className="text-tg-hint">Đang tải...</p>}
      {error && <p className="text-red-500">Không tải được danh sách sản phẩm.</p>}

      <div className="space-y-3">
        {data?.content.map(p => (
          <ProductCard key={p.id} product={p} />
        ))}
      </div>

      {cart.totalItems() > 0 && (
        <Link
          to="/customer/cart"
          className="fixed bottom-4 left-4 right-4 max-w-md mx-auto bg-tg-button text-tg-buttonText rounded-lg py-3 px-4 flex items-center justify-between shadow-lg"
        >
          <span>🛒 Giỏ hàng ({cart.totalItems()})</span>
          <span className="font-bold">{formatVnd(cart.subtotal())}</span>
        </Link>
      )}
    </div>
  );
}
```

- [ ] **Step 5: Update `App.tsx` — add route**

Replace `frontend/miniapp/src/App.tsx`:

```typescript
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { TelegramProvider } from './providers/TelegramProvider';
import { QueryProvider } from './providers/QueryProvider';
import { ErrorBoundary } from './components/ErrorBoundary';
import { Layout } from './components/Layout';
import { SplashPage } from './pages/SplashPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { CatalogPage } from './pages/CatalogPage';

export default function App() {
  return (
    <ErrorBoundary>
      <QueryProvider>
        <TelegramProvider>
          <BrowserRouter>
            <Routes>
              <Route element={<Layout />}>
                <Route index element={<SplashPage />} />
                <Route path="customer/shop" element={<CatalogPage />} />
                <Route path="*" element={<NotFoundPage />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </TelegramProvider>
      </QueryProvider>
    </ErrorBoundary>
  );
}
```

- [ ] **Step 6: Type-check + build**

```bash
cd frontend/miniapp && pnpm type-check && pnpm build
```

- [ ] **Step 7: Commit**

```bash
git add frontend/miniapp/src/
git commit -m "feat(miniapp): add catalog page + cart store (Zustand) + product card"
```

---

## TASK 11: Cart Page (1 commit)

**Files:**
- Create: `frontend/miniapp/src/pages/CartPage.tsx`
- Update: `App.tsx` — add route

- [ ] **Step 1: Write `pages/CartPage.tsx`**

Create `frontend/miniapp/src/pages/CartPage.tsx`:

```typescript
import { Link } from 'react-router-dom';
import { formatVnd } from '@shop/shared';
import { useCart } from '@/features/cart/use-cart';

export function CartPage() {
  const cart = useCart();

  if (cart.items.length === 0) {
    return (
      <div>
        <h1 className="text-2xl font-bold mb-4">Giỏ hàng</h1>
        <div className="text-center py-12">
          <p className="text-tg-hint mb-4">Giỏ hàng trống</p>
          <Link to="/customer/shop" className="text-tg-link">Quay lại mua hàng</Link>
        </div>
      </div>
    );
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Giỏ hàng</h1>

      <div className="space-y-3">
        {cart.items.map(item => (
          <div key={item.product.id} className="bg-tg-secondaryBg rounded-lg p-3 flex gap-3">
            {item.product.imageUrl ? (
              <img
                src={item.product.imageUrl}
                alt={item.product.name}
                className="w-16 h-16 rounded-md object-cover bg-tg-hint/20"
              />
            ) : (
              <div className="w-16 h-16 rounded-md bg-tg-hint/20" />
            )}
            <div className="flex-1 min-w-0">
              <h3 className="font-medium truncate">{item.product.name}</h3>
              <p className="text-sm text-tg-hint">{formatVnd(item.product.price)}</p>
              <div className="flex items-center gap-2 mt-2">
                <button
                  onClick={() => cart.setQuantity(item.product.id, item.quantity - 1)}
                  className="w-7 h-7 rounded-full bg-tg-hint/20 flex items-center justify-center"
                  aria-label="Giảm"
                >
                  −
                </button>
                <span className="w-8 text-center font-medium">{item.quantity}</span>
                <button
                  onClick={() => cart.setQuantity(item.product.id, item.quantity + 1)}
                  className="w-7 h-7 rounded-full bg-tg-hint/20 flex items-center justify-center"
                  aria-label="Tăng"
                >
                  +
                </button>
                <button
                  onClick={() => cart.remove(item.product.id)}
                  className="ml-auto text-sm text-red-500"
                >
                  Xóa
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-6 bg-tg-secondaryBg rounded-lg p-4">
        <div className="flex justify-between text-tg-hint">
          <span>Tạm tính</span>
          <span>{formatVnd(cart.subtotal())}</span>
        </div>
        <div className="flex justify-between text-tg-hint text-sm mt-1">
          <span>Phí ship sẽ tính khi nhập địa chỉ</span>
        </div>
      </div>

      <Link
        to="/customer/checkout"
        className="fixed bottom-4 left-4 right-4 max-w-md mx-auto bg-tg-button text-tg-buttonText rounded-lg py-3 px-4 text-center font-medium shadow-lg"
      >
        Đặt hàng
      </Link>
    </div>
  );
}
```

- [ ] **Step 2: Update `App.tsx` — add /customer/cart route**

Add the import and route. Read existing `App.tsx`, then update the Routes block to include:

```typescript
import { CartPage } from './pages/CartPage';
// ...
<Route path="customer/cart" element={<CartPage />} />
```

(Add after the catalog route.)

- [ ] **Step 3: Build**

```bash
cd frontend/miniapp && pnpm build
```

- [ ] **Step 4: Commit**

```bash
git add frontend/miniapp/src/
git commit -m "feat(miniapp): add cart page with quantity controls"
```

---

## TASK 12: Checkout Page (COD only) (1 commit)

**Files:**
- Create: `frontend/miniapp/src/pages/CheckoutPage.tsx`
- Update: `App.tsx` — add /customer/checkout route

- [ ] **Step 1: Write `pages/CheckoutPage.tsx`**

Create `frontend/miniapp/src/pages/CheckoutPage.tsx`:

```typescript
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { createOrder, formatVnd, type CreateOrderRequest, type PaymentMethod } from '@shop/shared';
import { api } from '@/lib/api';
import { useCart } from '@/features/cart/use-cart';
import { tg } from '@/lib/telegram';

export function CheckoutPage() {
  const navigate = useNavigate();
  const cart = useCart();

  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [deliveryAddress, setDeliveryAddress] = useState('');
  const [deliveryLat, setDeliveryLat] = useState('21.0193');
  const [deliveryLng, setDeliveryLng] = useState('105.8503');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('COD');
  const [note, setNote] = useState('');

  const placeOrder = useMutation({
    mutationFn: (req: CreateOrderRequest) => createOrder(api, req),
    onSuccess: order => {
      cart.clear();
      navigate(`/customer/orders/${order.id}`, { replace: true });
    },
    onError: async (err: any) => {
      const msg = err.response?.data?.message ?? 'Đặt đơn thất bại';
      if (tg.isInTelegram()) await tg.showAlert(msg);
      else alert(msg);
    },
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (cart.items.length === 0) return;
    placeOrder.mutate({
      customerName: customerName || undefined,
      customerPhone: customerPhone || undefined,
      deliveryAddress,
      deliveryLat,
      deliveryLng,
      items: cart.items.map(i => ({ productId: i.product.id, quantity: i.quantity })),
      paymentMethod,
      note: note || undefined,
    });
  };

  if (cart.items.length === 0) {
    return (
      <div className="text-center py-12">
        <p className="text-tg-hint">Giỏ hàng trống</p>
      </div>
    );
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Xác nhận đặt hàng</h1>

      <form onSubmit={submit} className="space-y-4">
        <div className="bg-tg-secondaryBg rounded-lg p-3">
          <p className="text-sm text-tg-hint mb-2">Sản phẩm ({cart.totalItems()})</p>
          {cart.items.map(i => (
            <div key={i.product.id} className="flex justify-between py-1 text-sm">
              <span>{i.product.name} × {i.quantity}</span>
              <span>{formatVnd(i.product.price * i.quantity)}</span>
            </div>
          ))}
          <div className="border-t border-tg-hint/20 mt-2 pt-2 flex justify-between font-bold">
            <span>Tạm tính</span>
            <span>{formatVnd(cart.subtotal())}</span>
          </div>
        </div>

        <label className="block">
          <span className="text-sm text-tg-hint">Tên người nhận</span>
          <input
            type="text"
            value={customerName}
            onChange={e => setCustomerName(e.target.value)}
            placeholder="Để trống = dùng tên Telegram"
            className="mt-1 w-full px-3 py-2 rounded-md bg-tg-secondaryBg border border-tg-hint/30"
          />
        </label>

        <label className="block">
          <span className="text-sm text-tg-hint">SĐT</span>
          <input
            type="tel"
            value={customerPhone}
            onChange={e => setCustomerPhone(e.target.value)}
            placeholder="+849xxxxxxxx"
            className="mt-1 w-full px-3 py-2 rounded-md bg-tg-secondaryBg border border-tg-hint/30"
          />
        </label>

        <label className="block">
          <span className="text-sm text-tg-hint">Địa chỉ giao *</span>
          <input
            type="text"
            value={deliveryAddress}
            onChange={e => setDeliveryAddress(e.target.value)}
            required
            placeholder="Số nhà, đường, phường, quận"
            className="mt-1 w-full px-3 py-2 rounded-md bg-tg-secondaryBg border border-tg-hint/30"
          />
          <p className="text-xs text-tg-hint mt-1">
            (P6 sẽ thay bằng map picker — hiện gửi tọa độ default Bà Triệu)
          </p>
        </label>

        <fieldset className="block">
          <legend className="text-sm text-tg-hint mb-2">Phương thức thanh toán</legend>
          <label className="flex items-center gap-2 py-2">
            <input
              type="radio"
              name="payment"
              value="COD"
              checked={paymentMethod === 'COD'}
              onChange={() => setPaymentMethod('COD')}
            />
            <span>💰 Thanh toán khi nhận hàng (COD)</span>
          </label>
          <label className="flex items-center gap-2 py-2 opacity-50">
            <input type="radio" name="payment" value="VNPAY" disabled />
            <span>💳 VNPay (sắp có — P7)</span>
          </label>
        </fieldset>

        <label className="block">
          <span className="text-sm text-tg-hint">Ghi chú</span>
          <textarea
            value={note}
            onChange={e => setNote(e.target.value)}
            placeholder="Vd: Giao tối 6-8h, gọi trước khi đến..."
            className="mt-1 w-full px-3 py-2 rounded-md bg-tg-secondaryBg border border-tg-hint/30"
            rows={2}
          />
        </label>

        <button
          type="submit"
          disabled={placeOrder.isPending}
          className="w-full py-3 bg-tg-button text-tg-buttonText rounded-lg font-medium disabled:opacity-50"
        >
          {placeOrder.isPending ? 'Đang đặt...' : `Đặt hàng (${formatVnd(cart.subtotal())} + phí ship)`}
        </button>
      </form>
    </div>
  );
}
```

- [ ] **Step 2: Update `App.tsx`**

Read `App.tsx` and add route + import:

```typescript
import { CheckoutPage } from './pages/CheckoutPage';
// ...
<Route path="customer/checkout" element={<CheckoutPage />} />
```

- [ ] **Step 3: Build**

```bash
cd frontend/miniapp && pnpm build
```

- [ ] **Step 4: Commit**

```bash
git add frontend/miniapp/src/
git commit -m "feat(miniapp): add checkout page with COD flow"
```

---

## TASK 13: Orders List + Detail (1 commit)

**Files:**
- Create: `frontend/miniapp/src/pages/OrdersPage.tsx`, `OrderDetailPage.tsx`
- Create: `frontend/miniapp/src/components/OrderStatusBadge.tsx`
- Update: `App.tsx`

- [ ] **Step 1: Write `components/OrderStatusBadge.tsx`**

Create `frontend/miniapp/src/components/OrderStatusBadge.tsx`:

```typescript
import type { OrderStatus } from '@shop/shared';

const LABELS: Record<OrderStatus, { label: string; className: string }> = {
  PENDING:    { label: 'Chờ xác nhận', className: 'bg-yellow-100 text-yellow-800' },
  CONFIRMED:  { label: 'Đã xác nhận',  className: 'bg-blue-100 text-blue-800' },
  ASSIGNED:   { label: 'Đã gán shipper', className: 'bg-indigo-100 text-indigo-800' },
  DELIVERING: { label: 'Đang giao',    className: 'bg-purple-100 text-purple-800' },
  DELIVERED:  { label: 'Đã giao',      className: 'bg-green-100 text-green-800' },
  CANCELLED:  { label: 'Đã hủy',       className: 'bg-gray-100 text-gray-800' },
  RETURNED:   { label: 'Hoàn hàng',    className: 'bg-red-100 text-red-800' },
};

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const info = LABELS[status];
  return (
    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${info.className}`}>
      {info.label}
    </span>
  );
}
```

- [ ] **Step 2: Write `pages/OrdersPage.tsx`**

Create `frontend/miniapp/src/pages/OrdersPage.tsx`:

```typescript
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { listMyOrders, formatVnd, formatRelative } from '@shop/shared';
import { api } from '@/lib/api';
import { OrderStatusBadge } from '@/components/OrderStatusBadge';

export function OrdersPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['orders', 'mine'],
    queryFn: () => listMyOrders(api, 0, 50),
  });

  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Đơn hàng của tôi</h1>

      {isLoading && <p className="text-tg-hint">Đang tải...</p>}
      {error && <p className="text-red-500">Không tải được lịch sử đơn.</p>}

      {data && data.content.length === 0 && (
        <div className="text-center py-12">
          <p className="text-tg-hint mb-4">Chưa có đơn nào</p>
          <Link to="/customer/shop" className="text-tg-link">Bắt đầu mua hàng</Link>
        </div>
      )}

      <div className="space-y-3">
        {data?.content.map(o => (
          <Link
            key={o.id}
            to={`/customer/orders/${o.id}`}
            className="block bg-tg-secondaryBg rounded-lg p-3"
          >
            <div className="flex justify-between items-start">
              <div className="min-w-0">
                <p className="font-medium">{o.code}</p>
                <p className="text-xs text-tg-hint mt-0.5">{formatRelative(o.createdAt)}</p>
              </div>
              <OrderStatusBadge status={o.status} />
            </div>
            <div className="mt-2 flex justify-between items-end">
              <span className="text-sm text-tg-hint">{o.paymentMethod}</span>
              <span className="font-bold">{formatVnd(o.total)}</span>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Write `pages/OrderDetailPage.tsx`**

Create `frontend/miniapp/src/pages/OrderDetailPage.tsx`:

```typescript
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getOrder, cancelOrder, formatVnd, formatDateTime } from '@shop/shared';
import { api } from '@/lib/api';
import { OrderStatusBadge } from '@/components/OrderStatusBadge';
import { tg } from '@/lib/telegram';

const CANCELLABLE = new Set(['PENDING', 'CONFIRMED']);

export function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();

  const { data: order, isLoading, error } = useQuery({
    queryKey: ['order', id],
    queryFn: () => getOrder(api, id!),
    enabled: !!id,
  });

  const cancelMut = useMutation({
    mutationFn: (reason: string) => cancelOrder(api, id!, reason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['order', id] });
      qc.invalidateQueries({ queryKey: ['orders', 'mine'] });
    },
    onError: async (err: any) => {
      const msg = err.response?.data?.message ?? 'Không hủy được đơn';
      if (tg.isInTelegram()) await tg.showAlert(msg);
      else alert(msg);
    },
  });

  const handleCancel = async () => {
    const ok = tg.isInTelegram()
      ? await tg.showConfirm('Bạn có chắc muốn hủy đơn này?')
      : confirm('Bạn có chắc muốn hủy đơn này?');
    if (!ok) return;
    cancelMut.mutate('Khách hủy');
  };

  if (isLoading) return <p className="text-tg-hint">Đang tải...</p>;
  if (error || !order) {
    return (
      <div>
        <p className="text-red-500 mb-4">Không tải được đơn.</p>
        <Link to="/customer/orders" className="text-tg-link">Về danh sách đơn</Link>
      </div>
    );
  }

  return (
    <div>
      <button
        onClick={() => navigate(-1)}
        className="mb-4 text-tg-link"
      >
        ← Quay lại
      </button>

      <div className="flex justify-between items-start mb-4">
        <div>
          <h1 className="text-2xl font-bold">{order.code}</h1>
          <p className="text-xs text-tg-hint mt-1">{formatDateTime(order.createdAt)}</p>
        </div>
        <OrderStatusBadge status={order.status} />
      </div>

      <div className="bg-tg-secondaryBg rounded-lg p-4 mb-4">
        <h2 className="font-semibold mb-2">Sản phẩm</h2>
        {order.items.map(i => (
          <div key={i.id} className="flex justify-between py-1 text-sm">
            <span>SP #{i.productId} × {i.quantity}</span>
            <span>{formatVnd(i.subtotal)}</span>
          </div>
        ))}
        <div className="border-t border-tg-hint/20 mt-2 pt-2 space-y-1 text-sm">
          <div className="flex justify-between">
            <span className="text-tg-hint">Tạm tính</span>
            <span>{formatVnd(order.subtotal)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-tg-hint">Phí ship ({order.distanceKm}km)</span>
            <span>{formatVnd(order.deliveryFee)}</span>
          </div>
          <div className="flex justify-between font-bold pt-1 border-t border-tg-hint/20">
            <span>Tổng</span>
            <span>{formatVnd(order.total)}</span>
          </div>
        </div>
      </div>

      <div className="bg-tg-secondaryBg rounded-lg p-4 mb-4">
        <h2 className="font-semibold mb-2">Giao đến</h2>
        <p className="text-sm">{order.deliveryAddress}</p>
        {order.customerPhone && (
          <p className="text-sm text-tg-hint mt-1">SĐT: {order.customerPhone}</p>
        )}
        {order.note && (
          <p className="text-sm text-tg-hint mt-2 italic">Ghi chú: {order.note}</p>
        )}
      </div>

      <div className="bg-tg-secondaryBg rounded-lg p-4 mb-4 text-sm">
        <div className="flex justify-between">
          <span className="text-tg-hint">Thanh toán</span>
          <span>{order.paymentMethod}</span>
        </div>
        <div className="flex justify-between mt-1">
          <span className="text-tg-hint">Trạng thái thanh toán</span>
          <span>{order.paymentStatus}</span>
        </div>
      </div>

      {CANCELLABLE.has(order.status) && (
        <button
          onClick={handleCancel}
          disabled={cancelMut.isPending}
          className="w-full py-3 border border-red-500 text-red-500 rounded-lg font-medium disabled:opacity-50"
        >
          {cancelMut.isPending ? 'Đang hủy...' : 'Hủy đơn'}
        </button>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Update `App.tsx`**

Read `App.tsx`, then add imports + routes:

```typescript
import { OrdersPage } from './pages/OrdersPage';
import { OrderDetailPage } from './pages/OrderDetailPage';
// ...
<Route path="customer/orders" element={<OrdersPage />} />
<Route path="customer/orders/:id" element={<OrderDetailPage />} />
```

- [ ] **Step 5: Build**

```bash
cd frontend/miniapp && pnpm build
```

- [ ] **Step 6: Commit**

```bash
git add frontend/miniapp/src/
git commit -m "feat(miniapp): add orders list + order detail pages with cancel action"
```

---

## TASK 14: Smoke Test (verification, no commit)

Test full backend + Mini App end-to-end with real Telegram.

**Prerequisites:**
- `BOT_TOKEN`, `BOT_USERNAME` in `.env`
- ngrok account (free) OR cloudflared
- Backend on port 8080, Mini App dev server on port 5173

- [ ] **Step 1: Install ngrok if needed**

```bash
which ngrok || brew install --cask ngrok
ngrok config add-authtoken <YOUR_NGROK_TOKEN>  # from dashboard.ngrok.com
```

- [ ] **Step 2: Start backend**

```bash
cd backend/app && export $(cat ../../.env | xargs) && ../mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Wait for `Started Application`.

- [ ] **Step 3: Start Mini App dev server**

```bash
cd frontend/miniapp && pnpm dev
```

Wait for `Local: http://localhost:5173/`.

- [ ] **Step 4: Start ngrok tunnel**

```bash
ngrok http 5173
```

Note the HTTPS URL, e.g. `https://abc123.ngrok-free.app`.

- [ ] **Step 5: Configure Bot Menu Button**

In Telegram:
1. Open `@BotFather` → `/mybots` → `@shop_giaohang_bot` → "Bot Settings" → "Menu Button"
2. Title: `🛒 Đặt hàng`
3. URL: `https://abc123.ngrok-free.app` (from ngrok)
4. Save

- [ ] **Step 6: Test in real Telegram**

On phone:
1. Open `@shop_giaohang_bot`
2. Tap the Menu Button (or `/start` and reply prompts)
3. Mini App opens fullscreen with splash → routes to /customer/shop
4. Browse products → tap "Thêm" → cart counter increments
5. Tap cart pill at bottom → see items, quantity controls
6. Tap "Đặt hàng" → fill form (or accept defaults) → COD → submit
7. Expected: redirect to /customer/orders/:id with order detail
8. Check DB: `docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "SELECT id, code, status, customer_id, total FROM orders ORDER BY created_at DESC LIMIT 3"`
9. Check `/customer/orders` shows the new order

- [ ] **Step 7: Test cancel flow**

- Tap "Hủy đơn" on PENDING order → confirm → order moves to CANCELLED
- Status badge updates to "Đã hủy"
- DB shows status_history row: PENDING → CANCELLED

- [ ] **Step 8: Stop everything**

`Ctrl+C` on backend, miniapp dev, ngrok.

---

## Acceptance Criteria (P3 done when ALL true)

- [x] `./mvnw clean verify` BUILD SUCCESS (82+ tests)
- [x] `pnpm -r build` from frontend root BUILD SUCCESS
- [x] OrderFlowIT now uses signed initData instead of X-Customer-Id
- [x] /api/me returns user info + roles when valid initData provided
- [x] /api/orders/* returns 401 without initData (verify via curl or new IT)
- [x] Mini App in Telegram:
  - Loads from menu button → splash → catalog
  - Catalog shows products from real backend
  - Cart persists across page reloads (localStorage)
  - Checkout creates order successfully (COD)
  - Order list shows the new order
  - Order detail shows items, total, status badge
  - Cancel button works on PENDING orders
- [x] No regressions: P0/P1/P2 tests still pass

---

## Known Limitations of P3 (giải quyết ở plan sau)

| Limitation | Plan |
|---|---|
| Delivery address picker uses static lat/lng | P6 (Live Location + Leaflet map picker) |
| VNPay button disabled | P7 (VNPay integration) |
| StartHandler may not yet have MiniApp button (Bot Menu Button hardcoded via BotFather) | Optional polish — auto-set webhook URL on bot start (P9) |
| Admin endpoints still no auth | P4 (Web Admin + JWT) |
| Shipper flow in Mini App | P5 (Delivery lifecycle + Mini App shipper screens) |
| No realtime updates (must refresh to see status change) | P6 (WebSocket subscription) |
| No notifications when shop confirms order | P5 (event-driven Telegram bot notifications) |
| Order list doesn't show product names (only IDs) | Optional improvement — join product table |

---

## Troubleshooting

**`401 Unauthorized` on /api/me:**
- Check `X-Telegram-Init-Data` header is sent and non-empty
- Check backend `bot.token` matches the bot that issued the initData
- Verify backend filter is being invoked (logs)

**Mini App shows "Đang chạy ngoài Telegram":**
- You opened Mini App directly in browser, not via Telegram
- Ensure ngrok URL is set in BotFather → Menu Button

**`Network Error` on API calls in Mini App:**
- Vite proxy `/api → :8080` works in `pnpm dev` only
- For prod build → ngrok tunnels miniapp port 5173 → Mini App tries to call `/api` on ngrok URL → 404
- Solution dev: use `pnpm dev` (not build) + ngrok exposes Vite dev which proxies to backend
- Solution prod: backend serves Mini App from same domain, or set `VITE_API_BASE_URL` env var to backend URL

**ngrok URL changes on every restart (free tier):**
- Each ngrok restart = new random subdomain → must update BotFather Menu Button URL each time
- Workaround: ngrok paid plan with reserved domain, or cloudflared with persistent tunnel, or commit BotFather to keep URL stable until P9 deploy

**Telegram theme not applying:**
- Telegram only sets CSS vars when initialized via WebApp.ready() in real Telegram
- Outside Telegram (browser dev mode) → fallback colors apply

---

**END OF P3 PLAN**
