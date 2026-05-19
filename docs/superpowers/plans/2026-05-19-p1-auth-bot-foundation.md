# P1 — Auth + Bot Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build minimum-viable Telegram Bot integration: bot trả lời `/start`, đăng ký Telegram user vào DB lần đầu, role resolver có cache, idempotent webhook nhờ `processed_update`. Sau P1, gửi `/start` cho bot → bot reply welcome + user xuất hiện trong DB.

**Architecture:** Hai module mới `auth` + `bot` được implement. `auth` sở hữu entity `TelegramUser`, `UserRole`, cung cấp `TelegramUserService.registerOrUpdate()` và `RoleResolver` (cache Caffeine 5 phút). `bot` extends `telegrambots-spring-boot-starter` 6.9.7.1; dev profile dùng long polling (không cần HTTPS), prod profile dùng webhook (Task P9 deploy sẽ tận dụng). `UpdateRouter` phân update theo type → `UpdateHandler` (interface). Lần này chỉ có `StartHandler`, `HelpHandler`, `UnknownCommandHandler` (common). Customer/Shipper/ShopOwner-specific handlers triển khai ở P2+.

**Tech Stack (mới so với P0):**
- `org.telegram:telegrambots-spring-boot-starter:6.9.7.1` — Telegram Bot library
- `com.github.ben-manes.caffeine:caffeine` (Spring Boot–managed) — cache cho `RoleResolver`
- Spring Cache abstraction (`@EnableCaching`)

---

## Bối cảnh từ P0

Sau P0:
- 11 commits, build pipeline ổn (10 tests pass)
- `shared` module có `BaseEntity`, `DomainException` hierarchy
- `app` module boot với Spring Web + JPA + Flyway + Actuator
- 8 module shells trống (auth, order, delivery, payment, notification, bot, miniapp, webadmin)
- Postgres dev container runs (`infra/docker-compose.dev.yml`)
- Flyway V1__init đã apply (chỉ có `app_meta` table)

**Lưu ý môi trường từ P0:**
- Port 8080 có thể bị app khác chiếm (`com.shopcuathuy.ECommerceBackendApplication`). Trước khi smoke test P1, `kill <pid>` hoặc đổi `server.port` ở `application-dev.yml`.
- Để chạy app dev: `cd backend/app && ../mvnw spring-boot:run` (không phải `./mvnw -pl app -am spring-boot:run` từ `backend/`).
- Cần **lấy bot token thật** từ [@BotFather](https://t.me/botfather) để test smoke. Set qua env `BOT_TOKEN=...` và `BOT_USERNAME=...` trước khi run.

---

## File Structure (sau khi P1 hoàn thành)

```
backend/
├── app/
│   └── src/main/resources/db/migration/
│       ├── V1__init.sql                                    (đã có từ P0)
│       ├── V2__auth.sql                                    (TASK 1)
│       └── V3__bot.sql                                     (TASK 7)
├── modules/
│   ├── auth/
│   │   ├── pom.xml                                         (đã có từ P0, có thể add Caffeine dep ở TASK 5)
│   │   └── src/
│   │       ├── main/java/com/shop/delivery/auth/
│   │       │   ├── domain/
│   │       │   │   └── Role.java                           (TASK 2 — enum)
│   │       │   ├── entity/
│   │       │   │   ├── TelegramUser.java                   (TASK 2)
│   │       │   │   └── UserRole.java                       (TASK 3)
│   │       │   ├── repository/
│   │       │   │   ├── TelegramUserRepository.java         (TASK 4)
│   │       │   │   └── UserRoleRepository.java             (TASK 4)
│   │       │   ├── service/
│   │       │   │   ├── TelegramUserService.java            (TASK 5)
│   │       │   │   └── RoleResolver.java                   (TASK 6)
│   │       │   └── config/
│   │       │       └── CacheConfig.java                    (TASK 6)
│   │       └── test/java/com/shop/delivery/auth/
│   │           ├── repository/
│   │           │   └── TelegramUserRepositoryIT.java       (TASK 4)
│   │           ├── service/
│   │           │   ├── TelegramUserServiceTest.java        (TASK 5)
│   │           │   └── RoleResolverTest.java               (TASK 6)
│   │           └── support/
│   │               └── AuthTestcontainerBase.java          (TASK 4)
│   └── bot/
│       ├── pom.xml                                         (TASK 7 — add telegrambots dep)
│       └── src/
│           ├── main/java/com/shop/delivery/bot/
│           │   ├── config/
│           │   │   ├── BotProperties.java                  (TASK 8)
│           │   │   └── BotRegistrationConfig.java          (TASK 12)
│           │   ├── idempotency/
│           │   │   ├── ProcessedUpdate.java                (TASK 9)
│           │   │   ├── ProcessedUpdateRepository.java      (TASK 9)
│           │   │   └── ProcessedUpdateService.java         (TASK 9)
│           │   ├── sender/
│           │   │   └── BotSender.java                      (TASK 10)
│           │   ├── handler/
│           │   │   ├── UpdateHandler.java                  (TASK 11 — interface)
│           │   │   └── common/
│           │   │       ├── StartHandler.java               (TASK 11)
│           │   │       ├── HelpHandler.java                (TASK 11)
│           │   │       └── UnknownCommandHandler.java      (TASK 11)
│           │   ├── router/
│           │   │   └── UpdateRouter.java                   (TASK 11)
│           │   ├── DeliveryBot.java                        (TASK 12)
│           │   └── controller/
│           │       └── BotWebhookController.java           (TASK 13)
│           └── test/java/com/shop/delivery/bot/
│               ├── handler/common/
│               │   └── StartHandlerTest.java               (TASK 11)
│               ├── router/
│               │   └── UpdateRouterTest.java               (TASK 11)
│               └── support/
│                   └── UpdateFixtures.java                 (TASK 11)
└── app/
    └── src/main/resources/
        ├── application.yml                                 (modify — add cache)
        ├── application-dev.yml                             (modify — bot dev settings)
        └── application-prod.yml                            (modify — bot prod settings)
```

**File mới:** ~25
**File modify:** 3 application*.yml

---

## TASK 1: Auth Migration `V2__auth.sql` (1 commit)

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V2__auth.sql`

- [ ] **Step 1: Write migration**

```sql
-- V2__auth.sql — auth module tables
-- Owns: telegram_user, user_role
-- Used by: bot (register user on /start), miniapp (role lookup), webadmin (role check)

CREATE TABLE telegram_user (
    id              BIGINT PRIMARY KEY,             -- = Telegram user ID
    username        VARCHAR(64),
    first_name      VARCHAR(128),
    last_name       VARCHAR(128),
    phone           VARCHAR(32),
    photo_url       TEXT,
    language_code   VARCHAR(8),
    is_blocked      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_telegram_user_username ON telegram_user(username) WHERE username IS NOT NULL;

CREATE TABLE user_role (
    id                  BIGSERIAL PRIMARY KEY,
    telegram_user_id    BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    role                VARCHAR(16) NOT NULL,           -- CUSTOMER | SHIPPER | SHOP_OWNER
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | PENDING | BLOCKED
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_role UNIQUE (telegram_user_id, role)
);

CREATE INDEX idx_user_role_lookup ON user_role(telegram_user_id, status);
```

- [ ] **Step 2: Verify Flyway picks it up (dev profile)**

Start Postgres if not running:
```bash
docker compose -f infra/docker-compose.dev.yml ps | grep healthy || docker compose -f infra/docker-compose.dev.yml up -d
```

Apply migration via Spring Boot (start app briefly):
```bash
cd backend/app
nohup ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev > /tmp/p1_t1.log 2>&1 &
BOOT_PID=$!
for i in $(seq 1 60); do
  grep -q "Started Application" /tmp/p1_t1.log 2>/dev/null && break
  sleep 1
done
kill $BOOT_PID 2>/dev/null
sleep 2
```

Verify tables created:
```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "\d telegram_user"
# Expected: shows id (bigint, PK), username, first_name, ... columns

docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "\d user_role"
# Expected: id (bigserial, PK), telegram_user_id (bigint FK), role, status

docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY version"
# Expected: 1 init t, 2 auth t
```

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V2__auth.sql
git commit -m "feat(auth): add V2 migration for telegram_user + user_role tables"
```

---

## TASK 2: `Role` Enum + `TelegramUser` Entity (TDD, 1 commit)

**Files:**
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/domain/Role.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/entity/TelegramUser.java`
- Test: `backend/modules/auth/src/test/java/com/shop/delivery/auth/entity/TelegramUserTest.java`

- [ ] **Step 1: Write failing test**

Create `backend/modules/auth/src/test/java/com/shop/delivery/auth/entity/TelegramUserTest.java`:

```java
package com.shop.delivery.auth.entity;

import com.shop.delivery.shared.domain.BaseEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramUserTest {

    @Test
    void shouldUseTelegramIdAsPrimaryKey() {
        TelegramUser u = new TelegramUser();
        u.setId(987654321L);
        assertThat(u.getId()).isEqualTo(987654321L);
    }

    @Test
    void shouldExposeStandardFields() {
        TelegramUser u = new TelegramUser();
        u.setUsername("nguyen_a");
        u.setFirstName("Nguyễn");
        u.setLastName("A");
        u.setPhone("+84901234567");
        u.setPhotoUrl("https://t.me/photo.jpg");
        u.setLanguageCode("vi");

        assertThat(u.getUsername()).isEqualTo("nguyen_a");
        assertThat(u.getFirstName()).isEqualTo("Nguyễn");
        assertThat(u.getLastName()).isEqualTo("A");
        assertThat(u.getPhone()).isEqualTo("+84901234567");
        assertThat(u.getPhotoUrl()).isEqualTo("https://t.me/photo.jpg");
        assertThat(u.getLanguageCode()).isEqualTo("vi");
        assertThat(u.isBlocked()).isFalse();
    }

    @Test
    void shouldExtendBaseEntity() {
        assertThat(BaseEntity.class.isAssignableFrom(TelegramUser.class)).isTrue();
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement `Role` enum**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/domain/Role.java`:

```java
package com.shop.delivery.auth.domain;

public enum Role {
    CUSTOMER,
    SHIPPER,
    SHOP_OWNER
}
```

- [ ] **Step 4: Implement `TelegramUser` entity**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/entity/TelegramUser.java`:

```java
package com.shop.delivery.auth.entity;

import com.shop.delivery.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "telegram_user")
public class TelegramUser extends BaseEntity {

    /** Telegram user ID — KHÔNG sinh tự động, lấy từ Telegram. */
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "first_name", length = 128)
    private String firstName;

    @Column(name = "last_name", length = 128)
    private String lastName;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;

    @Column(name = "language_code", length = 8)
    private String languageCode;

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getLanguageCode() { return languageCode; }
    public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }
}
```

- [ ] **Step 5: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

Expected: 3 tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/modules/auth/src/main/java/com/shop/delivery/auth/domain/Role.java \
        backend/modules/auth/src/main/java/com/shop/delivery/auth/entity/TelegramUser.java \
        backend/modules/auth/src/test/java/com/shop/delivery/auth/entity/TelegramUserTest.java
git commit -m "feat(auth): add Role enum and TelegramUser entity"
```

---

## TASK 3: `UserRole` Entity (TDD, 1 commit)

**Files:**
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/entity/UserRole.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/domain/UserRoleStatus.java`
- Test: `backend/modules/auth/src/test/java/com/shop/delivery/auth/entity/UserRoleTest.java`

- [ ] **Step 1: Write failing test**

Create `backend/modules/auth/src/test/java/com/shop/delivery/auth/entity/UserRoleTest.java`:

```java
package com.shop.delivery.auth.entity;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserRoleTest {

    @Test
    void shouldExposeFieldsAndDefaults() {
        UserRole ur = new UserRole();
        ur.setTelegramUserId(123L);
        ur.setRole(Role.CUSTOMER);
        ur.setStatus(UserRoleStatus.ACTIVE);
        Instant now = Instant.now();
        ur.setAssignedAt(now);

        assertThat(ur.getTelegramUserId()).isEqualTo(123L);
        assertThat(ur.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(ur.getStatus()).isEqualTo(UserRoleStatus.ACTIVE);
        assertThat(ur.getAssignedAt()).isEqualTo(now);
    }

    @Test
    void newInstanceShouldHaveActiveStatusByDefault() {
        UserRole ur = new UserRole();
        assertThat(ur.getStatus()).isEqualTo(UserRoleStatus.ACTIVE);
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement `UserRoleStatus` enum**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/domain/UserRoleStatus.java`:

```java
package com.shop.delivery.auth.domain;

public enum UserRoleStatus {
    ACTIVE,
    PENDING,
    BLOCKED
}
```

- [ ] **Step 4: Implement `UserRole` entity**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/entity/UserRole.java`:

```java
package com.shop.delivery.auth.entity;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "user_role",
       uniqueConstraints = @UniqueConstraint(name = "uq_user_role", columnNames = {"telegram_user_id", "role"}))
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserRoleStatus status = UserRoleStatus.ACTIVE;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTelegramUserId() { return telegramUserId; }
    public void setTelegramUserId(Long telegramUserId) { this.telegramUserId = telegramUserId; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public UserRoleStatus getStatus() { return status; }
    public void setStatus(UserRoleStatus status) { this.status = status; }

    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
}
```

- [ ] **Step 5: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

Expected: 5 tests pass (3 from Task 2 + 2 from Task 3).

- [ ] **Step 6: Commit**

```bash
git add backend/modules/auth/src/main/java/com/shop/delivery/auth/entity/UserRole.java \
        backend/modules/auth/src/main/java/com/shop/delivery/auth/domain/UserRoleStatus.java \
        backend/modules/auth/src/test/java/com/shop/delivery/auth/entity/UserRoleTest.java
git commit -m "feat(auth): add UserRole entity and UserRoleStatus enum"
```

---

## TASK 4: Repositories + Integration Test Base (TDD, 1 commit)

**Files:**
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/repository/TelegramUserRepository.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/repository/UserRoleRepository.java`
- Create: `backend/modules/auth/src/test/java/com/shop/delivery/auth/support/AuthTestcontainerBase.java`
- Create: `backend/modules/auth/src/test/java/com/shop/delivery/auth/repository/TelegramUserRepositoryIT.java`

Note: Integration tests need Postgres → use Testcontainers (auth module already has `spring-boot-starter-test` in scope test but not testcontainers; we need to add it).

- [ ] **Step 1: Add Testcontainers test deps to auth pom.xml**

Modify `backend/modules/auth/pom.xml` — add inside `<dependencies>` block:

```xml
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
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

Also add failsafe binding for IT tests (since the auth module pom doesn't bind failsafe like app does):

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-failsafe-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

The whole `<build>` block should be added at the bottom, before `</project>`.

- [ ] **Step 2: Write `AuthTestcontainerBase`**

Create `backend/modules/auth/src/test/java/com/shop/delivery/auth/support/AuthTestcontainerBase.java`:

```java
package com.shop.delivery.auth.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test base class for auth module integration tests — provides a Postgres container.
 * Migrations from app/db/migration are loaded via classpath when @SpringBootTest scans them.
 *
 * Tests in this module run via @DataJpaTest with Flyway disabled because the auth
 * module doesn't bring the full Spring Boot Application context. We rely on Hibernate
 * auto-DDL for tests in this module instead.
 */
@Testcontainers
public abstract class AuthTestcontainerBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auth_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Module-level tests use create-drop to avoid Flyway migration tight-coupling
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }
}
```

- [ ] **Step 3: Write failing repository integration test**

Create `backend/modules/auth/src/test/java/com/shop/delivery/auth/repository/TelegramUserRepositoryIT.java`:

```java
package com.shop.delivery.auth.repository;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.support.AuthTestcontainerBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@EntityScan("com.shop.delivery.auth.entity")
@EnableJpaRepositories("com.shop.delivery.auth.repository")
class TelegramUserRepositoryIT extends AuthTestcontainerBase {

    @Autowired
    TelegramUserRepository userRepo;

    @Autowired
    UserRoleRepository roleRepo;

    @Test
    void shouldPersistAndRetrieveTelegramUser() {
        TelegramUser u = new TelegramUser();
        u.setId(123456L);
        u.setUsername("test_user");
        u.setFirstName("Test");
        u.setLanguageCode("vi");
        userRepo.save(u);

        Optional<TelegramUser> found = userRepo.findById(123456L);
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("test_user");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindRolesByUserId() {
        TelegramUser u = new TelegramUser();
        u.setId(999L);
        u.setFirstName("Multi");
        userRepo.save(u);

        UserRole r1 = new UserRole();
        r1.setTelegramUserId(999L);
        r1.setRole(Role.CUSTOMER);
        r1.setStatus(UserRoleStatus.ACTIVE);
        roleRepo.save(r1);

        UserRole r2 = new UserRole();
        r2.setTelegramUserId(999L);
        r2.setRole(Role.SHIPPER);
        r2.setStatus(UserRoleStatus.PENDING);
        roleRepo.save(r2);

        var roles = roleRepo.findAllByTelegramUserIdAndStatus(999L, UserRoleStatus.ACTIVE);
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).getRole()).isEqualTo(Role.CUSTOMER);
    }
}
```

- [ ] **Step 4: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/auth -am test-compile
```

Expected: COMPILATION ERROR (repositories don't exist).

- [ ] **Step 5: Implement repositories**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/repository/TelegramUserRepository.java`:

```java
package com.shop.delivery.auth.repository;

import com.shop.delivery.auth.entity.TelegramUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelegramUserRepository extends JpaRepository<TelegramUser, Long> {
    Optional<TelegramUser> findByUsername(String username);
}
```

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/repository/UserRoleRepository.java`:

```java
package com.shop.delivery.auth.repository;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findAllByTelegramUserId(Long telegramUserId);

    List<UserRole> findAllByTelegramUserIdAndStatus(Long telegramUserId, UserRoleStatus status);

    Optional<UserRole> findByTelegramUserIdAndRole(Long telegramUserId, Role role);

    boolean existsByTelegramUserIdAndRoleAndStatus(Long telegramUserId, Role role, UserRoleStatus status);
}
```

- [ ] **Step 6: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/auth -am verify
```

Expected: BUILD SUCCESS, IT tests pass (Testcontainers spins up Postgres). First run downloads Postgres image.

- [ ] **Step 7: Commit**

```bash
git add backend/modules/auth/
git commit -m "feat(auth): add JPA repositories with integration test using Testcontainers"
```

---

## TASK 5: `TelegramUserService.registerOrUpdate` (TDD, 1 commit)

**Purpose:** When `/start` received, register new Telegram user or update existing one with latest info from Telegram (name might change). Idempotent.

**Files:**
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/TelegramUserService.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/TelegramUserUpsertCommand.java`
- Test: `backend/modules/auth/src/test/java/com/shop/delivery/auth/service/TelegramUserServiceTest.java`

- [ ] **Step 1: Write failing test**

Create `backend/modules/auth/src/test/java/com/shop/delivery/auth/service/TelegramUserServiceTest.java`:

```java
package com.shop.delivery.auth.service;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.auth.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramUserServiceTest {

    @Mock TelegramUserRepository userRepo;
    @Mock UserRoleRepository roleRepo;

    @InjectMocks TelegramUserService service;

    TelegramUserUpsertCommand command;

    @BeforeEach
    void setup() {
        command = new TelegramUserUpsertCommand(
            555L,
            "alice",
            "Alice",
            "Nguyen",
            "vi"
        );
    }

    @Test
    void registerNewUserShouldPersistUserAndAssignCustomerRole() {
        when(userRepo.findById(555L)).thenReturn(Optional.empty());
        when(userRepo.save(any(TelegramUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepo.findAllByTelegramUserId(555L)).thenReturn(List.of());

        TelegramUser result = service.registerOrUpdate(command);

        assertThat(result.getId()).isEqualTo(555L);
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getFirstName()).isEqualTo("Alice");

        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(roleRepo).save(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(roleCaptor.getValue().getStatus()).isEqualTo(UserRoleStatus.ACTIVE);
        assertThat(roleCaptor.getValue().getTelegramUserId()).isEqualTo(555L);
    }

    @Test
    void existingUserShouldHaveFieldsUpdated() {
        TelegramUser existing = new TelegramUser();
        existing.setId(555L);
        existing.setFirstName("Old Name");
        existing.setUsername("old_username");

        when(userRepo.findById(555L)).thenReturn(Optional.of(existing));
        when(userRepo.save(any(TelegramUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepo.findAllByTelegramUserId(555L)).thenReturn(
            List.of(makeRole(555L, Role.CUSTOMER, UserRoleStatus.ACTIVE)));

        service.registerOrUpdate(command);

        ArgumentCaptor<TelegramUser> userCaptor = ArgumentCaptor.forClass(TelegramUser.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getFirstName()).isEqualTo("Alice");
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("alice");
    }

    @Test
    void existingUserWithRoleShouldNotGetDuplicateRole() {
        TelegramUser existing = new TelegramUser();
        existing.setId(555L);

        when(userRepo.findById(555L)).thenReturn(Optional.of(existing));
        when(userRepo.save(any(TelegramUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepo.findAllByTelegramUserId(555L)).thenReturn(
            List.of(makeRole(555L, Role.CUSTOMER, UserRoleStatus.ACTIVE)));

        service.registerOrUpdate(command);

        verify(roleRepo, org.mockito.Mockito.never()).save(any(UserRole.class));
    }

    private UserRole makeRole(Long userId, Role role, UserRoleStatus status) {
        UserRole r = new UserRole();
        r.setTelegramUserId(userId);
        r.setRole(role);
        r.setStatus(status);
        return r;
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement `TelegramUserUpsertCommand`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/TelegramUserUpsertCommand.java`:

```java
package com.shop.delivery.auth.service;

/**
 * Dữ liệu để upsert TelegramUser. Field nullable theo Telegram Bot API:
 * - id luôn có
 * - username, firstName, lastName, languageCode có thể null
 */
public record TelegramUserUpsertCommand(
    Long id,
    String username,
    String firstName,
    String lastName,
    String languageCode
) {
}
```

- [ ] **Step 4: Implement `TelegramUserService`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/TelegramUserService.java`:

```java
package com.shop.delivery.auth.service;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.auth.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TelegramUserService {

    private final TelegramUserRepository userRepo;
    private final UserRoleRepository roleRepo;

    public TelegramUserService(TelegramUserRepository userRepo, UserRoleRepository roleRepo) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
    }

    /**
     * Đăng ký user mới hoặc cập nhật thông tin user hiện tại từ Telegram.
     * User mới được cấp role CUSTOMER với status ACTIVE.
     * Idempotent: gọi nhiều lần với cùng command không gây side effect ngoài update timestamp.
     */
    @Transactional
    public TelegramUser registerOrUpdate(TelegramUserUpsertCommand cmd) {
        TelegramUser user = userRepo.findById(cmd.id())
            .orElseGet(() -> {
                TelegramUser fresh = new TelegramUser();
                fresh.setId(cmd.id());
                return fresh;
            });

        // Cập nhật field từ Telegram (có thể đã đổi tên, đổi username)
        user.setUsername(cmd.username());
        user.setFirstName(cmd.firstName());
        user.setLastName(cmd.lastName());
        user.setLanguageCode(cmd.languageCode());

        TelegramUser saved = userRepo.save(user);

        // Gán role CUSTOMER nếu chưa có
        boolean hasCustomer = roleRepo.findAllByTelegramUserId(cmd.id()).stream()
            .anyMatch(r -> r.getRole() == Role.CUSTOMER);
        if (!hasCustomer) {
            UserRole role = new UserRole();
            role.setTelegramUserId(cmd.id());
            role.setRole(Role.CUSTOMER);
            role.setStatus(UserRoleStatus.ACTIVE);
            role.setAssignedAt(Instant.now());
            roleRepo.save(role);
        }

        return saved;
    }
}
```

- [ ] **Step 5: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

Expected: All 8 tests pass (3 + 2 + 3 from this task).

- [ ] **Step 6: Commit**

```bash
git add backend/modules/auth/src/main/java/com/shop/delivery/auth/service/ \
        backend/modules/auth/src/test/java/com/shop/delivery/auth/service/
git commit -m "feat(auth): add TelegramUserService.registerOrUpdate with auto CUSTOMER role"
```

---

## TASK 6: `RoleResolver` with Caffeine Cache (TDD, 1 commit)

**Purpose:** Look up active roles for a Telegram user, cached 5 minutes. Used by bot UpdateRouter to determine handler routing.

**Files:**
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/RoleResolver.java`
- Create: `backend/modules/auth/src/main/java/com/shop/delivery/auth/config/CacheConfig.java`
- Modify: `backend/modules/auth/pom.xml` — add Caffeine dep
- Modify: `backend/app/src/main/resources/application.yml` — enable cache + caffeine spec
- Test: `backend/modules/auth/src/test/java/com/shop/delivery/auth/service/RoleResolverTest.java`

- [ ] **Step 1: Add Caffeine dep to auth pom.xml**

Edit `backend/modules/auth/pom.xml`, add inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

- [ ] **Step 2: Write failing test**

Create `backend/modules/auth/src/test/java/com/shop/delivery/auth/service/RoleResolverTest.java`:

```java
package com.shop.delivery.auth.service;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleResolverTest {

    @Mock UserRoleRepository roleRepo;

    RoleResolver resolver;

    @BeforeEach
    void setup() {
        // Use uncached resolver (no Spring cache infrastructure in unit test).
        // Cache hit/miss behavior is covered separately by Spring integration.
        resolver = new RoleResolver(roleRepo);
    }

    @Test
    void shouldReturnEmptySetForUnknownUser() {
        when(roleRepo.findAllByTelegramUserIdAndStatus(999L, UserRoleStatus.ACTIVE))
            .thenReturn(List.of());

        Set<Role> roles = resolver.activeRolesOf(999L);

        assertThat(roles).isEmpty();
    }

    @Test
    void shouldReturnActiveRolesOnly() {
        UserRole r1 = makeRole(123L, Role.CUSTOMER, UserRoleStatus.ACTIVE);
        UserRole r2 = makeRole(123L, Role.SHIPPER, UserRoleStatus.ACTIVE);
        when(roleRepo.findAllByTelegramUserIdAndStatus(123L, UserRoleStatus.ACTIVE))
            .thenReturn(List.of(r1, r2));

        Set<Role> roles = resolver.activeRolesOf(123L);

        assertThat(roles).containsExactlyInAnyOrder(Role.CUSTOMER, Role.SHIPPER);
    }

    @Test
    void hasRoleShouldReturnTrueForAssignedActiveRole() {
        when(roleRepo.findAllByTelegramUserIdAndStatus(123L, UserRoleStatus.ACTIVE))
            .thenReturn(List.of(makeRole(123L, Role.CUSTOMER, UserRoleStatus.ACTIVE)));

        assertThat(resolver.hasRole(123L, Role.CUSTOMER)).isTrue();
        assertThat(resolver.hasRole(123L, Role.SHIPPER)).isFalse();
    }

    private UserRole makeRole(Long userId, Role role, UserRoleStatus status) {
        UserRole r = new UserRole();
        r.setTelegramUserId(userId);
        r.setRole(role);
        r.setStatus(status);
        return r;
    }
}
```

- [ ] **Step 3: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

Expected: COMPILATION ERROR.

- [ ] **Step 4: Implement `CacheConfig`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/config/CacheConfig.java`:

```java
package com.shop.delivery.auth.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_USER_ROLES = "auth.userRoles";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager(CACHE_USER_ROLES);
        mgr.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(10_000));
        return mgr;
    }
}
```

- [ ] **Step 5: Implement `RoleResolver`**

Create `backend/modules/auth/src/main/java/com/shop/delivery/auth/service/RoleResolver.java`:

```java
package com.shop.delivery.auth.service;

import com.shop.delivery.auth.config.CacheConfig;
import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.UserRoleRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleResolver {

    private final UserRoleRepository roleRepo;

    public RoleResolver(UserRoleRepository roleRepo) {
        this.roleRepo = roleRepo;
    }

    /**
     * Trả về tất cả role ACTIVE của user. Cache 5 phút (xem CacheConfig).
     * Trả về EnumSet rỗng nếu user không tồn tại hoặc không có role active nào.
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_USER_ROLES, key = "#telegramUserId")
    public Set<Role> activeRolesOf(Long telegramUserId) {
        Set<Role> roles = roleRepo.findAllByTelegramUserIdAndStatus(telegramUserId, UserRoleStatus.ACTIVE)
            .stream()
            .map(UserRole::getRole)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
        return roles;
    }

    public boolean hasRole(Long telegramUserId, Role role) {
        return activeRolesOf(telegramUserId).contains(role);
    }

    /** Gọi khi role của user thay đổi (assign/revoke) để invalidate cache. */
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_ROLES, key = "#telegramUserId")
    public void evict(Long telegramUserId) {
        // body trống — annotation làm hết
    }
}
```

- [ ] **Step 6: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/auth -am test
```

Expected: All 11 tests in auth pass.

- [ ] **Step 7: Commit**

```bash
git add backend/modules/auth/pom.xml \
        backend/modules/auth/src/main/java/com/shop/delivery/auth/config/CacheConfig.java \
        backend/modules/auth/src/main/java/com/shop/delivery/auth/service/RoleResolver.java \
        backend/modules/auth/src/test/java/com/shop/delivery/auth/service/RoleResolverTest.java
git commit -m "feat(auth): add RoleResolver with Caffeine cache (5min TTL)"
```

---

## TASK 7: Bot Module Dependencies + Migration `V3__bot.sql` (1 commit)

**Files:**
- Modify: `backend/modules/bot/pom.xml` — add telegrambots + auth dep
- Create: `backend/app/src/main/resources/db/migration/V3__bot.sql`

- [ ] **Step 1: Update `backend/modules/bot/pom.xml`**

Replace entire file content with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.shop.delivery</groupId>
        <artifactId>delivery-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>bot</artifactId>
    <name>bot</name>
    <description>Telegram bot webhook, router, handlers, FSM</description>

    <properties>
        <telegrambots.version>6.9.7.1</telegrambots.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.shop.delivery</groupId>
            <artifactId>shared</artifactId>
        </dependency>
        <dependency>
            <groupId>com.shop.delivery</groupId>
            <artifactId>auth</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <dependency>
            <groupId>org.telegram</groupId>
            <artifactId>telegrambots-spring-boot-starter</artifactId>
            <version>${telegrambots.version}</version>
            <exclusions>
                <!-- Avoid conflict with Spring Boot's logging -->
                <exclusion>
                    <groupId>commons-logging</groupId>
                    <artifactId>commons-logging</artifactId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Write `V3__bot.sql`**

Create `backend/app/src/main/resources/db/migration/V3__bot.sql`:

```sql
-- V3__bot.sql — bot module tables
-- Owns: processed_update (idempotency), conversation_state (FSM)

CREATE TABLE processed_update (
    update_id       BIGINT PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Cron sẽ xóa record cũ > 24h. Index hỗ trợ scan cleanup.
CREATE INDEX idx_processed_update_processed_at ON processed_update(processed_at);

CREATE TABLE conversation_state (
    telegram_user_id    BIGINT PRIMARY KEY REFERENCES telegram_user(id) ON DELETE CASCADE,
    state               VARCHAR(64) NOT NULL,
    data                JSONB,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- TTL 30 phút, cron sẽ xóa stale state
CREATE INDEX idx_conversation_state_updated_at ON conversation_state(updated_at);
```

- [ ] **Step 3: Verify bot module compiles + migration applies**

```bash
cd backend && ./mvnw -pl modules/bot -am compile
```

Expected: BUILD SUCCESS. (May take longer first run due to telegrambots dep download.)

Run a quick app startup to apply V3 (similar pattern as Task 1):
```bash
cd backend/app
nohup ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev > /tmp/p1_t7.log 2>&1 &
BOOT_PID=$!
for i in $(seq 1 60); do
  grep -q "Started Application" /tmp/p1_t7.log 2>/dev/null && break
  sleep 1
done
kill $BOOT_PID 2>/dev/null
sleep 2
```

Verify:
```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT version, success FROM flyway_schema_history ORDER BY version"
# Expected: 1 t, 2 t, 3 t
```

If app fails to start because no `BOT_TOKEN` env var, that's expected — but at this point app might startup ok because we haven't wired up DeliveryBot yet (that's Task 12). So Flyway should apply V3. If it doesn't because of missing token, document this and proceed — Task 12 will fix.

- [ ] **Step 4: Commit**

```bash
git add backend/modules/bot/pom.xml \
        backend/app/src/main/resources/db/migration/V3__bot.sql
git commit -m "feat(bot): add telegrambots dependency and V3 migration for bot tables"
```

---

## TASK 8: `BotProperties` Configuration (1 commit)

**Purpose:** Type-safe config for bot token, username, webhook URL, secret, mode (polling/webhook).

**Files:**
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/config/BotProperties.java`
- Modify: `backend/app/src/main/resources/application.yml` — add `bot:` section
- Modify: `backend/app/src/main/resources/application-dev.yml` — set polling defaults
- Modify: `backend/app/src/main/resources/application-prod.yml` — webhook config

- [ ] **Step 1: Write `BotProperties`**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/config/BotProperties.java`:

```java
package com.shop.delivery.bot.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bot")
public class BotProperties {

    @NotBlank
    private String token;

    @NotBlank
    private String username;

    /** "polling" hoặc "webhook" */
    private String mode = "polling";

    /** Chỉ dùng khi mode=webhook */
    private String webhookUrl;

    /** Secret token cho webhook verification (X-Telegram-Bot-Api-Secret-Token header) */
    private String webhookSecret;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
}
```

- [ ] **Step 2: Modify `application.yml` to add bot section**

Append to `backend/app/src/main/resources/application.yml`:

```yaml

bot:
  # token + username MUST be set via env vars or profile-specific config
  mode: polling
```

- [ ] **Step 3: Modify `application-dev.yml`**

Append to `backend/app/src/main/resources/application-dev.yml`:

```yaml

bot:
  token: ${BOT_TOKEN:dev-token-not-set}
  username: ${BOT_USERNAME:DevBot}
  mode: polling
```

Note: `dev-token-not-set` is a placeholder — app won't actually connect to Telegram if you keep this default. It just prevents `@NotBlank` failure at startup. For real bot interaction, `export BOT_TOKEN=<real_token>` before running.

- [ ] **Step 4: Modify `application-prod.yml`**

Append to `backend/app/src/main/resources/application-prod.yml`:

```yaml

bot:
  token: ${BOT_TOKEN}
  username: ${BOT_USERNAME}
  mode: webhook
  webhook-url: ${BOT_WEBHOOK_URL}
  webhook-secret: ${BOT_WEBHOOK_SECRET}
```

- [ ] **Step 5: Verify property binding compiles**

```bash
cd backend && ./mvnw -pl modules/bot -am compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/modules/bot/src/main/java/com/shop/delivery/bot/config/BotProperties.java \
        backend/app/src/main/resources/application.yml \
        backend/app/src/main/resources/application-dev.yml \
        backend/app/src/main/resources/application-prod.yml
git commit -m "feat(bot): add BotProperties configuration and profile defaults"
```

---

## TASK 9: ProcessedUpdate Idempotency (TDD, 1 commit)

**Purpose:** Telegram retries webhook on 5xx. We dedupe by `update_id`.

**Files:**
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/idempotency/ProcessedUpdate.java`
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/idempotency/ProcessedUpdateRepository.java`
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/idempotency/ProcessedUpdateService.java`
- Test: `backend/modules/bot/src/test/java/com/shop/delivery/bot/idempotency/ProcessedUpdateServiceTest.java`

- [ ] **Step 1: Write failing test**

Create `backend/modules/bot/src/test/java/com/shop/delivery/bot/idempotency/ProcessedUpdateServiceTest.java`:

```java
package com.shop.delivery.bot.idempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessedUpdateServiceTest {

    @Mock ProcessedUpdateRepository repo;

    @InjectMocks ProcessedUpdateService service;

    @Test
    void markProcessedShouldReturnTrueForNewUpdate() {
        when(repo.save(any(ProcessedUpdate.class))).thenReturn(new ProcessedUpdate(1L));

        boolean result = service.markIfNew(1L);

        assertThat(result).isTrue();
    }

    @Test
    void markProcessedShouldReturnFalseForDuplicate() {
        when(repo.save(any(ProcessedUpdate.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        boolean result = service.markIfNew(1L);

        assertThat(result).isFalse();
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/bot -am test
```

Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement `ProcessedUpdate` entity**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/idempotency/ProcessedUpdate.java`:

```java
package com.shop.delivery.bot.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "processed_update")
public class ProcessedUpdate {

    @Id
    @Column(name = "update_id")
    private Long updateId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    protected ProcessedUpdate() { /* JPA */ }

    public ProcessedUpdate(Long updateId) {
        this.updateId = updateId;
    }

    public Long getUpdateId() { return updateId; }
    public Instant getProcessedAt() { return processedAt; }
}
```

- [ ] **Step 4: Implement repository**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/idempotency/ProcessedUpdateRepository.java`:

```java
package com.shop.delivery.bot.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedUpdateRepository extends JpaRepository<ProcessedUpdate, Long> {
}
```

- [ ] **Step 5: Implement service**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/idempotency/ProcessedUpdateService.java`:

```java
package com.shop.delivery.bot.idempotency;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessedUpdateService {

    private final ProcessedUpdateRepository repo;

    public ProcessedUpdateService(ProcessedUpdateRepository repo) {
        this.repo = repo;
    }

    /**
     * Đánh dấu update đã xử lý. Trả true nếu là update mới, false nếu đã xử lý rồi.
     * Dùng @Transactional với REQUIRES_NEW để insert idempotent của riêng nó không
     * bị rollback chung với business transaction phía gọi.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markIfNew(Long updateId) {
        try {
            repo.save(new ProcessedUpdate(updateId));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }
}
```

- [ ] **Step 6: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/bot -am test
```

Expected: 2 tests pass in bot module.

- [ ] **Step 7: Commit**

```bash
git add backend/modules/bot/src/main/java/com/shop/delivery/bot/idempotency/ \
        backend/modules/bot/src/test/java/com/shop/delivery/bot/idempotency/
git commit -m "feat(bot): add ProcessedUpdate idempotency entity + service"
```

---

## TASK 10: `BotSender` Wrapper (1 commit)

**Purpose:** Centralized wrapper to send Telegram messages. Wraps `DefaultAbsSender` from telegrambots. Logging & error handling here. (Rate limiting via Bucket4j is later in P7+.)

**Files:**
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/sender/BotSender.java`

Note: this depends on the bot class itself (TASK 12) for the actual `execute()` call. For P1 we keep it simple: `BotSender` holds a reference to `DefaultAbsSender` (interface from telegrambots that both `TelegramLongPollingBot` and `TelegramWebhookBot` implement).

- [ ] **Step 1: Write `BotSender`**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/sender/BotSender.java`:

```java
package com.shop.delivery.bot.sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.io.Serializable;

/**
 * Wrapper xung quanh telegrambots' DefaultAbsSender với log + error handling.
 * Inject vào DeliveryBot trong Task 12 thông qua setter (vòng phụ thuộc).
 *
 * Lý do không inject @Lazy: muốn explicit về thứ tự khởi tạo. DeliveryBot
 * sẽ register chính nó vào BotSender khi nó được tạo (xem TASK 12).
 */
@Component
public class BotSender {

    private static final Logger log = LoggerFactory.getLogger(BotSender.class);

    private DefaultAbsSender sender;

    public void register(DefaultAbsSender sender) {
        this.sender = sender;
    }

    /**
     * Gửi method (SendMessage, EditMessageText, AnswerCallbackQuery, ...).
     * Trả về response của Telegram, hoặc null nếu lỗi (đã log).
     */
    public <T extends Serializable, M extends BotApiMethod<T>> T execute(M method) {
        if (sender == null) {
            log.error("BotSender not initialized — DeliveryBot must call register() at startup");
            return null;
        }
        try {
            return sender.execute(method);
        } catch (TelegramApiRequestException e) {
            // 403 user blocked, 400 chat not found, etc.
            log.warn("Telegram API error code={} desc='{}' method={}",
                e.getErrorCode(), e.getApiResponse(), method.getMethod());
            return null;
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message: method={}", method.getMethod(), e);
            return null;
        }
    }

    /** Helper cho trường hợp phổ biến nhất: gửi text message. */
    public void sendText(Long chatId, String text) {
        SendMessage msg = SendMessage.builder()
            .chatId(chatId)
            .text(text)
            .build();
        execute(msg);
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
cd backend && ./mvnw -pl modules/bot -am compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/modules/bot/src/main/java/com/shop/delivery/bot/sender/
git commit -m "feat(bot): add BotSender wrapper with error logging"
```

---

## TASK 11: `UpdateHandler` Interface + `UpdateRouter` + `StartHandler` (TDD, 1 commit)

**Files:**
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/UpdateHandler.java`
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/common/StartHandler.java`
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/common/HelpHandler.java`
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/common/UnknownCommandHandler.java`
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/router/UpdateRouter.java`
- Create: `backend/modules/bot/src/test/java/com/shop/delivery/bot/support/UpdateFixtures.java`
- Create: `backend/modules/bot/src/test/java/com/shop/delivery/bot/handler/common/StartHandlerTest.java`
- Create: `backend/modules/bot/src/test/java/com/shop/delivery/bot/router/UpdateRouterTest.java`

- [ ] **Step 1: Write `UpdateFixtures` helper**

Create `backend/modules/bot/src/test/java/com/shop/delivery/bot/support/UpdateFixtures.java`:

```java
package com.shop.delivery.bot.support;

import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

/**
 * Helper để tạo Update fixture cho tests. Telegram Update model là mutable
 * POJO nên cần build bằng setter.
 */
public final class UpdateFixtures {

    private UpdateFixtures() {}

    public static Update textMessage(long updateId, long userId, String username, String text) {
        Update u = new Update();
        u.setUpdateId((int) updateId);

        User from = new User();
        from.setId(userId);
        from.setIsBot(false);
        from.setFirstName("Test");
        from.setLastName("User");
        from.setUserName(username);
        from.setLanguageCode("vi");

        Chat chat = new Chat();
        chat.setId(userId);
        chat.setType("private");

        Message msg = new Message();
        msg.setMessageId((int) updateId);
        msg.setFrom(from);
        msg.setChat(chat);
        msg.setText(text);
        msg.setDate((int) (System.currentTimeMillis() / 1000));

        u.setMessage(msg);
        return u;
    }
}
```

- [ ] **Step 2: Write failing tests**

Create `backend/modules/bot/src/test/java/com/shop/delivery/bot/handler/common/StartHandlerTest.java`:

```java
package com.shop.delivery.bot.handler.common;

import com.shop.delivery.auth.service.TelegramUserService;
import com.shop.delivery.auth.service.TelegramUserUpsertCommand;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.bot.support.UpdateFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StartHandlerTest {

    @Mock TelegramUserService userService;
    @Mock BotSender sender;

    @InjectMocks StartHandler handler;

    @Test
    void handleShouldRegisterUserAndSendWelcomeText() {
        Update update = UpdateFixtures.textMessage(1L, 1001L, "alice", "/start");

        handler.handle(update);

        ArgumentCaptor<TelegramUserUpsertCommand> userCaptor = ArgumentCaptor.forClass(TelegramUserUpsertCommand.class);
        verify(userService).registerOrUpdate(userCaptor.capture());
        assertThat(userCaptor.getValue().id()).isEqualTo(1001L);
        assertThat(userCaptor.getValue().username()).isEqualTo("alice");
        assertThat(userCaptor.getValue().firstName()).isEqualTo("Test");
        assertThat(userCaptor.getValue().languageCode()).isEqualTo("vi");

        ArgumentCaptor<Long> chatIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(chatIdCaptor.capture(), textCaptor.capture());
        assertThat(chatIdCaptor.getValue()).isEqualTo(1001L);
        assertThat(textCaptor.getValue()).contains("Chào");
    }

    @Test
    void canHandleShouldReturnTrueForSlashStart() {
        Update update = UpdateFixtures.textMessage(1L, 1001L, "alice", "/start");
        assertThat(handler.canHandle(update)).isTrue();
    }

    @Test
    void canHandleShouldReturnFalseForOtherText() {
        Update update = UpdateFixtures.textMessage(1L, 1001L, "alice", "hello");
        assertThat(handler.canHandle(update)).isFalse();
    }
}
```

Create `backend/modules/bot/src/test/java/com/shop/delivery/bot/router/UpdateRouterTest.java`:

```java
package com.shop.delivery.bot.router;

import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.handler.common.HelpHandler;
import com.shop.delivery.bot.handler.common.StartHandler;
import com.shop.delivery.bot.handler.common.UnknownCommandHandler;
import com.shop.delivery.bot.idempotency.ProcessedUpdateService;
import com.shop.delivery.bot.support.UpdateFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateRouterTest {

    @Mock StartHandler startHandler;
    @Mock HelpHandler helpHandler;
    @Mock UnknownCommandHandler unknownCommandHandler;
    @Mock ProcessedUpdateService processedUpdateService;

    @Test
    void shouldRouteToStartHandlerForSlashStart() {
        when(processedUpdateService.markIfNew(any())).thenReturn(true);
        when(startHandler.canHandle(any(Update.class))).thenReturn(true);

        UpdateRouter router = new UpdateRouter(
            List.of(startHandler, helpHandler),
            unknownCommandHandler,
            processedUpdateService
        );

        Update update = UpdateFixtures.textMessage(1L, 1001L, "u", "/start");
        router.route(update);

        verify(startHandler).handle(update);
        verify(helpHandler, never()).handle(any());
        verify(unknownCommandHandler, never()).handle(any());
    }

    @Test
    void shouldFallbackToUnknownCommandWhenNoHandlerCanHandle() {
        when(processedUpdateService.markIfNew(any())).thenReturn(true);
        when(startHandler.canHandle(any(Update.class))).thenReturn(false);
        when(helpHandler.canHandle(any(Update.class))).thenReturn(false);

        UpdateRouter router = new UpdateRouter(
            List.of(startHandler, helpHandler),
            unknownCommandHandler,
            processedUpdateService
        );

        Update update = UpdateFixtures.textMessage(1L, 1001L, "u", "/wat");
        router.route(update);

        verify(unknownCommandHandler).handle(update);
    }

    @Test
    void duplicateUpdateShouldBeSkipped() {
        when(processedUpdateService.markIfNew(any())).thenReturn(false);

        UpdateRouter router = new UpdateRouter(
            List.of(startHandler),
            unknownCommandHandler,
            processedUpdateService
        );

        router.route(UpdateFixtures.textMessage(1L, 1001L, "u", "/start"));

        verify(startHandler, never()).canHandle(any());
        verify(startHandler, never()).handle(any());
    }
}
```

- [ ] **Step 3: Run tests — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/bot -am test
```

Expected: COMPILATION ERROR.

- [ ] **Step 4: Implement `UpdateHandler` interface**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/UpdateHandler.java`:

```java
package com.shop.delivery.bot.handler;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface UpdateHandler {

    /** Trả về true nếu handler có thể xử lý update này. UpdateRouter sẽ gọi sequentially. */
    boolean canHandle(Update update);

    /** Xử lý update. Side effects: gọi BotSender, gọi services khác. Không throw — log và quay lại. */
    void handle(Update update);
}
```

- [ ] **Step 5: Implement `StartHandler`**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/common/StartHandler.java`:

```java
package com.shop.delivery.bot.handler.common;

import com.shop.delivery.auth.service.TelegramUserService;
import com.shop.delivery.auth.service.TelegramUserUpsertCommand;
import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

@Component
public class StartHandler implements UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(StartHandler.class);
    private static final String WELCOME = """
        Chào %s! 👋

        Đây là bot quản lý giao hàng. Bấm nút bên dưới để bắt đầu đặt hàng.

        Bạn cũng có thể gõ:
        /myorders — Xem đơn hàng của tôi
        /help — Trợ giúp
        """;

    private final TelegramUserService userService;
    private final BotSender sender;

    public StartHandler(TelegramUserService userService, BotSender sender) {
        this.userService = userService;
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return false;
        String text = update.getMessage().getText();
        return text != null && text.trim().startsWith("/start");
    }

    @Override
    public void handle(Update update) {
        User from = update.getMessage().getFrom();
        Long userId = from.getId();
        Long chatId = update.getMessage().getChatId();

        userService.registerOrUpdate(new TelegramUserUpsertCommand(
            userId,
            from.getUserName(),
            from.getFirstName(),
            from.getLastName(),
            from.getLanguageCode()
        ));

        String displayName = from.getFirstName() != null ? from.getFirstName() : "bạn";
        sender.sendText(chatId, String.format(WELCOME, displayName));

        log.info("Handled /start for userId={} username={}", userId, from.getUserName());
    }
}
```

- [ ] **Step 6: Implement `HelpHandler`**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/common/HelpHandler.java`:

```java
package com.shop.delivery.bot.handler.common;

import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class HelpHandler implements UpdateHandler {

    private static final String HELP_TEXT = """
        📖 Hướng dẫn

        Lệnh thường dùng:
        /start — Bắt đầu / xem menu chính
        /myorders — Xem đơn hàng của tôi
        /help — Trợ giúp

        Cần hỗ trợ? Liên hệ shop qua @yourshop
        """;

    private final BotSender sender;

    public HelpHandler(BotSender sender) {
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return false;
        return update.getMessage().getText().trim().startsWith("/help");
    }

    @Override
    public void handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        sender.sendText(chatId, HELP_TEXT);
    }
}
```

- [ ] **Step 7: Implement `UnknownCommandHandler`**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/common/UnknownCommandHandler.java`:

```java
package com.shop.delivery.bot.handler.common;

import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class UnknownCommandHandler implements UpdateHandler {

    private final BotSender sender;

    public UnknownCommandHandler(BotSender sender) {
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update update) {
        return false; // chỉ làm fallback, UpdateRouter gọi trực tiếp
    }

    @Override
    public void handle(Update update) {
        if (!update.hasMessage()) return;
        Long chatId = update.getMessage().getChatId();
        sender.sendText(chatId, "Mình chưa hiểu lệnh này. Gõ /help để xem hướng dẫn.");
    }
}
```

- [ ] **Step 8: Implement `UpdateRouter`**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/router/UpdateRouter.java`:

```java
package com.shop.delivery.bot.router;

import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.handler.common.UnknownCommandHandler;
import com.shop.delivery.bot.idempotency.ProcessedUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
public class UpdateRouter {

    private static final Logger log = LoggerFactory.getLogger(UpdateRouter.class);

    private final List<UpdateHandler> handlers;
    private final UnknownCommandHandler fallback;
    private final ProcessedUpdateService processedUpdateService;

    public UpdateRouter(List<UpdateHandler> handlers,
                        UnknownCommandHandler fallback,
                        ProcessedUpdateService processedUpdateService) {
        // Filter out fallback từ injected list để tránh đệ quy
        this.handlers = handlers.stream()
            .filter(h -> !(h instanceof UnknownCommandHandler))
            .toList();
        this.fallback = fallback;
        this.processedUpdateService = processedUpdateService;
    }

    public void route(Update update) {
        Long updateId = (long) update.getUpdateId();
        if (!processedUpdateService.markIfNew(updateId)) {
            log.debug("Skip duplicate update_id={}", updateId);
            return;
        }

        try {
            for (UpdateHandler h : handlers) {
                if (h.canHandle(update)) {
                    h.handle(update);
                    return;
                }
            }
            // Không handler nào claim — fallback
            fallback.handle(update);
        } catch (Exception e) {
            log.error("Unhandled error processing update_id={}", updateId, e);
            // Không re-throw; webhook reply 200 OK để Telegram không retry vô tận
        }
    }
}
```

- [ ] **Step 9: Run tests — expect pass**

```bash
cd backend && ./mvnw -pl modules/bot -am test
```

Expected: All bot module tests pass (2 from Task 9 + 3 StartHandler + 3 UpdateRouter = 8 tests).

- [ ] **Step 10: Commit**

```bash
git add backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/ \
        backend/modules/bot/src/main/java/com/shop/delivery/bot/router/ \
        backend/modules/bot/src/test/java/com/shop/delivery/bot/
git commit -m "feat(bot): add UpdateRouter + StartHandler/HelpHandler/UnknownCommandHandler"
```

---

## TASK 12: `DeliveryBot` Class + Bot Registration (1 commit)

**Purpose:** The actual `TelegramLongPollingBot` (dev profile only). For webhook mode (prod), this class becomes `TelegramWebhookBot` instead — Task 13 handles that.

**Files:**
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/DeliveryBot.java`
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/config/BotRegistrationConfig.java`

- [ ] **Step 1: Write `DeliveryBot`**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/DeliveryBot.java`:

```java
package com.shop.delivery.bot;

import com.shop.delivery.bot.config.BotProperties;
import com.shop.delivery.bot.router.UpdateRouter;
import com.shop.delivery.bot.sender.BotSender;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Long-polling bot — chỉ activate khi bot.mode=polling (default cho dev).
 * Production dùng webhook controller (TASK 13) thay cho class này.
 */
@Component
@ConditionalOnProperty(prefix = "bot", name = "mode", havingValue = "polling", matchIfMissing = true)
public class DeliveryBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(DeliveryBot.class);

    private final BotProperties props;
    private final UpdateRouter router;
    private final BotSender sender;

    public DeliveryBot(BotProperties props, UpdateRouter router, BotSender sender) {
        super(props.getToken());
        this.props = props;
        this.router = router;
        this.sender = sender;
    }

    @PostConstruct
    void wireUpSender() {
        sender.register(this);
        log.info("DeliveryBot initialized: username={}, mode=polling", props.getUsername());
    }

    @Override
    public String getBotUsername() {
        return props.getUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        log.debug("Received update: id={}", update.getUpdateId());
        router.route(update);
    }
}
```

- [ ] **Step 2: Write `BotRegistrationConfig`**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/config/BotRegistrationConfig.java`:

```java
package com.shop.delivery.bot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Marker config để enable @ConfigurationProperties cho bot.
 * telegrambots-spring-boot-starter sẽ auto-register bất kỳ class nào extends
 * TelegramLongPollingBot và được @Component.
 */
@Configuration
@EnableConfigurationProperties(BotProperties.class)
public class BotRegistrationConfig {
}
```

- [ ] **Step 3: Verify the app compiles and (with dummy token) the context loads**

```bash
cd backend && ./mvnw -pl app -am compile
```

Expected: BUILD SUCCESS.

Quick smoke (app should boot with `BOT_TOKEN=dummy`):
```bash
cd backend/app
BOT_TOKEN=dummytoken-just-for-startup BOT_USERNAME=DummyBot \
  nohup ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev > /tmp/p1_t12.log 2>&1 &
BOOT_PID=$!
for i in $(seq 1 60); do
  grep -q "Started Application" /tmp/p1_t12.log 2>/dev/null && break
  sleep 1
done

# Check for DeliveryBot initialization log
grep "DeliveryBot initialized" /tmp/p1_t12.log

# DeliveryBot will try to connect to Telegram API, will fail with dummytoken, that's OK
grep "Error registering bot" /tmp/p1_t12.log  # expected: some 401 from Telegram

kill $BOOT_PID 2>/dev/null
sleep 2
```

The point of this verification: confirm Spring context loads and DeliveryBot bean is created. The actual Telegram API connection will fail with dummy token — that's expected. Real bot token testing is in TASK 14.

- [ ] **Step 4: Commit**

```bash
git add backend/modules/bot/src/main/java/com/shop/delivery/bot/DeliveryBot.java \
        backend/modules/bot/src/main/java/com/shop/delivery/bot/config/BotRegistrationConfig.java
git commit -m "feat(bot): add DeliveryBot long-polling implementation"
```

---

## TASK 13: Webhook Controller (1 commit)

**Purpose:** Receive webhook from Telegram in prod mode. Verify secret token header, route via `UpdateRouter`.

This is the prod alternative to `DeliveryBot` long polling. They are mutually exclusive (`@ConditionalOnProperty bot.mode`).

**Files:**
- Create: `backend/modules/bot/src/main/java/com/shop/delivery/bot/controller/BotWebhookController.java`

- [ ] **Step 1: Write `BotWebhookController`**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/controller/BotWebhookController.java`:

```java
package com.shop.delivery.bot.controller;

import com.shop.delivery.bot.config.BotProperties;
import com.shop.delivery.bot.router.UpdateRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Webhook endpoint cho Telegram (prod profile).
 *
 * Telegram POST /api/bot/webhook với header X-Telegram-Bot-Api-Secret-Token = bot.webhook-secret.
 * Trả 200 OK trong mọi trường hợp (kể cả lỗi) để Telegram không retry vô tận;
 * UpdateRouter đã có error handling nội bộ.
 */
@RestController
@RequestMapping("/api/bot")
@ConditionalOnProperty(prefix = "bot", name = "mode", havingValue = "webhook")
public class BotWebhookController {

    private static final Logger log = LoggerFactory.getLogger(BotWebhookController.class);
    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final UpdateRouter router;
    private final BotProperties props;

    public BotWebhookController(UpdateRouter router, BotProperties props) {
        this.router = router;
        this.props = props;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader(value = SECRET_HEADER, required = false) String secret,
                                        @RequestBody Update update) {
        if (!constantTimeEquals(props.getWebhookSecret(), secret)) {
            log.warn("Webhook called with invalid secret header — IP unknown via this layer");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        router.route(update);
        return ResponseEntity.ok().build();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
cd backend && ./mvnw -pl modules/bot -am compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/modules/bot/src/main/java/com/shop/delivery/bot/controller/
git commit -m "feat(bot): add webhook controller with constant-time secret verification"
```

---

## TASK 14: Smoke Test với Real Bot Token (verification, no commit)

**Purpose:** Verify entire P1 end-to-end. Requires real Telegram bot token. If you don't have one yet, skip this task and complete it manually later.

**Prerequisites:**
1. Open Telegram → search [@BotFather](https://t.me/botfather) → `/newbot` → choose name + username → save the token.
2. Postgres dev container running.
3. Port 8080 free (or change `server.port`).

- [ ] **Step 1: Set env vars and start backend**

```bash
export BOT_TOKEN="<paste-real-token-here>"
export BOT_USERNAME="<paste-your-bot-username>"

cd backend/app
../mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Wait for:
```
DeliveryBot initialized: username=<YourBot>, mode=polling
Started Application in X.XXX seconds
```

If you see `Error registering bot... 401 Unauthorized` — wrong token. Stop, fix, retry.

- [ ] **Step 2: From your phone, open Telegram and send `/start` to your bot**

You should receive within a few seconds:
```
Chào Test! 👋

Đây là bot quản lý giao hàng. ...
```

If you don't get a reply, check `/tmp/sboot.log` or the terminal where Spring Boot runs for errors.

- [ ] **Step 3: Verify user persisted to DB**

```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT id, username, first_name, last_name, language_code, created_at FROM telegram_user"
```

Expected: 1 row (your Telegram user).

```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT telegram_user_id, role, status FROM user_role"
```

Expected: 1 row with `role=CUSTOMER`, `status=ACTIVE`.

- [ ] **Step 4: Send `/start` again, verify idempotent**

Phone → bot → `/start`. Bot replies again.

Verify only 1 row in telegram_user (no duplicate):
```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT COUNT(*) FROM telegram_user"
# Expected: 1
```

Verify only 1 CUSTOMER role:
```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT COUNT(*) FROM user_role WHERE role='CUSTOMER'"
# Expected: 1
```

Verify processed_update has 2 entries:
```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT COUNT(*) FROM processed_update"
# Expected: 2
```

- [ ] **Step 5: Send `/help`, verify HelpHandler responds**

Phone → bot → `/help`. Expected reply: 📖 Hướng dẫn ...

- [ ] **Step 6: Send unknown command, verify UnknownCommandHandler**

Phone → bot → `/wat`. Expected reply: "Mình chưa hiểu lệnh này. Gõ /help để xem hướng dẫn."

- [ ] **Step 7: Stop backend**

`Ctrl+C` in Spring Boot terminal.

---

## Acceptance Criteria (P1 done when ALL true)

- [x] `cd backend && ./mvnw clean verify` exits with BUILD SUCCESS
- [x] All tests pass (P0 10 + P1 ~16 new = ~26 tests)
- [x] Flyway migrated V1 (init), V2 (auth), V3 (bot)
- [x] Smoke test passes:
  - Bot replies to `/start` with welcome
  - User registered in `telegram_user` table with correct Telegram ID
  - `user_role` has 1 row with `role=CUSTOMER`, `status=ACTIVE`
  - Repeated `/start` is idempotent (no duplicate rows)
  - `/help` works
  - Unknown command falls back to UnknownCommandHandler
- [x] No regressions in P0 (HealthCheckIT still passes)

---

## Known Limitations of P1 (giải quyết ở plan sau)

| Limitation | Plan |
|---|---|
| Shipper FSM registration | P2 (order/shipper extension) hoặc P5 (delivery) |
| `/myorders`, customer-specific handlers | P3 (Mini App) |
| Bot rate limiting (Bucket4j) | P9 (deploy hardening) |
| Mini App initData verification (separate auth path) | P3 |
| Web Admin JWT login | P4 |
| Webhook actually configured in prod with HTTPS | P9 |
| Cleanup cron cho processed_update & conversation_state | P9 |

---

## Troubleshooting

**`@NotBlank validation failed for bot.token`:** Set env var `BOT_TOKEN=...` before running.

**`401 Unauthorized: invalid token`:** Token wrong or revoked. Get new one from @BotFather (`/revoke`).

**Bot không reply:** Kiểm tra:
- App đang chạy không? `lsof -i :8080`
- Bot có log "Received update"? Nếu không — token sai hoặc Telegram chặn IP.
- DB connection OK? `curl localhost:8080/actuator/health`

**`org.springframework.dao.DataIntegrityViolationException: ERROR: insert or update on table "user_role" violates foreign key constraint`:**
- User chưa được register trước khi assign role. StartHandler save user trước, role sau — đảm bảo cùng transaction.

**Flyway V3 fails với `relation "telegram_user" does not exist`:**
- V2 phải apply trước V3 (Flyway tự sort theo version number, OK).

**Build fail trong `auth` module với "Cannot find class telegrambots":**
- `auth` không nên import `telegrambots` — chỉ `bot` được. Kiểm tra dependency.

---

**END OF P1 PLAN**
