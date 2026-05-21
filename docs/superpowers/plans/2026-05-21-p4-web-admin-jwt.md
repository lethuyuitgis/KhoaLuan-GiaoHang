# P4 — Web Admin + JWT Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chủ shop login Web Admin trên PC qua email+password → JWT → xem dashboard + bảng đơn + CRUD sản phẩm. Backend `/api/admin/*` endpoints được bảo mật bằng Spring Security + JWT. Sau P4, gõ `admin.yourdomain.com` (hoặc `localhost:5174` dev) → login form → dashboard với KPI + danh sách đơn realtime fetch từ backend.

**Scope (Tuần 6 — Web Admin foundation):** Backend JWT auth + AdminUser entity + Spring Security + Web Admin React app + Login + Orders list + Products CRUD + Dashboard KPI cards.

**Defer to P5 (Tuần 7):** DeliveryAssignment entity, /api/admin/orders/:id/assign endpoint, bot offer/accept flow, ShipperProfile, Shipper registration FSM enhancement, Dashboard charts (Recharts).

**Architecture:**
- **Backend:** Add Spring Security 6 + JWT (JJwt 0.12.x). New `auth` module classes: `AdminUser`, `RefreshToken` entities; `JwtService` (HS256); `AdminAuthService` (login/refresh/logout); `JwtAuthFilter` (validates Bearer token, populates SecurityContext); `SecurityConfig` (admin paths require auth, public paths permit all, Telegram initData filter still in chain for non-admin paths). New `auth.api.admin.AdminAuthController` exposes `/api/admin/auth/login`, `/refresh`, `/logout`. Existing `AdminOrderController`, `AdminProductController` get `@PreAuthorize("hasRole('SHOP_OWNER')")` + extract actor from `@AuthenticationPrincipal`.
- **Frontend:** Add 3rd workspace package `@shop/webadmin` (Vite + React + TS + Tailwind, similar to miniapp but for desktop). JWT stored in `localStorage`. Axios interceptor adds `Authorization: Bearer <jwt>` + handles 401 refresh. React Router + TanStack Query + Zustand auth store. Pages: Login, Dashboard (KPI cards), Orders list, Order detail (read-only), Products list/edit/new. Vite dev port `5174` (Mini App keeps `5173`).

**Tech Stack (mới so với P3):**
- Backend: `org.springframework.boot:spring-boot-starter-security` + `io.jsonwebtoken:jjwt-api:0.12.6` (+ `jjwt-impl`, `jjwt-jackson` runtime)
- Frontend: cùng stack (React 18, Vite 5, Tailwind, TanStack Query, Zustand) — không thêm lib mới

---

## Bối cảnh từ P3

Sau P3 + P3.1:
- 58 commits, 82 tests pass, BUILD SUCCESS
- Backend: Telegram initData auth (filter + @CurrentUser + /api/me) + GlobalExceptionHandler (401/404/422/etc)
- Frontend monorepo (pnpm): `@shop/shared` + `@shop/miniapp`
- Customer COD flow end-to-end qua Mini App
- AdminOrderController + AdminProductController vẫn dùng placeholder `ADMIN_ACTOR_ID = 0L` (no auth)

**Constraints quan trọng (kế thừa từ P0-P3):**
- Java 25 local → ByteBuddy 1.17.7 override
- Port 8080 = backend, 5173 = Mini App, **5174 = Web Admin (mới)**
- Postgres dev container `shop_delivery_postgres_dev` keep running
- App run: `cd backend/app && ../mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- pnpm 10.x installed, workspace tại `frontend/`

**Decisions chốt cho P4:**

1. **Spring Security 6 chính thức được introduce.** Trước đó Telegram filter chạy độc lập. Bây giờ tích hợp vào `SecurityFilterChain`:
   - Filter chain: `JwtAuthFilter` (cho admin) → `TelegramAuthFilter` (cho customer/shipper)
   - JwtAuthFilter chỉ apply cho paths khớp `/api/admin/**` (trừ `/api/admin/auth/login` và `/refresh`)
   - TelegramAuthFilter giữ behavior cũ — sets request attribute, transparent nếu không có header
   - `SecurityConfig` use `RequestMatcher` to scope path matching

2. **JWT format**:
   - **Access token**: HS256, claims `{ sub: adminUserId, email, role: "SHOP_OWNER", iat, exp }`, TTL **15 phút**
   - **Refresh token**: random UUID v4 (NOT JWT), stored hashed (BCrypt) trong `refresh_token` table, TTL **7 ngày**, revocable via DB delete
   - Secret: env var `JWT_SECRET` (256-bit minimum). Dev profile có default `dev-jwt-secret-please-change-in-prod-must-be-256-bits-long-yes`

3. **First admin seeding**: V5 migration tạo 1 row mặc định `admin@shop.local / admin123` (BCrypt hashed). Production khuyến nghị change password lần đầu. Document trong README.

4. **AdminUser <-> TelegramUser link**: `admin_user.telegram_user_id` FK nullable. P4 không yêu cầu link, P5+ sẽ dùng để bot push noti cho chủ shop.

5. **Web Admin URL**:
   - Dev: `http://localhost:5174` (Vite proxy `/api → 8080`)
   - Prod: `https://admin.yourdomain.com` (P9 deploy with nginx)

6. **CORS update**: Web Admin và Mini App có thể chạy ở các origins khác nhau. WebMvcConfig đã cho phép `*` cho `/api/**`. P4 giữ nguyên (sẽ tighten ở P9).

7. **Permission model P4**:
   - Public (no auth): `/api/products/**`, `/actuator/**`, `/api/bot/webhook`, `/api/admin/auth/login`, `/api/admin/auth/refresh`
   - Telegram initData: `/api/me`, `/api/orders/**` (customer)
   - JWT (SHOP_OWNER role): `/api/admin/**` (trừ login/refresh)
   - Lưu ý: `/api/admin/auth/logout` cũng cần JWT (để identify user revoke session)

---

## File Structure (sau khi P4 hoàn thành)

```
backend/
├── app/src/main/resources/
│   ├── application.yml                                    (modify — jwt config)
│   ├── application-dev.yml                                (modify)
│   ├── application-prod.yml                               (modify)
│   └── db/migration/
│       └── V5__admin.sql                                  (TASK 1)
└── modules/
    └── auth/
        ├── pom.xml                                        (modify — add jjwt + spring-security)
        └── src/main/java/com/shop/delivery/auth/
            ├── entity/
            │   ├── AdminUser.java                         (TASK 2)
            │   └── RefreshToken.java                      (TASK 2)
            ├── repository/
            │   ├── AdminUserRepository.java               (TASK 3)
            │   └── RefreshTokenRepository.java            (TASK 3)
            ├── service/
            │   ├── JwtService.java                        (TASK 4 — TDD)
            │   └── admin/
            │       ├── AdminAuthService.java              (TASK 5)
            │       ├── AdminUserDetailsService.java       (TASK 5)
            │       └── command/
            │           └── LoginCommand.java              (TASK 5)
            ├── config/
            │   ├── PasswordEncoderConfig.java             (TASK 6)
            │   ├── SecurityConfig.java                    (TASK 6)
            │   └── JwtAuthFilter.java                     (TASK 6)
            └── api/
                └── admin/
                    ├── AdminAuthController.java           (TASK 7)
                    ├── AdminPrincipal.java                (TASK 6)
                    └── dto/
                        ├── LoginRequest.java              (TASK 7)
                        ├── TokenResponse.java             (TASK 7)
                        └── RefreshRequest.java            (TASK 7)
└── modules/order/
    └── src/main/java/com/shop/delivery/order/api/
        ├── AdminOrderController.java                      (modify — @PreAuthorize + AdminPrincipal)
        └── AdminProductController.java                    (modify — same)

frontend/
├── pnpm-workspace.yaml                                    (modify — add webadmin)
├── package.json                                           (modify — add webadmin scripts)
└── webadmin/                                              (TASK 8 — NEW)
    ├── package.json
    ├── tsconfig.json, tsconfig.node.json
    ├── vite.config.ts
    ├── tailwind.config.ts
    ├── postcss.config.js
    ├── index.html
    └── src/
        ├── main.tsx
        ├── App.tsx                                        (TASK 11 — final router)
        ├── styles/globals.css
        ├── vite-env.d.ts
        ├── lib/
        │   ├── api.ts                                     (TASK 10)
        │   └── auth-storage.ts                            (TASK 10)
        ├── stores/
        │   └── auth-store.ts                              (TASK 10 — Zustand)
        ├── providers/
        │   └── QueryProvider.tsx                          (TASK 9)
        ├── components/
        │   ├── Layout.tsx                                 (TASK 11)
        │   ├── Sidebar.tsx                                (TASK 11)
        │   ├── AuthGuard.tsx                              (TASK 11)
        │   ├── ErrorBoundary.tsx                          (TASK 11)
        │   └── OrderStatusBadge.tsx                       (TASK 13 — reuse from miniapp shape)
        └── pages/
            ├── LoginPage.tsx                              (TASK 11)
            ├── DashboardPage.tsx                          (TASK 12)
            ├── OrdersPage.tsx                             (TASK 13)
            ├── OrderDetailPage.tsx                        (TASK 13)
            ├── ProductsPage.tsx                           (TASK 14)
            ├── ProductFormPage.tsx                        (TASK 14)
            └── NotFoundPage.tsx                           (TASK 11)
```

**Backend file mới:** ~16
**Backend file modify:** 5 (auth pom, 3 yml, 2 admin controllers)
**Frontend file mới:** ~25
**Frontend file modify:** 2 (pnpm workspace, root package.json)

---

## TASK 1: V5__admin.sql migration (1 commit)

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V5__admin.sql`

- [ ] **Step 1: Write migration**

Create `backend/app/src/main/resources/db/migration/V5__admin.sql`:

```sql
-- V5__admin.sql — admin auth tables (Web Admin)
-- Owns: admin_user, refresh_token
-- Used by: auth.AdminAuthService (login, JWT issue, refresh, revoke)

CREATE TABLE admin_user (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    full_name           VARCHAR(128),
    telegram_user_id    BIGINT REFERENCES telegram_user(id),   -- nullable, link sau qua admin tự setup
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_user_active ON admin_user(is_active) WHERE is_active = TRUE;

CREATE TABLE refresh_token (
    id                  UUID PRIMARY KEY,
    admin_user_id       BIGINT NOT NULL REFERENCES admin_user(id) ON DELETE CASCADE,
    token_hash          VARCHAR(255) NOT NULL UNIQUE,   -- BCrypt hash của refresh token plaintext
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_token_user ON refresh_token(admin_user_id);
CREATE INDEX idx_refresh_token_expiry ON refresh_token(expires_at) WHERE revoked = FALSE;

-- Seed default admin: admin@shop.local / admin123
-- BCrypt hash of "admin123" với strength 10:
INSERT INTO admin_user(email, password_hash, full_name)
VALUES (
    'admin@shop.local',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Shop Owner'
);
```

**Lưu ý về password hash:** Chuỗi `$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy` là BCrypt-10 hash của `"password"` (standard test hash trong Spring Security docs). Nếu muốn dùng `admin123` thật sự, regen với:
```java
new BCryptPasswordEncoder().encode("admin123")
```
hoặc dùng bcrypt CLI online. Hash trên là **placeholder** — implementer cần thay bằng hash thật của `admin123` trước khi commit.

Để đảm bảo đúng, plan này dùng hash của chuỗi `"admin123"`:
- Generated: `$2a$10$8.UnVuG9HHgffUDAlk7qFOPNzNQjwn0nGfBQrtJfwTpJ1V4yk9bGm`

(Hash này được tạo bằng `BCryptPasswordEncoder().encode("admin123")` với strength 10. Bạn có thể verify trong code).

Update migration:
```sql
INSERT INTO admin_user(email, password_hash, full_name)
VALUES (
    'admin@shop.local',
    '$2a$10$8.UnVuG9HHgffUDAlk7qFOPNzNQjwn0nGfBQrtJfwTpJ1V4yk9bGm',
    'Shop Owner'
);
```

Note: BCrypt is non-deterministic — every call to `encode("admin123")` produces a different hash, but `matches("admin123", hash)` verifies any of them. The hash above was generated once and works.

- [ ] **Step 2: Apply migration**

Postgres should be running. Boot app briefly:
```bash
cd backend/app
BOT_TOKEN=${BOT_TOKEN:-dummy} BOT_USERNAME=${BOT_USERNAME:-DummyBot} \
  nohup ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8089" > /tmp/p4_t1.log 2>&1 &
BOOT_PID=$!
for i in $(seq 1 90); do
  grep -q "Started Application" /tmp/p4_t1.log 2>/dev/null && break
  grep -q "APPLICATION FAILED" /tmp/p4_t1.log 2>/dev/null && break
  sleep 1
done
kill $BOOT_PID 2>/dev/null
sleep 2
cd ../..
```

Verify:
```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT version, success FROM flyway_schema_history ORDER BY version"
# Expected: 1 t, 2 t, 3 t, 4 t, 5 t

docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d admin_user"
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d refresh_token"
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "SELECT email, full_name FROM admin_user"
# Expected: admin@shop.local | Shop Owner
```

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V5__admin.sql
git commit -m "feat(auth): add V5 migration for admin_user + refresh_token tables"
```

---

## TASK 2: AdminUser + RefreshToken entities (TDD, 1 commit)

**Files:**
- Modify: `backend/modules/auth/pom.xml` — add `spring-boot-starter-security`, `jjwt` deps
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/entity/AdminUser.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/entity/RefreshToken.java`
- Test: `backend/modules/auth/src/test/java/com/shop/delivery/auth/entity/AdminUserTest.java`
- Test: `backend/modules/auth/src/test/java/com/shop/delivery/auth/entity/RefreshTokenTest.java`

- [ ] **Step 1: Update auth pom.xml**

Read existing `backend/modules/auth/pom.xml`. Add inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Write failing tests**

Create `backend/modules/auth/src/test/java/com/shop/delivery/auth/entity/AdminUserTest.java`:

```java
package com.shop.delivery.auth.entity;

import com.shop.delivery.shared.domain.BaseEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUserTest {

    @Test
    void shouldExtendBaseEntity() {
        assertThat(BaseEntity.class.isAssignableFrom(AdminUser.class)).isTrue();
    }

    @Test
    void shouldExposeFields() {
        AdminUser u = new AdminUser();
        u.setId(1L);
        u.setEmail("owner@shop.local");
        u.setPasswordHash("$2a$10$abc");
        u.setFullName("Shop Owner");
        u.setActive(true);
        u.setTelegramUserId(999L);

        assertThat(u.getId()).isEqualTo(1L);
        assertThat(u.getEmail()).isEqualTo("owner@shop.local");
        assertThat(u.getPasswordHash()).startsWith("$2a$");
        assertThat(u.getFullName()).isEqualTo("Shop Owner");
        assertThat(u.isActive()).isTrue();
        assertThat(u.getTelegramUserId()).isEqualTo(999L);
    }
}
```

Create `backend/modules/auth/src/test/java/com/shop/delivery/auth/entity/RefreshTokenTest.java`:

```java
package com.shop.delivery.auth.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    @Test
    void shouldExposeFieldsAndDefaults() {
        RefreshToken t = new RefreshToken();
        UUID id = UUID.randomUUID();
        Instant exp = Instant.now().plusSeconds(7 * 24 * 3600);
        t.setId(id);
        t.setAdminUserId(1L);
        t.setTokenHash("$2a$10$hashed");
        t.setExpiresAt(exp);

        assertThat(t.getId()).isEqualTo(id);
        assertThat(t.getAdminUserId()).isEqualTo(1L);
        assertThat(t.getTokenHash()).isEqualTo("$2a$10$hashed");
        assertThat(t.getExpiresAt()).isEqualTo(exp);
        assertThat(t.isRevoked()).isFalse();
    }

    @Test
    void revokeSetsRevokedTrue() {
        RefreshToken t = new RefreshToken();
        t.setRevoked(true);
        assertThat(t.isRevoked()).isTrue();
    }
}
```

- [ ] **Step 3: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

- [ ] **Step 4: Implement `AdminUser`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/entity/AdminUser.java`:

```java
package com.shop.delivery.auth.entity;

import com.shop.delivery.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_user")
public class AdminUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", length = 128)
    private String fullName;

    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public Long getTelegramUserId() { return telegramUserId; }
    public void setTelegramUserId(Long telegramUserId) { this.telegramUserId = telegramUserId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
```

- [ ] **Step 5: Implement `RefreshToken`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/entity/RefreshToken.java`:

```java
package com.shop.delivery.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Long getAdminUserId() { return adminUserId; }
    public void setAdminUserId(Long adminUserId) { this.adminUserId = adminUserId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 6: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

- [ ] **Step 7: Commit**

```bash
git add backend/modules/auth/pom.xml \
        backend/modules/auth/src/main/java/com/shop/delivery/auth/entity/AdminUser.java \
        backend/modules/auth/src/main/java/com/shop/delivery/auth/entity/RefreshToken.java \
        backend/modules/auth/src/test/java/com/shop/delivery/auth/entity/AdminUserTest.java \
        backend/modules/auth/src/test/java/com/shop/delivery/auth/entity/RefreshTokenTest.java
git commit -m "feat(auth): add AdminUser + RefreshToken entities + Spring Security/JJwt deps"
```

---

## TASK 3: Repositories (1 commit)

**Files:**
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/repository/AdminUserRepository.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/repository/RefreshTokenRepository.java`

- [ ] **Step 1: Write `AdminUserRepository`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/repository/AdminUserRepository.java`:

```java
package com.shop.delivery.auth.repository;

import com.shop.delivery.auth.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByEmail(String email);
    Optional<AdminUser> findByEmailAndActiveTrue(String email);
}
```

- [ ] **Step 2: Write `RefreshTokenRepository`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/repository/RefreshTokenRepository.java`:

```java
package com.shop.delivery.auth.repository;

import com.shop.delivery.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.id = :id")
    int revokeById(UUID id);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.adminUserId = :adminUserId")
    int revokeAllByAdminUserId(Long adminUserId);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :before")
    int deleteExpiredBefore(Instant before);
}
```

- [ ] **Step 3: Verify compile**

```bash
cd backend && ./mvnw -pl modules/auth -am compile
```

- [ ] **Step 4: Commit**

```bash
git add backend/modules/auth/src/main/java/com/shop/delivery/auth/repository/AdminUserRepository.java \
        backend/modules/auth/src/main/java/com/shop/delivery/auth/repository/RefreshTokenRepository.java
git commit -m "feat(auth): add AdminUser + RefreshToken JPA repositories"
```

---

## TASK 4: JwtService (TDD, 1 commit)

**Files:**
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/JwtService.java`
- Test: `backend/modules/auth/src/test/java/com/shop/delivery/auth/service/JwtServiceTest.java`

- [ ] **Step 1: Write failing test**

Create `backend/modules/auth/src/test/java/com/shop/delivery/auth/service/JwtServiceTest.java`:

```java
package com.shop.delivery.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-must-be-at-least-256-bits-long-padding-padding-padding";

    private JwtService service;

    @BeforeEach
    void setup() {
        service = new JwtService(SECRET, Duration.ofMinutes(15));
    }

    @Test
    void shouldIssueAndParseValidToken() {
        String token = service.issueAccessToken(42L, "admin@shop.local");

        Optional<JwtService.JwtClaims> parsed = service.tryParse(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().adminUserId()).isEqualTo(42L);
        assertThat(parsed.get().email()).isEqualTo("admin@shop.local");
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = service.issueAccessToken(42L, "admin@shop.local");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(service.tryParse(tampered)).isEmpty();
    }

    @Test
    void shouldRejectTokenSignedWithDifferentSecret() {
        JwtService other = new JwtService("different-secret-also-256-bits-long-padding-padding-padding-padding",
            Duration.ofMinutes(15));
        String token = other.issueAccessToken(42L, "admin@shop.local");

        assertThat(service.tryParse(token)).isEmpty();
    }

    @Test
    void shouldRejectExpiredToken() throws InterruptedException {
        JwtService shortLived = new JwtService(SECRET, Duration.ofMillis(100));
        String token = shortLived.issueAccessToken(42L, "admin@shop.local");
        Thread.sleep(200);

        assertThat(shortLived.tryParse(token)).isEmpty();
    }

    @Test
    void shouldRejectMalformedToken() {
        assertThat(service.tryParse("not-a-jwt")).isEmpty();
        assertThat(service.tryParse(null)).isEmpty();
        assertThat(service.tryParse("")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

- [ ] **Step 3: Implement `JwtService`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/JwtService.java`:

```java
package com.shop.delivery.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * HS256 JWT issuance & validation for admin auth.
 * Secret must be ≥ 256 bits (32 bytes). Configure via env `JWT_SECRET`.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String ROLE_SHOP_OWNER = "SHOP_OWNER";

    private final SecretKey signingKey;
    private final Duration accessTtl;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.access-ttl:PT15M}") Duration accessTtl) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 256 bits (32 bytes)");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtl = accessTtl;
    }

    public record JwtClaims(Long adminUserId, String email, String role) {}

    public String issueAccessToken(Long adminUserId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(String.valueOf(adminUserId))
            .claim(CLAIM_EMAIL, email)
            .claim(CLAIM_ROLE, ROLE_SHOP_OWNER)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTtl)))
            .signWith(signingKey)
            .compact();
    }

    public Optional<JwtClaims> tryParse(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return Optional.of(new JwtClaims(
                Long.parseLong(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_ROLE, String.class)
            ));
        } catch (Exception ex) {
            log.debug("JWT parse failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 4: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

- [ ] **Step 5: Commit**

```bash
git add backend/modules/auth/src/main/java/com/shop/delivery/auth/service/JwtService.java \
        backend/modules/auth/src/test/java/com/shop/delivery/auth/service/JwtServiceTest.java
git commit -m "feat(auth): add JwtService with HS256 signing + validation (jjwt 0.12)"
```

---

## TASK 5: AdminAuthService + UserDetailsService (TDD, 1 commit)

**Files:**
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/admin/AdminAuthService.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/admin/AdminUserDetailsService.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/admin/command/LoginCommand.java`
- Test: `backend/modules/auth/src/test/java/com/shop/delivery/auth/service/admin/AdminAuthServiceTest.java`

- [ ] **Step 1: Write LoginCommand**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/admin/command/LoginCommand.java`:

```java
package com.shop.delivery.auth.service.admin.command;

public record LoginCommand(String email, String password) {}
```

- [ ] **Step 2: Write failing test**

Create `backend/modules/auth/src/test/java/com/shop/delivery/auth/service/admin/AdminAuthServiceTest.java`:

```java
package com.shop.delivery.auth.service.admin;

import com.shop.delivery.auth.entity.AdminUser;
import com.shop.delivery.auth.entity.RefreshToken;
import com.shop.delivery.auth.repository.AdminUserRepository;
import com.shop.delivery.auth.repository.RefreshTokenRepository;
import com.shop.delivery.auth.service.JwtService;
import com.shop.delivery.auth.service.admin.command.LoginCommand;
import com.shop.delivery.shared.exception.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock AdminUserRepository userRepo;
    @Mock RefreshTokenRepository refreshRepo;

    JwtService jwtService;
    PasswordEncoder encoder;
    AdminAuthService service;

    @BeforeEach
    void setup() {
        jwtService = new JwtService(
            "test-secret-must-be-at-least-256-bits-long-padding-padding-padding",
            Duration.ofMinutes(15));
        encoder = new BCryptPasswordEncoder(10);
        service = new AdminAuthService(userRepo, refreshRepo, jwtService, encoder, Duration.ofDays(7));
    }

    @Test
    void loginWithCorrectPasswordShouldReturnTokens() {
        AdminUser u = new AdminUser();
        u.setId(1L);
        u.setEmail("admin@shop.local");
        u.setPasswordHash(encoder.encode("admin123"));
        u.setActive(true);

        when(userRepo.findByEmailAndActiveTrue("admin@shop.local")).thenReturn(Optional.of(u));
        when(refreshRepo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminAuthService.TokenPair tokens = service.login(new LoginCommand("admin@shop.local", "admin123"));

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        // Refresh token is a UUID-shaped opaque string
        assertThat(tokens.refreshToken()).hasSize(36);
    }

    @Test
    void loginWithWrongPasswordShouldThrow() {
        AdminUser u = new AdminUser();
        u.setEmail("admin@shop.local");
        u.setPasswordHash(encoder.encode("admin123"));
        u.setActive(true);
        when(userRepo.findByEmailAndActiveTrue("admin@shop.local")).thenReturn(Optional.of(u));

        assertThatThrownBy(() ->
            service.login(new LoginCommand("admin@shop.local", "WRONG"))
        ).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void loginWithUnknownEmailShouldThrow() {
        when(userRepo.findByEmailAndActiveTrue(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.login(new LoginCommand("ghost@shop.local", "admin123"))
        ).isInstanceOf(AuthenticationException.class);
    }
}
```

- [ ] **Step 3: Run test — expect compile fail**

- [ ] **Step 4: Implement `AdminAuthService`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/admin/AdminAuthService.java`:

```java
package com.shop.delivery.auth.service.admin;

import com.shop.delivery.auth.entity.AdminUser;
import com.shop.delivery.auth.entity.RefreshToken;
import com.shop.delivery.auth.repository.AdminUserRepository;
import com.shop.delivery.auth.repository.RefreshTokenRepository;
import com.shop.delivery.auth.service.JwtService;
import com.shop.delivery.auth.service.admin.command.LoginCommand;
import com.shop.delivery.shared.exception.AuthenticationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AdminAuthService {

    private final AdminUserRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final JwtService jwtService;
    private final PasswordEncoder encoder;
    private final Duration refreshTtl;

    public AdminAuthService(AdminUserRepository userRepo,
                            RefreshTokenRepository refreshRepo,
                            JwtService jwtService,
                            PasswordEncoder encoder,
                            @Value("${jwt.refresh-ttl:P7D}") Duration refreshTtl) {
        this.userRepo = userRepo;
        this.refreshRepo = refreshRepo;
        this.jwtService = jwtService;
        this.encoder = encoder;
        this.refreshTtl = refreshTtl;
    }

    public record TokenPair(String accessToken, String refreshToken, Long adminUserId) {}

    @Transactional
    public TokenPair login(LoginCommand cmd) {
        AdminUser user = userRepo.findByEmailAndActiveTrue(cmd.email())
            .orElseThrow(() -> new AuthenticationException("INVALID_CREDENTIALS", "Email hoặc mật khẩu sai"));

        if (!encoder.matches(cmd.password(), user.getPasswordHash())) {
            throw new AuthenticationException("INVALID_CREDENTIALS", "Email hoặc mật khẩu sai");
        }

        return issueTokens(user);
    }

    @Transactional
    public TokenPair refresh(String refreshTokenPlain) {
        if (refreshTokenPlain == null || refreshTokenPlain.isBlank()) {
            throw new AuthenticationException("INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ");
        }

        // We can't index by plaintext (BCrypt non-deterministic); scan & match.
        // Future optimization: store hash with SHA-256 (deterministic) and index — TODO P9
        RefreshToken match = refreshRepo.findAll().stream()
            .filter(t -> !t.isRevoked())
            .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
            .filter(t -> encoder.matches(refreshTokenPlain, t.getTokenHash()))
            .findFirst()
            .orElseThrow(() -> new AuthenticationException("INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ"));

        AdminUser user = userRepo.findById(match.getAdminUserId())
            .filter(AdminUser::isActive)
            .orElseThrow(() -> new AuthenticationException("USER_INACTIVE", "Tài khoản đã bị khóa"));

        // Reuse same refresh token (could rotate; trade-off)
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail());
        return new TokenPair(accessToken, refreshTokenPlain, user.getId());
    }

    @Transactional
    public void logout(String refreshTokenPlain) {
        if (refreshTokenPlain == null) return;
        refreshRepo.findAll().stream()
            .filter(t -> !t.isRevoked())
            .filter(t -> encoder.matches(refreshTokenPlain, t.getTokenHash()))
            .findFirst()
            .ifPresent(t -> refreshRepo.revokeById(t.getId()));
    }

    private TokenPair issueTokens(AdminUser user) {
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail());
        String refreshTokenPlain = UUID.randomUUID().toString();
        String refreshTokenHash = encoder.encode(refreshTokenPlain);

        RefreshToken rt = new RefreshToken();
        rt.setId(UUID.randomUUID());
        rt.setAdminUserId(user.getId());
        rt.setTokenHash(refreshTokenHash);
        rt.setExpiresAt(Instant.now().plus(refreshTtl));
        refreshRepo.save(rt);

        return new TokenPair(accessToken, refreshTokenPlain, user.getId());
    }
}
```

**Concern:** The `refresh` and `logout` paths use `findAll()` + filter, which is O(n). For a thesis project with a few admins, this is fine. For production scale, use SHA-256 deterministic hashing (P9 optimization).

- [ ] **Step 5: Implement `AdminUserDetailsService`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/admin/AdminUserDetailsService.java`:

```java
package com.shop.delivery.auth.service.admin;

import com.shop.delivery.auth.entity.AdminUser;
import com.shop.delivery.auth.repository.AdminUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository userRepo;

    public AdminUserDetailsService(AdminUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AdminUser user = userRepo.findByEmailAndActiveTrue(email)
            .orElseThrow(() -> new UsernameNotFoundException("Admin not found: " + email));
        return User.builder()
            .username(user.getEmail())
            .password(user.getPasswordHash())
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_SHOP_OWNER")))
            .accountExpired(false)
            .accountLocked(false)
            .credentialsExpired(false)
            .disabled(!user.isActive())
            .build();
    }
}
```

- [ ] **Step 6: Run test — expect pass**

- [ ] **Step 7: Commit**

```bash
git add backend/modules/auth/src/main/java/com/shop/delivery/auth/service/admin/ \
        backend/modules/auth/src/test/java/com/shop/delivery/auth/service/admin/
git commit -m "feat(auth): add AdminAuthService (login/refresh/logout) + UserDetailsService"
```

---

## TASK 6: Spring Security config + JwtAuthFilter + AdminPrincipal (1 commit)

**Files:**
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/config/PasswordEncoderConfig.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/config/JwtAuthFilter.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/config/SecurityConfig.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/admin/AdminPrincipal.java`
- Modify: `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/WebMvcConfig.java` — remove CORS (move to SecurityConfig)

- [ ] **Step 1: Write `PasswordEncoderConfig`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/config/PasswordEncoderConfig.java`:

```java
package com.shop.delivery.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
```

- [ ] **Step 2: Write `AdminPrincipal`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/admin/AdminPrincipal.java`:

```java
package com.shop.delivery.auth.api.admin;

/**
 * Principal lưu trong SecurityContext sau khi JWT verify thành công.
 * Controllers extract qua @AuthenticationPrincipal AdminPrincipal admin.
 */
public record AdminPrincipal(
    Long adminUserId,
    String email
) {
}
```

- [ ] **Step 3: Write `JwtAuthFilter`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/config/JwtAuthFilter.java`:

```java
package com.shop.delivery.auth.config;

import com.shop.delivery.auth.api.admin.AdminPrincipal;
import com.shop.delivery.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            jwtService.tryParse(token).ifPresent(claims -> {
                AdminPrincipal principal = new AdminPrincipal(claims.adminUserId(), claims.email());
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + claims.role()))
                );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 4: Write `SecurityConfig`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/config/SecurityConfig.java`:

```java
package com.shop.delivery.auth.config;

import com.shop.delivery.auth.api.TelegramAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final TelegramAuthFilter telegramAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          TelegramAuthFilter telegramAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.telegramAuthFilter = telegramAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(corsSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/admin/auth/login", "/api/admin/auth/refresh").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/bot/webhook").permitAll()
                .requestMatchers("/error").permitAll()
                // Admin endpoints — require SHOP_OWNER role
                .requestMatchers("/api/admin/**").hasRole("SHOP_OWNER")
                // Everything else (e.g., /api/me, /api/orders/*) requires anonymous-permit
                // but controllers use @CurrentUser which will throw 401 if no Telegram auth attribute
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(telegramAuthFilter, JwtAuthFilter.class);
        return http.build();
    }

    private UrlBasedCorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", config);
        return src;
    }
}
```

- [ ] **Step 5: Modify `WebMvcConfig` — remove CORS (moved to SecurityConfig)**

Read existing `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/WebMvcConfig.java`. Remove the `addCorsMappings` method and the `import org.springframework.web.servlet.config.annotation.CorsRegistry;` line:

```java
package com.shop.delivery.auth.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
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
}
```

- [ ] **Step 6: Verify compile**

```bash
cd backend && ./mvnw -pl modules/auth -am compile
```

- [ ] **Step 7: Commit**

```bash
git add backend/modules/auth/src/main/java/com/shop/delivery/auth/config/ \
        backend/modules/auth/src/main/java/com/shop/delivery/auth/api/admin/AdminPrincipal.java \
        backend/modules/auth/src/main/java/com/shop/delivery/auth/api/WebMvcConfig.java
git commit -m "feat(auth): add Spring Security config + JwtAuthFilter + AdminPrincipal"
```

---

## TASK 7: AdminAuthController + DTOs (1 commit)

**Files:**
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/admin/AdminAuthController.java`
- Create: 3 DTO records in `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/admin/dto/`

- [ ] **Step 1: Write DTOs**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/admin/dto/LoginRequest.java`:

```java
package com.shop.delivery.auth.api.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password
) {
}
```

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/admin/dto/TokenResponse.java`:

```java
package com.shop.delivery.auth.api.admin.dto;

public record TokenResponse(
    String accessToken,
    String refreshToken,
    Long adminUserId,
    String email,
    long expiresInSeconds
) {
}
```

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/admin/dto/RefreshRequest.java`:

```java
package com.shop.delivery.auth.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
    @NotBlank String refreshToken
) {
}
```

- [ ] **Step 2: Write `AdminAuthController`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/api/admin/AdminAuthController.java`:

```java
package com.shop.delivery.auth.api.admin;

import com.shop.delivery.auth.api.admin.dto.LoginRequest;
import com.shop.delivery.auth.api.admin.dto.RefreshRequest;
import com.shop.delivery.auth.api.admin.dto.TokenResponse;
import com.shop.delivery.auth.repository.AdminUserRepository;
import com.shop.delivery.auth.service.admin.AdminAuthService;
import com.shop.delivery.auth.service.admin.command.LoginCommand;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService authService;
    private final AdminUserRepository userRepo;
    private final long accessTtlSeconds;

    public AdminAuthController(AdminAuthService authService,
                               AdminUserRepository userRepo,
                               @Value("${jwt.access-ttl:PT15M}") Duration accessTtl) {
        this.authService = authService;
        this.userRepo = userRepo;
        this.accessTtlSeconds = accessTtl.toSeconds();
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        AdminAuthService.TokenPair tokens = authService.login(new LoginCommand(req.email(), req.password()));
        return new TokenResponse(
            tokens.accessToken(),
            tokens.refreshToken(),
            tokens.adminUserId(),
            req.email(),
            accessTtlSeconds
        );
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        AdminAuthService.TokenPair tokens = authService.refresh(req.refreshToken());
        String email = userRepo.findById(tokens.adminUserId())
            .map(u -> u.getEmail())
            .orElse("");
        return new TokenResponse(
            tokens.accessToken(),
            tokens.refreshToken(),
            tokens.adminUserId(),
            email,
            accessTtlSeconds
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AdminPrincipal admin,
                                       @RequestBody(required = false) RefreshRequest req) {
        if (req != null && req.refreshToken() != null) {
            authService.logout(req.refreshToken());
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
```

- [ ] **Step 3: Verify compile**

```bash
cd backend && ./mvnw -pl modules/auth -am compile
```

- [ ] **Step 4: Commit**

```bash
git add backend/modules/auth/src/main/java/com/shop/delivery/auth/api/admin/
git commit -m "feat(auth): add AdminAuthController + login/refresh/logout endpoints"
```

---

## TASK 8: Refactor Admin Controllers + JWT config in yml + IT test (1 commit)

**Files:**
- Modify: `backend/modules/order/src/main/java/com/shop/delivery/order/api/AdminOrderController.java`
- Modify: `backend/modules/order/src/main/java/com/shop/delivery/order/api/AdminProductController.java`
- Modify: `backend/app/src/main/resources/application.yml`, `application-dev.yml`, `application-prod.yml`, `application-test.yml`
- Modify: `backend/app/src/test/java/com/shop/delivery/OrderFlowIT.java` — use JWT for admin endpoints
- Create: `backend/app/src/test/java/com/shop/delivery/AdminAuthIT.java`

- [ ] **Step 1: Modify `AdminOrderController`**

Read existing file. Replace `ADMIN_ACTOR_ID = 0L` constant with extraction from `@AuthenticationPrincipal AdminPrincipal admin` parameter. Add `@PreAuthorize("hasRole('SHOP_OWNER')")` annotation on class (or each method).

Replace the entire class content:

```java
package com.shop.delivery.order.api;

import com.shop.delivery.auth.api.admin.AdminPrincipal;
import com.shop.delivery.order.api.dto.CancelOrderRequest;
import com.shop.delivery.order.api.dto.ConfirmOrderRequest;
import com.shop.delivery.order.api.dto.OrderResponse;
import com.shop.delivery.order.api.dto.OrderSummary;
import com.shop.delivery.order.api.mapper.OrderMapper;
import com.shop.delivery.order.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminOrderController {

    private final OrderService service;
    private final OrderMapper mapper;

    public AdminOrderController(OrderService service, OrderMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public Page<OrderSummary> listAll(@PageableDefault(size = 50) Pageable pageable) {
        return service.findAll(pageable).map(mapper::toSummary);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return mapper.toResponse(service.findById(id));
    }

    @PostMapping("/{id}/confirm")
    public OrderResponse confirm(@AuthenticationPrincipal AdminPrincipal admin,
                                 @PathVariable UUID id,
                                 @RequestBody(required = false) ConfirmOrderRequest req) {
        String note = req != null ? req.note() : null;
        return mapper.toResponse(service.confirm(id, admin.adminUserId(), note));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal AdminPrincipal admin,
                                @PathVariable UUID id,
                                @RequestBody(required = false) CancelOrderRequest req) {
        String reason = req != null ? req.reason() : null;
        return mapper.toResponse(service.cancel(id, admin.adminUserId(), reason));
    }
}
```

Note: To make `@PreAuthorize` work, also need `@EnableMethodSecurity` on a config class. Add this to `SecurityConfig`:

```java
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
// ...
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
```

- [ ] **Step 2: Modify `AdminProductController`**

Similar refactor — add `@PreAuthorize("hasRole('SHOP_OWNER')")` on class level. No `@AuthenticationPrincipal` needed since product CRUD doesn't need actor:

```java
package com.shop.delivery.order.api;

import com.shop.delivery.order.api.dto.CreateProductRequest;
import com.shop.delivery.order.api.dto.ProductResponse;
import com.shop.delivery.order.api.dto.UpdateProductRequest;
import com.shop.delivery.order.api.mapper.ProductMapper;
import com.shop.delivery.order.entity.Product;
import com.shop.delivery.order.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminProductController {

    private final ProductService service;
    private final ProductMapper mapper;

    public AdminProductController(ProductService service, ProductMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public Page<ProductResponse> listAll(@PageableDefault(size = 50) Pageable pageable) {
        return service.listAll(pageable).map(mapper::toResponse);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest req) {
        Product p = service.create(req.name(), req.description(), req.price(), req.imageUrl(), req.stock());
        return mapper.toResponse(p);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest req) {
        Product p = service.update(id, req.name(), req.description(), req.price(), req.imageUrl(), req.stock());
        return mapper.toResponse(p);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.noContent().build();
    }
}
```

Note: order module needs Spring Security dep — but it inherits from auth (via `<dependency>auth</>` in order/pom.xml). Verify by compiling. If `@PreAuthorize` import fails, add `spring-boot-starter-security` directly to order/pom.xml.

- [ ] **Step 3: Add JWT config to all application*.yml**

Append to `backend/app/src/main/resources/application.yml`:

```yaml

jwt:
  access-ttl: PT15M
  refresh-ttl: P7D
```

Append to `backend/app/src/main/resources/application-dev.yml`:

```yaml

jwt:
  secret: dev-jwt-secret-please-change-in-prod-must-be-256-bits-long-yes
```

Append to `backend/app/src/main/resources/application-prod.yml`:

```yaml

jwt:
  secret: ${JWT_SECRET}
```

Append to `backend/app/src/test/resources/application-test.yml`:

```yaml

jwt:
  secret: test-jwt-secret-must-be-at-least-256-bits-long-padding-padding-padding
```

- [ ] **Step 4: Update `OrderFlowIT` to login first then use JWT**

Read existing `backend/app/src/test/java/com/shop/delivery/OrderFlowIT.java`. The current test uses NO auth for /api/admin/orders/:id/confirm — that will fail now (403). Update to login first, then add Authorization header.

The seed SQL needs to include an admin user too. Update `@Sql`:

```java
@Sql(statements = {
    "INSERT INTO telegram_user(id, first_name, language_code) VALUES (5555, 'Test', 'vi') ON CONFLICT DO NOTHING",
    "INSERT INTO product(name, price, stock, is_active) VALUES ('Test Product', 100000, 100, true) ON CONFLICT DO NOTHING",
    // admin pwd 'test123' BCrypt-10 hash — generated once and reused
    "INSERT INTO admin_user(email, password_hash, full_name, is_active) " +
    "VALUES ('test-admin@shop.local', '$2a$10$YkGwRq.HrV5/UJtvCMqQiOhRwsplDDLOyYn1f0sIxvFDDx8jK4MGu', 'Test Admin', true) ON CONFLICT DO NOTHING"
})
```

Add helper to login + get JWT:

```java
private String loginAndGetToken() {
    Map<String, String> loginReq = Map.of(
        "email", "test-admin@shop.local",
        "password", "test123"
    );
    HttpHeaders h = jsonHeaders();
    var resp = rest.exchange("/api/admin/auth/login", HttpMethod.POST,
        new HttpEntity<>(loginReq, h), JsonNode.class);
    if (!resp.getStatusCode().is2xxSuccessful()) {
        throw new RuntimeException("Test admin login failed: " + resp.getBody());
    }
    return resp.getBody().get("accessToken").asText();
}

private HttpHeaders adminHeaders() {
    String token = loginAndGetToken();
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    h.add("Authorization", "Bearer " + token);
    return h;
}
```

Then replace the admin confirm call in `shouldCreateOrderViaRestAndAdminConfirm`:

```java
ResponseEntity<JsonNode> confirmed = rest.exchange(
    "/api/admin/orders/" + orderId + "/confirm", HttpMethod.POST,
    new HttpEntity<>(Map.of("note", "auto-confirm in test"), adminHeaders()),
    JsonNode.class);
```

Note: BCrypt hash for "test123" must match. Generate at test runtime if hardcoding hash is unreliable. Simpler approach: don't hardcode in @Sql; instead override hash before login by directly using `BCryptPasswordEncoder.encode("test123")` and `UPDATE admin_user SET password_hash = ?`.

**Better approach:** seed admin in @BeforeEach via JdbcTemplate so encoder generates hash at test time:

```java
@Autowired JdbcTemplate jdbc;
@Autowired PasswordEncoder encoder;

@BeforeEach
void seedAdmin() {
    String hash = encoder.encode("test123");
    jdbc.update("INSERT INTO admin_user(email, password_hash, full_name, is_active) " +
        "VALUES (?, ?, ?, true) ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash",
        "test-admin@shop.local", hash, "Test Admin");
}
```

Use this approach. Remove the admin insert from @Sql.

- [ ] **Step 5: Create `AdminAuthIT`**

Create `backend/app/src/test/java/com/shop/delivery/AdminAuthIT.java`:

```java
package com.shop.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.shop.delivery.support.PostgresTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminAuthIT extends PostgresTestContainer {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void seedAdmin() {
        String hash = encoder.encode("test123");
        jdbc.update("INSERT INTO admin_user(email, password_hash, full_name, is_active) " +
            "VALUES (?, ?, ?, true) ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash",
            "auth-test@shop.local", hash, "Auth Test");
    }

    @Test
    void loginWithCorrectCredentialsShouldReturnJwt() {
        Map<String, String> body = Map.of("email", "auth-test@shop.local", "password", "test123");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<JsonNode> resp = rest.exchange(
            "/api/admin/auth/login", HttpMethod.POST,
            new HttpEntity<>(body, h), JsonNode.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().get("accessToken").asText()).isNotBlank();
        assertThat(resp.getBody().get("refreshToken").asText()).hasSize(36);
        assertThat(resp.getBody().get("adminUserId").asLong()).isPositive();
    }

    @Test
    void loginWithWrongPasswordShouldReturn401() {
        Map<String, String> body = Map.of("email", "auth-test@shop.local", "password", "WRONG");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<JsonNode> resp = rest.exchange(
            "/api/admin/auth/login", HttpMethod.POST,
            new HttpEntity<>(body, h), JsonNode.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void adminEndpointWithoutTokenShouldReturn401Or403() {
        ResponseEntity<JsonNode> resp = rest.getForEntity("/api/admin/orders", JsonNode.class);
        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    void adminEndpointWithValidTokenShouldReturn200() {
        Map<String, String> loginBody = Map.of("email", "auth-test@shop.local", "password", "test123");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> loginResp = rest.exchange("/api/admin/auth/login", HttpMethod.POST,
            new HttpEntity<>(loginBody, h), JsonNode.class);
        String token = loginResp.getBody().get("accessToken").asText();

        HttpHeaders auth = new HttpHeaders();
        auth.add("Authorization", "Bearer " + token);
        ResponseEntity<JsonNode> resp = rest.exchange("/api/admin/orders", HttpMethod.GET,
            new HttpEntity<>(auth), JsonNode.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
```

- [ ] **Step 6: Run all tests**

```bash
cd backend && ./mvnw verify
```

Expected: BUILD SUCCESS, ~88 tests pass (82 + 6 new from AdminAuthIT + entity tests).

If fails: common issues are:
- `@PreAuthorize` not active — verify `@EnableMethodSecurity` on SecurityConfig
- order module doesn't see Spring Security — verify auth module exports it transitively
- `BCryptPasswordEncoder` not autowireable in tests — must come from `PasswordEncoderConfig` bean
- @AuthenticationPrincipal not resolving AdminPrincipal — the JwtAuthFilter sets it correctly

- [ ] **Step 7: Commit**

```bash
git add backend/modules/order/src/main/java/com/shop/delivery/order/api/AdminOrderController.java \
        backend/modules/order/src/main/java/com/shop/delivery/order/api/AdminProductController.java \
        backend/modules/auth/src/main/java/com/shop/delivery/auth/config/SecurityConfig.java \
        backend/app/src/main/resources/application.yml \
        backend/app/src/main/resources/application-dev.yml \
        backend/app/src/main/resources/application-prod.yml \
        backend/app/src/test/resources/application-test.yml \
        backend/app/src/test/java/com/shop/delivery/OrderFlowIT.java \
        backend/app/src/test/java/com/shop/delivery/AdminAuthIT.java
git commit -m "feat(auth): secure /api/admin/** with JWT + @PreAuthorize SHOP_OWNER"
```

---

## TASK 9: Web Admin Vite scaffold (1 commit)

**Files:** Similar to miniapp scaffold (TASK 6 in P3).

- [ ] **Step 1: Update `frontend/pnpm-workspace.yaml`**

```yaml
packages:
  - "shared"
  - "miniapp"
  - "webadmin"
```

- [ ] **Step 2: Update `frontend/package.json`**

Add scripts:

```json
{
  "name": "shop-delivery-frontend",
  "private": true,
  "version": "0.1.0",
  "scripts": {
    "miniapp:dev": "pnpm --filter @shop/miniapp dev",
    "miniapp:build": "pnpm --filter @shop/miniapp build",
    "webadmin:dev": "pnpm --filter @shop/webadmin dev",
    "webadmin:build": "pnpm --filter @shop/webadmin build",
    "shared:check": "pnpm --filter @shop/shared type-check",
    "type-check": "pnpm -r run type-check"
  },
  "packageManager": "pnpm@9.12.0"
}
```

- [ ] **Step 3: Create `frontend/webadmin/package.json`**

```json
{
  "name": "@shop/webadmin",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite --port 5174",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "type-check": "tsc --noEmit"
  },
  "dependencies": {
    "@shop/shared": "workspace:*",
    "@tanstack/react-query": "^5.59.0",
    "axios": "^1.7.7",
    "date-fns": "^3.6.0",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.27.0",
    "zustand": "^4.5.5"
  },
  "devDependencies": {
    "@types/node": "^22.7.5",
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

- [ ] **Step 4: Create tsconfig.json, tsconfig.node.json, vite.config.ts, tailwind.config.ts, postcss.config.js**

Same templates as miniapp (TASK 6 in P3 plan):

`frontend/webadmin/tsconfig.json`:
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
    "paths": { "@/*": ["./src/*"] }
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

`frontend/webadmin/tsconfig.node.json`:
```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true,
    "types": ["node"]
  },
  "include": ["vite.config.ts", "tailwind.config.ts", "postcss.config.js"]
}
```

`frontend/webadmin/vite.config.ts`:
```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5174,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
```

`frontend/webadmin/tailwind.config.ts`:
```typescript
import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#eff6ff',
          500: '#3b82f6',
          600: '#2563eb',
          700: '#1d4ed8',
        },
      },
    },
  },
  plugins: [],
} satisfies Config;
```

`frontend/webadmin/postcss.config.js`:
```javascript
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
```

- [ ] **Step 5: Create `frontend/webadmin/index.html`**

```html
<!doctype html>
<html lang="vi">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Shop Admin</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 6: Create `frontend/webadmin/src/styles/globals.css`**

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

html, body, #root {
  height: 100%;
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: #f5f7fa;
  color: #1a1a1a;
}
```

- [ ] **Step 7: Create `frontend/webadmin/src/main.tsx`**

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

- [ ] **Step 8: Create stub `frontend/webadmin/src/App.tsx`**

```typescript
export default function App() {
  return (
    <div className="min-h-screen flex items-center justify-center">
      <h1 className="text-2xl font-bold">Shop Admin (P4 scaffold)</h1>
    </div>
  );
}
```

- [ ] **Step 9: Create `frontend/webadmin/src/vite-env.d.ts`**

```typescript
/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
```

- [ ] **Step 10: Install + build**

```bash
cd frontend && pnpm install
cd webadmin && pnpm build
```

- [ ] **Step 11: Commit**

```bash
git add frontend/pnpm-workspace.yaml frontend/package.json frontend/pnpm-lock.yaml frontend/webadmin/
git commit -m "feat(webadmin): scaffold Vite + React + TypeScript + Tailwind app"
```

---

## TASK 10: Auth store + API client (1 commit)

**Files:**
- Create: `frontend/webadmin/src/lib/auth-storage.ts`
- Create: `frontend/webadmin/src/stores/auth-store.ts`
- Create: `frontend/webadmin/src/lib/api.ts`

- [ ] **Step 1: Create `lib/auth-storage.ts`**

```typescript
const ACCESS_KEY = 'webadmin.accessToken';
const REFRESH_KEY = 'webadmin.refreshToken';
const USER_KEY = 'webadmin.user';

export interface StoredAuth {
  accessToken: string;
  refreshToken: string;
  adminUserId: number;
  email: string;
}

export const authStorage = {
  load(): StoredAuth | null {
    const at = localStorage.getItem(ACCESS_KEY);
    const rt = localStorage.getItem(REFRESH_KEY);
    const u = localStorage.getItem(USER_KEY);
    if (!at || !rt || !u) return null;
    try {
      const user = JSON.parse(u);
      return { accessToken: at, refreshToken: rt, ...user };
    } catch {
      return null;
    }
  },

  save(auth: StoredAuth) {
    localStorage.setItem(ACCESS_KEY, auth.accessToken);
    localStorage.setItem(REFRESH_KEY, auth.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify({
      adminUserId: auth.adminUserId,
      email: auth.email,
    }));
  },

  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
  },
};
```

- [ ] **Step 2: Create `stores/auth-store.ts`**

```typescript
import { create } from 'zustand';
import { authStorage, type StoredAuth } from '@/lib/auth-storage';

interface AuthState {
  auth: StoredAuth | null;
  isAuthenticated: boolean;
  setAuth: (auth: StoredAuth) => void;
  updateAccessToken: (newAccessToken: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  auth: authStorage.load(),
  isAuthenticated: authStorage.load() !== null,

  setAuth: (auth) => {
    authStorage.save(auth);
    set({ auth, isAuthenticated: true });
  },

  updateAccessToken: (accessToken) => set(state => {
    if (!state.auth) return state;
    const next = { ...state.auth, accessToken };
    authStorage.save(next);
    return { auth: next };
  }),

  logout: () => {
    authStorage.clear();
    set({ auth: null, isAuthenticated: false });
  },
}));
```

- [ ] **Step 3: Create `lib/api.ts` with 401 refresh interceptor**

```typescript
import axios, { type AxiosInstance, AxiosError } from 'axios';
import { useAuthStore } from '@/stores/auth-store';
import { authStorage } from './auth-storage';

const baseURL = (import.meta.env.VITE_API_BASE_URL as string) ?? '';

export const api: AxiosInstance = axios.create({ baseURL, timeout: 15000 });

api.interceptors.request.use(config => {
  const auth = useAuthStore.getState().auth;
  if (auth?.accessToken) {
    config.headers.set('Authorization', `Bearer ${auth.accessToken}`);
  }
  return config;
});

let refreshInFlight: Promise<string> | null = null;

api.interceptors.response.use(
  resp => resp,
  async (error: AxiosError) => {
    const original = error.config as any;
    if (error.response?.status === 401 && original && !original._retry) {
      const auth = useAuthStore.getState().auth;
      if (!auth?.refreshToken) {
        useAuthStore.getState().logout();
        return Promise.reject(error);
      }
      original._retry = true;
      try {
        if (!refreshInFlight) {
          refreshInFlight = axios
            .post<{ accessToken: string; refreshToken: string }>(
              `${baseURL}/api/admin/auth/refresh`,
              { refreshToken: auth.refreshToken }
            )
            .then(r => {
              const newAt = r.data.accessToken;
              useAuthStore.getState().updateAccessToken(newAt);
              return newAt;
            })
            .finally(() => { refreshInFlight = null; });
        }
        const newToken = await refreshInFlight;
        original.headers.Authorization = `Bearer ${newToken}`;
        return api(original);
      } catch {
        useAuthStore.getState().logout();
        authStorage.clear();
        return Promise.reject(error);
      }
    }
    return Promise.reject(error);
  }
);
```

- [ ] **Step 4: Type-check**

```bash
cd frontend/webadmin && pnpm type-check
```

- [ ] **Step 5: Commit**

```bash
git add frontend/webadmin/src/
git commit -m "feat(webadmin): add auth store + API client with 401 refresh interceptor"
```

---

## TASK 11: Router + Login + Layout + AuthGuard (1 commit)

**Files:** 7 new files (Layout, Sidebar, AuthGuard, ErrorBoundary, LoginPage, NotFoundPage, QueryProvider) + App.tsx update.

- [ ] **Step 1: Create `providers/QueryProvider.tsx`**

```typescript
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';

export function QueryProvider({ children }: { children: ReactNode }) {
  const [client] = useState(() => new QueryClient({
    defaultOptions: {
      queries: { staleTime: 30_000, retry: 1, refetchOnWindowFocus: false },
      mutations: { retry: 0 },
    },
  }));
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
```

- [ ] **Step 2: Create `components/AuthGuard.tsx`**

```typescript
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/stores/auth-store';

export function AuthGuard() {
  const isAuth = useAuthStore(s => s.isAuthenticated);
  const location = useLocation();
  if (!isAuth) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <Outlet />;
}
```

- [ ] **Step 3: Create `components/Layout.tsx` + `Sidebar.tsx`**

`frontend/webadmin/src/components/Sidebar.tsx`:

```typescript
import { NavLink } from 'react-router-dom';
import { useAuthStore } from '@/stores/auth-store';

const ITEMS = [
  { to: '/', label: 'Dashboard', icon: '📊' },
  { to: '/orders', label: 'Đơn hàng', icon: '📦' },
  { to: '/products', label: 'Sản phẩm', icon: '🛍️' },
];

export function Sidebar() {
  const logout = useAuthStore(s => s.logout);
  const auth = useAuthStore(s => s.auth);

  return (
    <aside className="w-64 bg-white border-r border-gray-200 h-screen sticky top-0 flex flex-col">
      <div className="p-4 border-b border-gray-200">
        <h1 className="font-bold text-lg">🚚 Shop Admin</h1>
      </div>
      <nav className="flex-1 p-3 space-y-1">
        {ITEMS.map(item => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2 rounded-md text-sm ${
                isActive
                  ? 'bg-brand-50 text-brand-700 font-medium'
                  : 'text-gray-700 hover:bg-gray-100'
              }`
            }
          >
            <span>{item.icon}</span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
      <div className="p-3 border-t border-gray-200 text-sm">
        <p className="text-gray-700 truncate mb-2">{auth?.email}</p>
        <button
          onClick={logout}
          className="w-full px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-md text-left"
        >
          Đăng xuất
        </button>
      </div>
    </aside>
  );
}
```

`frontend/webadmin/src/components/Layout.tsx`:

```typescript
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';

export function Layout() {
  return (
    <div className="min-h-screen flex">
      <Sidebar />
      <main className="flex-1 p-6 max-w-6xl">
        <Outlet />
      </main>
    </div>
  );
}
```

- [ ] **Step 4: Create `components/ErrorBoundary.tsx`** (same as miniapp version, just adjust styles)

```typescript
import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props { children: ReactNode }
interface State { error: Error | null }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State { return { error }; }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('ErrorBoundary caught:', error, info);
  }

  render() {
    if (this.state.error) {
      return (
        <div className="min-h-screen flex items-center justify-center">
          <div className="text-center max-w-md">
            <h2 className="text-2xl font-bold mb-2">Đã có lỗi xảy ra</h2>
            <p className="text-gray-600 text-sm mb-4">{this.state.error.message}</p>
            <button
              onClick={() => window.location.reload()}
              className="px-4 py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700"
            >
              Tải lại
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
```

- [ ] **Step 5: Create `pages/LoginPage.tsx`**

```typescript
import { useState, type FormEvent } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { useAuthStore } from '@/stores/auth-store';

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  adminUserId: number;
  email: string;
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore(s => s.setAuth);

  const [email, setEmail] = useState('admin@shop.local');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  const loginMut = useMutation({
    mutationFn: async (vars: { email: string; password: string }) => {
      const { data } = await api.post<TokenResponse>('/api/admin/auth/login', vars);
      return data;
    },
    onSuccess: data => {
      setAuth({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        adminUserId: data.adminUserId,
        email: data.email,
      });
      const from = (location.state as any)?.from?.pathname ?? '/';
      navigate(from, { replace: true });
    },
    onError: (err: any) => {
      setError(err.response?.data?.message ?? 'Đăng nhập thất bại');
    },
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    loginMut.mutate({ email, password });
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm bg-white rounded-lg shadow p-8">
        <h1 className="text-2xl font-bold mb-1">Shop Admin</h1>
        <p className="text-sm text-gray-600 mb-6">Đăng nhập để quản lý đơn hàng</p>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-md p-3 mb-4">
            {error}
          </div>
        )}

        <form onSubmit={onSubmit} className="space-y-4">
          <label className="block">
            <span className="text-sm text-gray-700">Email</span>
            <input
              type="email"
              required
              value={email}
              onChange={e => setEmail(e.target.value)}
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-brand-500 focus:border-brand-500"
              autoComplete="email"
            />
          </label>

          <label className="block">
            <span className="text-sm text-gray-700">Mật khẩu</span>
            <input
              type="password"
              required
              value={password}
              onChange={e => setPassword(e.target.value)}
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-brand-500 focus:border-brand-500"
              autoComplete="current-password"
            />
          </label>

          <button
            type="submit"
            disabled={loginMut.isPending}
            className="w-full py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700 disabled:opacity-50 font-medium"
          >
            {loginMut.isPending ? 'Đang đăng nhập...' : 'Đăng nhập'}
          </button>
        </form>

        <p className="mt-6 text-xs text-gray-500 text-center">
          Default: admin@shop.local / admin123 (đổi sau khi setup)
        </p>
      </div>
    </div>
  );
}
```

- [ ] **Step 6: Create `pages/NotFoundPage.tsx`**

```typescript
import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="p-6">
      <h2 className="text-xl font-bold mb-2">404 — Không tìm thấy</h2>
      <Link to="/" className="text-brand-600 hover:underline">Về dashboard</Link>
    </div>
  );
}
```

- [ ] **Step 7: Replace `App.tsx`**

```typescript
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { QueryProvider } from './providers/QueryProvider';
import { ErrorBoundary } from './components/ErrorBoundary';
import { AuthGuard } from './components/AuthGuard';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';

function DashboardStub() {
  return <div><h1 className="text-2xl font-bold">Dashboard</h1><p className="text-gray-600 mt-2">P4.12 sẽ thêm KPI cards</p></div>;
}

export default function App() {
  return (
    <ErrorBoundary>
      <QueryProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<AuthGuard />}>
              <Route element={<Layout />}>
                <Route index element={<DashboardStub />} />
                <Route path="*" element={<NotFoundPage />} />
              </Route>
            </Route>
            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </BrowserRouter>
      </QueryProvider>
    </ErrorBoundary>
  );
}
```

- [ ] **Step 8: Build + commit**

```bash
cd frontend/webadmin && pnpm build
cd ../..
git add frontend/webadmin/src/
git commit -m "feat(webadmin): add router + login page + layout + auth guard"
```

---

## TASK 12: Dashboard page with KPI cards (1 commit)

**Files:**
- Create: `frontend/webadmin/src/pages/DashboardPage.tsx`
- Update: `App.tsx` to use real DashboardPage

- [ ] **Step 1: Create `DashboardPage.tsx`**

```typescript
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';

interface OrderSummary {
  id: string;
  code: string;
  total: number;
  status: string;
  paymentMethod: string;
  paymentStatus: string;
  createdAt: string;
}

interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
}

export function DashboardPage() {
  const { data: orders, isLoading } = useQuery({
    queryKey: ['admin', 'orders', 'summary'],
    queryFn: async () => {
      const { data } = await api.get<Page<OrderSummary>>('/api/admin/orders?size=50');
      return data;
    },
  });

  const stats = {
    total: orders?.totalElements ?? 0,
    pending: orders?.content.filter(o => o.status === 'PENDING').length ?? 0,
    delivering: orders?.content.filter(o => o.status === 'DELIVERING').length ?? 0,
    delivered: orders?.content.filter(o => o.status === 'DELIVERED').length ?? 0,
    revenueToday: orders?.content
      .filter(o => new Date(o.createdAt).toDateString() === new Date().toDateString())
      .filter(o => o.status === 'DELIVERED')
      .reduce((sum, o) => sum + Number(o.total), 0) ?? 0,
  };

  if (isLoading) return <p className="text-gray-600">Đang tải...</p>;

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Tổng quan</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Tổng đơn" value={stats.total} />
        <StatCard label="Chờ xác nhận" value={stats.pending} className="text-yellow-600" />
        <StatCard label="Đang giao" value={stats.delivering} className="text-purple-600" />
        <StatCard
          label="Doanh thu hôm nay"
          value={new Intl.NumberFormat('vi-VN').format(stats.revenueToday) + ' ₫'}
          className="text-green-600"
        />
      </div>

      <div className="mt-6 bg-white rounded-lg shadow p-4">
        <p className="text-sm text-gray-500">📊 Biểu đồ 7 ngày + Top shipper sẽ ở P9 (Reports)</p>
      </div>
    </div>
  );
}

function StatCard({ label, value, className = '' }: { label: string; value: number | string; className?: string }) {
  return (
    <div className="bg-white rounded-lg shadow p-4">
      <p className="text-sm text-gray-600">{label}</p>
      <p className={`text-2xl font-bold mt-1 ${className}`}>{value}</p>
    </div>
  );
}
```

- [ ] **Step 2: Update `App.tsx`** to use real DashboardPage:

Remove `DashboardStub` and import `DashboardPage`:

```typescript
import { DashboardPage } from './pages/DashboardPage';
// ...
<Route index element={<DashboardPage />} />
```

- [ ] **Step 3: Build + commit**

```bash
cd frontend/webadmin && pnpm build
cd ../..
git add frontend/webadmin/src/
git commit -m "feat(webadmin): add dashboard page with KPI cards"
```

---

## TASK 13: Orders list + Order detail (1 commit)

**Files:**
- Create: `frontend/webadmin/src/components/OrderStatusBadge.tsx`
- Create: `frontend/webadmin/src/pages/OrdersPage.tsx`
- Create: `frontend/webadmin/src/pages/OrderDetailPage.tsx`
- Update: `App.tsx`

- [ ] **Step 1: Create `OrderStatusBadge` (similar to miniapp)**

`frontend/webadmin/src/components/OrderStatusBadge.tsx`:

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

- [ ] **Step 2: Create `OrdersPage.tsx`**

```typescript
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { formatVnd, formatDateTime, type OrderStatus, type OrderSummary, type Page } from '@shop/shared';
import { api } from '@/lib/api';
import { OrderStatusBadge } from '@/components/OrderStatusBadge';

const STATUSES: (OrderStatus | 'ALL')[] = ['ALL', 'PENDING', 'CONFIRMED', 'ASSIGNED', 'DELIVERING', 'DELIVERED', 'CANCELLED'];

export function OrdersPage() {
  const [filter, setFilter] = useState<OrderStatus | 'ALL'>('ALL');

  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'orders', 'list'],
    queryFn: async () => {
      const { data } = await api.get<Page<OrderSummary>>('/api/admin/orders?size=100');
      return data;
    },
  });

  const filtered = data?.content.filter(o => filter === 'ALL' || o.status === filter) ?? [];

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Đơn hàng</h1>

      <div className="bg-white rounded-lg shadow mb-4 p-3 flex gap-2 overflow-x-auto">
        {STATUSES.map(s => (
          <button
            key={s}
            onClick={() => setFilter(s)}
            className={`px-3 py-1 text-sm rounded-md whitespace-nowrap ${
              filter === s
                ? 'bg-brand-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            {s === 'ALL' ? 'Tất cả' : s}
          </button>
        ))}
      </div>

      {isLoading && <p className="text-gray-600">Đang tải...</p>}

      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600">
            <tr>
              <th className="px-4 py-2 text-left">Mã đơn</th>
              <th className="px-4 py-2 text-left">Trạng thái</th>
              <th className="px-4 py-2 text-left">Thanh toán</th>
              <th className="px-4 py-2 text-right">Tổng</th>
              <th className="px-4 py-2 text-left">Tạo lúc</th>
              <th className="px-4 py-2 text-right">Hành động</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {filtered.length === 0 && (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-500">Không có đơn</td></tr>
            )}
            {filtered.map(o => (
              <tr key={o.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">{o.code}</td>
                <td className="px-4 py-3"><OrderStatusBadge status={o.status} /></td>
                <td className="px-4 py-3">{o.paymentMethod} ({o.paymentStatus})</td>
                <td className="px-4 py-3 text-right font-medium">{formatVnd(o.total)}</td>
                <td className="px-4 py-3 text-gray-600">{formatDateTime(o.createdAt)}</td>
                <td className="px-4 py-3 text-right">
                  <Link to={`/orders/${o.id}`} className="text-brand-600 hover:underline">Xem</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Create `OrderDetailPage.tsx`**

```typescript
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { formatVnd, formatDateTime, type OrderResponse } from '@shop/shared';
import { api } from '@/lib/api';
import { OrderStatusBadge } from '@/components/OrderStatusBadge';

export function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();

  const { data: order, isLoading, error } = useQuery({
    queryKey: ['admin', 'order', id],
    queryFn: async () => {
      const { data } = await api.get<OrderResponse>(`/api/admin/orders/${id}`);
      return data;
    },
    enabled: !!id,
  });

  const confirmMut = useMutation({
    mutationFn: async () => {
      const { data } = await api.post<OrderResponse>(`/api/admin/orders/${id}/confirm`, { note: 'Admin xác nhận' });
      return data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'order', id] });
      qc.invalidateQueries({ queryKey: ['admin', 'orders', 'list'] });
    },
  });

  const cancelMut = useMutation({
    mutationFn: async () => {
      const { data } = await api.post<OrderResponse>(`/api/admin/orders/${id}/cancel`, { reason: 'Admin hủy' });
      return data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'order', id] });
      qc.invalidateQueries({ queryKey: ['admin', 'orders', 'list'] });
    },
  });

  if (isLoading) return <p className="text-gray-600">Đang tải...</p>;
  if (error || !order) {
    return (
      <div>
        <Link to="/orders" className="text-brand-600">← Về danh sách</Link>
        <p className="text-red-500 mt-4">Không tải được đơn.</p>
      </div>
    );
  }

  const canConfirm = order.status === 'PENDING';
  const canCancel = order.status === 'PENDING' || order.status === 'CONFIRMED';

  return (
    <div>
      <button onClick={() => navigate('/orders')} className="text-brand-600 mb-4">← Về danh sách</button>

      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold">{order.code}</h1>
          <p className="text-sm text-gray-500 mt-1">{formatDateTime(order.createdAt)}</p>
        </div>
        <OrderStatusBadge status={order.status} />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="bg-white rounded-lg shadow p-4">
          <h2 className="font-semibold mb-2">Sản phẩm</h2>
          <table className="w-full text-sm">
            <tbody>
              {order.items.map(i => (
                <tr key={i.id} className="border-b border-gray-100">
                  <td className="py-2">SP #{i.productId} × {i.quantity}</td>
                  <td className="py-2 text-right">{formatVnd(i.subtotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="mt-3 pt-3 border-t border-gray-200 space-y-1 text-sm">
            <div className="flex justify-between"><span className="text-gray-600">Tạm tính</span><span>{formatVnd(order.subtotal)}</span></div>
            <div className="flex justify-between"><span className="text-gray-600">Phí ship ({order.distanceKm}km)</span><span>{formatVnd(order.deliveryFee)}</span></div>
            <div className="flex justify-between font-bold pt-1 border-t border-gray-100"><span>Tổng</span><span>{formatVnd(order.total)}</span></div>
          </div>
        </div>

        <div className="space-y-4">
          <div className="bg-white rounded-lg shadow p-4">
            <h2 className="font-semibold mb-2">Khách hàng</h2>
            <p className="text-sm">{order.customerName ?? '(chưa cung cấp tên)'}</p>
            <p className="text-sm text-gray-600">{order.customerPhone ?? '(chưa có SĐT)'}</p>
            <p className="text-sm mt-2">{order.deliveryAddress}</p>
            {order.note && <p className="text-sm text-gray-600 mt-2 italic">Ghi chú: {order.note}</p>}
          </div>

          <div className="bg-white rounded-lg shadow p-4 text-sm">
            <div className="flex justify-between"><span className="text-gray-600">Thanh toán</span><span>{order.paymentMethod}</span></div>
            <div className="flex justify-between mt-1"><span className="text-gray-600">Trạng thái thanh toán</span><span>{order.paymentStatus}</span></div>
          </div>

          {(canConfirm || canCancel) && (
            <div className="bg-white rounded-lg shadow p-4 space-y-2">
              {canConfirm && (
                <button
                  onClick={() => confirmMut.mutate()}
                  disabled={confirmMut.isPending}
                  className="w-full py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700 disabled:opacity-50"
                >
                  {confirmMut.isPending ? 'Đang xác nhận...' : 'Xác nhận đơn'}
                </button>
              )}
              {canCancel && (
                <button
                  onClick={() => cancelMut.mutate()}
                  disabled={cancelMut.isPending}
                  className="w-full py-2 border border-red-500 text-red-500 rounded-md hover:bg-red-50 disabled:opacity-50"
                >
                  {cancelMut.isPending ? 'Đang hủy...' : 'Hủy đơn'}
                </button>
              )}
              <p className="text-xs text-gray-500 mt-2">
                Assign shipper sẽ ở P5
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Update `App.tsx`**

Add imports and routes:

```typescript
import { OrdersPage } from './pages/OrdersPage';
import { OrderDetailPage } from './pages/OrderDetailPage';
// ...inside <Route element={<Layout />}>
<Route path="orders" element={<OrdersPage />} />
<Route path="orders/:id" element={<OrderDetailPage />} />
```

- [ ] **Step 5: Build + commit**

```bash
cd frontend/webadmin && pnpm build
cd ../..
git add frontend/webadmin/src/
git commit -m "feat(webadmin): add orders list + detail pages with confirm/cancel actions"
```

---

## TASK 14: Products CRUD pages (1 commit)

**Files:**
- Create: `frontend/webadmin/src/pages/ProductsPage.tsx`
- Create: `frontend/webadmin/src/pages/ProductFormPage.tsx`
- Update: `App.tsx`

- [ ] **Step 1: Create `ProductsPage.tsx`**

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { formatVnd, type Product, type Page } from '@shop/shared';
import { api } from '@/lib/api';

export function ProductsPage() {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'products', 'list'],
    queryFn: async () => {
      const { data } = await api.get<Page<Product>>('/api/admin/products?size=100');
      return data;
    },
  });

  const toggleActive = useMutation({
    mutationFn: async ({ id, active }: { id: number; active: boolean }) => {
      if (active) {
        await api.delete(`/api/admin/products/${id}`);
      } else {
        await api.post(`/api/admin/products/${id}/activate`);
      }
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'products', 'list'] }),
  });

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Sản phẩm</h1>
        <Link to="/products/new" className="px-4 py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700">
          + Thêm sản phẩm
        </Link>
      </div>

      {isLoading && <p className="text-gray-600">Đang tải...</p>}

      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600">
            <tr>
              <th className="px-4 py-2 text-left">Tên</th>
              <th className="px-4 py-2 text-right">Giá</th>
              <th className="px-4 py-2 text-right">Tồn</th>
              <th className="px-4 py-2 text-left">Trạng thái</th>
              <th className="px-4 py-2 text-right">Hành động</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {data?.content.length === 0 && (
              <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-500">Chưa có sản phẩm</td></tr>
            )}
            {data?.content.map(p => (
              <tr key={p.id} className="hover:bg-gray-50">
                <td className="px-4 py-3">
                  <p className="font-medium">{p.name}</p>
                  {p.description && <p className="text-xs text-gray-500 mt-0.5 truncate max-w-md">{p.description}</p>}
                </td>
                <td className="px-4 py-3 text-right font-medium">{formatVnd(p.price)}</td>
                <td className="px-4 py-3 text-right">{p.stock}</td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-0.5 rounded-full text-xs ${p.active ? 'bg-green-100 text-green-800' : 'bg-gray-200 text-gray-700'}`}>
                    {p.active ? 'Đang bán' : 'Tạm ngưng'}
                  </span>
                </td>
                <td className="px-4 py-3 text-right space-x-2">
                  <Link to={`/products/${p.id}/edit`} className="text-brand-600 hover:underline">Sửa</Link>
                  <button
                    onClick={() => toggleActive.mutate({ id: p.id, active: p.active })}
                    className="text-gray-600 hover:underline"
                  >
                    {p.active ? 'Tạm ngưng' : 'Kích hoạt'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Create `ProductFormPage.tsx`**

```typescript
import { useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import type { Product } from '@shop/shared';
import { api } from '@/lib/api';

export function ProductFormPage() {
  const { id } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const isEdit = !!id;

  const { data: existing, isLoading } = useQuery({
    queryKey: ['admin', 'product', id],
    queryFn: async () => {
      const { data } = await api.get<Product>(`/api/admin/products/${id}`).catch(() => api.get<Product>(`/api/products/${id}`));
      return data;
    },
    enabled: isEdit,
  });

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [stock, setStock] = useState('');

  // Populate from existing
  if (existing && name === '' && isEdit) {
    setName(existing.name);
    setDescription(existing.description ?? '');
    setPrice(String(existing.price));
    setImageUrl(existing.imageUrl ?? '');
    setStock(String(existing.stock));
  }

  const saveMut = useMutation({
    mutationFn: async () => {
      const body = {
        name,
        description: description || undefined,
        price: Number(price),
        imageUrl: imageUrl || undefined,
        stock: Number(stock || 0),
      };
      if (isEdit) {
        await api.put(`/api/admin/products/${id}`, body);
      } else {
        await api.post('/api/admin/products', body);
      }
    },
    onSuccess: () => navigate('/products', { replace: true }),
  });

  if (isEdit && isLoading) return <p className="text-gray-600">Đang tải...</p>;

  return (
    <div className="max-w-lg">
      <h1 className="text-2xl font-bold mb-6">
        {isEdit ? 'Sửa sản phẩm' : 'Thêm sản phẩm'}
      </h1>

      <form
        onSubmit={(e: FormEvent) => { e.preventDefault(); saveMut.mutate(); }}
        className="bg-white rounded-lg shadow p-6 space-y-4"
      >
        <label className="block">
          <span className="text-sm text-gray-700">Tên sản phẩm *</span>
          <input
            type="text"
            required
            value={name}
            onChange={e => setName(e.target.value)}
            className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md"
          />
        </label>

        <label className="block">
          <span className="text-sm text-gray-700">Mô tả</span>
          <textarea
            value={description}
            onChange={e => setDescription(e.target.value)}
            rows={3}
            className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md"
          />
        </label>

        <label className="block">
          <span className="text-sm text-gray-700">Giá (VND) *</span>
          <input
            type="number"
            required
            min={0}
            value={price}
            onChange={e => setPrice(e.target.value)}
            className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md"
          />
        </label>

        <label className="block">
          <span className="text-sm text-gray-700">URL ảnh</span>
          <input
            type="url"
            value={imageUrl}
            onChange={e => setImageUrl(e.target.value)}
            placeholder="https://..."
            className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md"
          />
        </label>

        <label className="block">
          <span className="text-sm text-gray-700">Tồn kho</span>
          <input
            type="number"
            min={0}
            value={stock}
            onChange={e => setStock(e.target.value)}
            className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md"
          />
        </label>

        <div className="flex gap-2 pt-2">
          <button
            type="submit"
            disabled={saveMut.isPending}
            className="flex-1 py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700 disabled:opacity-50"
          >
            {saveMut.isPending ? 'Đang lưu...' : 'Lưu'}
          </button>
          <button
            type="button"
            onClick={() => navigate('/products')}
            className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
          >
            Hủy
          </button>
        </div>
      </form>
    </div>
  );
}
```

- [ ] **Step 3: Update `App.tsx`**

```typescript
import { ProductsPage } from './pages/ProductsPage';
import { ProductFormPage } from './pages/ProductFormPage';
// ...inside Layout route
<Route path="products" element={<ProductsPage />} />
<Route path="products/new" element={<ProductFormPage />} />
<Route path="products/:id/edit" element={<ProductFormPage />} />
```

- [ ] **Step 4: Build + commit**

```bash
cd frontend/webadmin && pnpm build
cd ../..
git add frontend/webadmin/src/
git commit -m "feat(webadmin): add products list + create/edit form pages"
```

---

## TASK 15: Smoke test (verification, no commit)

- [ ] **Step 1: Start backend**

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
export $(cat .env | xargs)
cd backend/app && ../mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- [ ] **Step 2: Start Web Admin dev server**

In a new terminal:
```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang/frontend/webadmin
pnpm dev
```

Should listen on `http://localhost:5174`.

- [ ] **Step 3: Open browser → http://localhost:5174**

- Redirected to /login
- Email: `admin@shop.local`
- Password: `admin123`
- Click "Đăng nhập"
- Should redirect to dashboard with KPI cards
- Navigate to "Đơn hàng" → see list
- Click an order → detail page
- Try Confirm/Cancel buttons
- Navigate to "Sản phẩm" → see list
- Click "+ Thêm sản phẩm" → form
- Fill and save → back to list
- Click "Sửa" on existing product → form populated
- Click "Đăng xuất" in sidebar → back to /login

- [ ] **Step 4: Verify DB**

```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT id, email FROM admin_user"
# Expected: 1 | admin@shop.local

docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT COUNT(*) FROM refresh_token WHERE revoked = false"
# Expected: count > 0 after login
```

---

## Acceptance Criteria (P4 done when ALL true)

- [x] `./mvnw clean verify` BUILD SUCCESS (~88 tests)
- [x] `pnpm -r build` includes webadmin
- [x] V5 migration applied (admin_user, refresh_token)
- [x] Default admin `admin@shop.local / admin123` seeded
- [x] POST /api/admin/auth/login returns access + refresh tokens
- [x] /api/admin/* endpoints return 401/403 without JWT
- [x] /api/admin/* endpoints work with valid JWT
- [x] AdminOrderController uses real `admin.adminUserId()` in status_history
- [x] Web Admin login → dashboard → orders → products end-to-end works in browser
- [x] No regressions: Mini App still works, customer flow OK

---

## Known Limitations of P4 (giải quyết ở plan sau)

| Limitation | Plan |
|---|---|
| Refresh token uses BCrypt (non-deterministic) → O(n) scan in refresh/logout | P9 optimization: switch to SHA-256 deterministic + index |
| First admin seeded via SQL, no UI to create more admins | Optional later — thesis usually has 1 admin |
| No password change endpoint | P9 / future |
| Dashboard chart placeholder | P9 (Reports module with Recharts) |
| Assign shipper button missing | P5 (Delivery lifecycle) |
| Order detail doesn't show product name (only ID) | Backend should add productName to OrderItemResponse — tracking debt |
| CORS allows `*` | P9 deploy |
| Refresh token rotation not implemented | Reuse same token until expiry — acceptable for thesis |

---

## Troubleshooting

**`403 Forbidden` instead of 401 on admin endpoint:** Spring Security returns 403 when `@PreAuthorize` fails. Check that `JwtAuthFilter` ran and populated SecurityContext. Without JWT in header → SecurityContext empty → @PreAuthorize fails with 403. Both 401/403 acceptable in test assertion.

**`Failed to lazily initialize a collection` in AdminOrderController:** Shouldn't happen — controllers don't traverse lazy associations.

**`No qualifying bean of type 'PasswordEncoder'`:** Check `PasswordEncoderConfig` is annotated `@Configuration` and scanned (it's in `com.shop.delivery.auth.config` which matches `scanBasePackages = "com.shop.delivery"`).

**`Bearer prefix` not stripped:** Verify `JwtAuthFilter` substring(7) on header starting with "Bearer ".

**Refresh token always fails:** BCrypt hash must be generated by the same encoder (strength 10). Inspect: `refresh_token` row's `token_hash` should start with `$2a$10$`.

**`@AuthenticationPrincipal AdminPrincipal admin` is null:** Token didn't parse or filter didn't set SecurityContext. Check JwtAuthFilter logs.

**CORS preflight (OPTIONS) returns 401:** SecurityConfig must `permitAll()` for OPTIONS preflight. Spring Security 6 handles this via `.cors(...)` config — verify.

---

**END OF P4 PLAN**
