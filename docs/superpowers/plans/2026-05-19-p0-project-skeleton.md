# P0 — Project Skeleton & Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tạo bộ khung dự án Maven multi-module Spring Boot, kết nối Postgres qua Docker Compose dev, Flyway baseline, và endpoint `/actuator/health` trả `UP` với DB status — sẵn sàng cho các module nghiệp vụ ở P1+.

**Architecture:** Maven multi-module với 1 parent POM + 9 module library (8 bounded contexts + 1 `shared`) + 1 `app` boot module. Tất cả module compile thành jar; chỉ `app` đóng gói fat-jar có Application class. Postgres chạy trong Docker, backend chạy local IDE/CLI lúc dev. Migration tập trung tại `app/src/main/resources/db/migration/`.

**Tech Stack:**
- Java 17 (LTS) + Maven 3.9+
- Spring Boot 3.4.0 (Spring Web, Actuator, JPA, Validation)
- PostgreSQL 16 (Docker)
- Flyway 10.x (auto-migrate khi app start)
- HikariCP (default connection pool)
- Testcontainers 1.20.4 (integration test với Postgres thật)
- JUnit 5 + AssertJ + Spring Boot Test
- Logback + logstash-logback-encoder (JSON log cho prod)
- GitHub Actions (CI: mvn verify)

---

## File Structure (sau khi P0 hoàn thành)

```
KhoaLuan-GiaoHang/
├── .gitignore                                   (đã có)
├── .editorconfig                                (TASK 1)
├── README.md                                    (TASK 1)
├── .github/
│   └── workflows/
│       └── backend.yml                          (TASK 11)
├── docs/                                        (đã có spec + plan)
├── backend/
│   ├── pom.xml                                  (TASK 2 — parent)
│   ├── modules/
│   │   ├── shared/
│   │   │   ├── pom.xml                          (TASK 3)
│   │   │   └── src/
│   │   │       ├── main/java/com/shop/delivery/shared/
│   │   │       │   ├── domain/BaseEntity.java   (TASK 4)
│   │   │       │   ├── exception/DomainException.java       (TASK 5)
│   │   │       │   ├── exception/NotFoundException.java     (TASK 5)
│   │   │       │   ├── exception/ValidationException.java   (TASK 5)
│   │   │       │   ├── exception/BusinessRuleException.java (TASK 5)
│   │   │       │   ├── exception/ConflictException.java     (TASK 5)
│   │   │       │   ├── exception/ExternalServiceException.java (TASK 5)
│   │   │       │   └── util/Result.java         (TASK 5)
│   │   │       └── test/java/com/shop/delivery/shared/
│   │   │           ├── domain/BaseEntityTest.java (TASK 4)
│   │   │           └── exception/DomainExceptionTest.java (TASK 5)
│   │   ├── auth/
│   │   │   ├── pom.xml                          (TASK 6)
│   │   │   └── src/main/java/com/shop/delivery/auth/.gitkeep  (TASK 6)
│   │   ├── order/        (same shape)           (TASK 6)
│   │   ├── delivery/     (same shape)           (TASK 6)
│   │   ├── payment/      (same shape)           (TASK 6)
│   │   ├── notification/ (same shape)           (TASK 6)
│   │   ├── bot/          (same shape)           (TASK 6)
│   │   ├── miniapp/      (same shape)           (TASK 6)
│   │   └── webadmin/     (same shape)           (TASK 6)
│   └── app/
│       ├── pom.xml                              (TASK 7)
│       └── src/
│           ├── main/
│           │   ├── java/com/shop/delivery/
│           │   │   └── Application.java         (TASK 7)
│           │   └── resources/
│           │       ├── application.yml          (TASK 8)
│           │       ├── application-dev.yml      (TASK 8)
│           │       ├── application-prod.yml     (TASK 8)
│           │       ├── logback-spring.xml       (TASK 8)
│           │       └── db/migration/
│           │           └── V1__init.sql         (TASK 9)
│           └── test/
│               ├── java/com/shop/delivery/
│               │   ├── ApplicationTests.java    (TASK 7)
│               │   ├── HealthCheckIT.java       (TASK 10)
│               │   └── support/PostgresTestContainer.java (TASK 10)
│               └── resources/
│                   └── application-test.yml     (TASK 10)
├── frontend/
│   └── README.md                                (TASK 1 — placeholder)
└── infra/
    ├── README.md                                (TASK 1)
    ├── docker-compose.dev.yml                   (TASK 9)
    └── postgres/init.sql                        (TASK 9)
```

**Tổng số file mới:** ~35

---

## TASK 1: Monorepo Directory Structure + Top-level Files

**Files:**
- Create: `README.md`
- Create: `.editorconfig`
- Create: `backend/README.md`, `frontend/README.md`, `infra/README.md`
- Create: empty `.gitkeep` cho `backend/`, `frontend/`, `infra/`

- [ ] **Step 1: Create directory structure**

```bash
mkdir -p backend/modules/{shared,auth,order,delivery,payment,notification,bot,miniapp,webadmin}/src/main/java
mkdir -p backend/app/src/main/{java,resources}
mkdir -p backend/app/src/test/java
mkdir -p frontend
mkdir -p infra/postgres
mkdir -p .github/workflows
```

Verify: `find backend frontend infra .github -type d | sort` should list all created dirs.

- [ ] **Step 2: Write top-level `README.md`**

```markdown
# Hệ thống Hỗ trợ Quản lý Giao hàng

Khóa luận tốt nghiệp — Đề tài 3.

Hệ thống quản lý giao hàng cho shop online, sử dụng Telegram Mini App + Bot cho khách & shipper, và Web Admin cho chủ shop. Thanh toán COD + VNPay.

## Cấu trúc

- `backend/` — Spring Boot 3 multi-module (8 bounded contexts)
- `frontend/` — Mini App (React + Vite) + Web Admin (React + Vite)
- `infra/` — Docker Compose, Nginx, Postgres init
- `docs/` — Spec (`docs/superpowers/specs/`) + Plans (`docs/superpowers/plans/`) + Thesis chapters (`docs/thesis/`)

## Quick start (dev)

```bash
# 1. Start Postgres
docker compose -f infra/docker-compose.dev.yml up -d

# 2. Build & run backend
cd backend && ./mvnw spring-boot:run -pl app -am -Dspring-boot.run.profiles=dev
```

Backend chạy ở `http://localhost:8080`. Health check: `http://localhost:8080/actuator/health`.

## Tài liệu

- [Design Spec](docs/superpowers/specs/2026-05-19-he-thong-quan-ly-giao-hang-design.md)
- [Implementation Plans](docs/superpowers/plans/)
```

- [ ] **Step 3: Write `.editorconfig`**

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
indent_style = space
insert_final_newline = true
trim_trailing_whitespace = true

[*.{java,xml}]
indent_size = 4

[*.{yml,yaml,json,js,jsx,ts,tsx,md}]
indent_size = 2

[Makefile]
indent_style = tab
```

- [ ] **Step 4: Write subdirectory READMEs**

`backend/README.md`:
```markdown
# Backend (Spring Boot 3 Multi-module)

## Modules

| Module | Trách nhiệm |
|---|---|
| `shared` | Base entity, exceptions, common utils |
| `auth` | Telegram user, role, JWT |
| `order` | Product, order, order_item |
| `delivery` | Assignment, location_ping |
| `payment` | VNPay integration |
| `notification` | Event listener → Telegram + WS |
| `bot` | Telegram bot webhook & handlers |
| `miniapp` | REST + WS cho Mini App |
| `webadmin` | REST + WS cho Web Admin |
| `app` | Boot module (Application.java) |

## Build

```bash
./mvnw clean verify
```

## Run dev

```bash
./mvnw spring-boot:run -pl app -am -Dspring-boot.run.profiles=dev
```
```

`frontend/README.md`:
```markdown
# Frontend (placeholder — chi tiết ở P3+)

Sẽ chứa 2 ứng dụng React + Vite:
- `miniapp/` — Telegram Mini App (Khách + Shipper)
- `webadmin/` — Web Admin (Chủ shop)
- `shared/` — Types + API client dùng chung

Sẽ được khởi tạo ở Plan P3 (Mini App foundation).
```

`infra/README.md`:
```markdown
# Infrastructure

- `docker-compose.dev.yml` — Postgres only (dev). Backend chạy IDE/CLI ngoài Docker.
- `docker-compose.yml` — Full stack (postgres + backend + frontend + nginx). Sẽ hoàn thiện ở P9.
- `postgres/init.sql` — Khởi tạo DB lần đầu (create database, user).
```

- [ ] **Step 5: Commit**

```bash
git add README.md .editorconfig backend/README.md frontend/README.md infra/README.md
git commit -m "chore: bootstrap monorepo structure with placeholder READMEs"
```

Expected: 1 commit, 5 files added.

---

## TASK 2: Maven Parent POM

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/.mvn/wrapper/maven-wrapper.properties`
- Create: `backend/mvnw`, `backend/mvnw.cmd` (Maven Wrapper)

- [ ] **Step 1: Install Maven Wrapper**

```bash
cd backend
mvn -N wrapper:wrapper -Dmaven=3.9.9
cd ..
```

Verify: `ls backend/` should show `mvnw`, `mvnw.cmd`, `.mvn/`.

- [ ] **Step 2: Write `backend/pom.xml` (parent)**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.0</version>
        <relativePath/>
    </parent>

    <groupId>com.shop.delivery</groupId>
    <artifactId>delivery-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>delivery-parent</name>
    <description>Shop delivery management — parent POM</description>

    <modules>
        <module>modules/shared</module>
        <module>modules/auth</module>
        <module>modules/order</module>
        <module>modules/delivery</module>
        <module>modules/payment</module>
        <module>modules/notification</module>
        <module>modules/bot</module>
        <module>modules/miniapp</module>
        <module>modules/webadmin</module>
        <module>app</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>

        <!-- Tracked versions (Spring Boot manages most) -->
        <testcontainers.version>1.20.4</testcontainers.version>
        <logstash-logback.version>8.0</logstash-logback.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Internal modules -->
            <dependency>
                <groupId>com.shop.delivery</groupId>
                <artifactId>shared</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.shop.delivery</groupId>
                <artifactId>auth</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.shop.delivery</groupId>
                <artifactId>order</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.shop.delivery</groupId>
                <artifactId>delivery</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.shop.delivery</groupId>
                <artifactId>payment</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.shop.delivery</groupId>
                <artifactId>notification</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.shop.delivery</groupId>
                <artifactId>bot</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.shop.delivery</groupId>
                <artifactId>miniapp</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.shop.delivery</groupId>
                <artifactId>webadmin</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- Testcontainers BOM -->
            <dependency>
                <groupId>org.testcontainers</groupId>
                <artifactId>testcontainers-bom</artifactId>
                <version>${testcontainers.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Logstash encoder -->
            <dependency>
                <groupId>net.logstash.logback</groupId>
                <artifactId>logstash-logback-encoder</artifactId>
                <version>${logstash-logback.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                        <parameters>true</parameters>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <argLine>-Xshare:off</argLine>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <configuration>
                        <includes>
                            <include>**/*IT.java</include>
                        </includes>
                    </configuration>
                    <executions>
                        <execution>
                            <goals>
                                <goal>integration-test</goal>
                                <goal>verify</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 3: Verify parent POM**

```bash
cd backend && ./mvnw validate
```

Expected: `BUILD SUCCESS`. May warn about missing module pom.xml files — that's OK, we add them next.

- [ ] **Step 4: Commit**

```bash
git add backend/pom.xml backend/mvnw backend/mvnw.cmd backend/.mvn/
git commit -m "chore(backend): add Maven parent POM and wrapper"
```

---

## TASK 3: Module `shared` — POM

**Files:**
- Create: `backend/modules/shared/pom.xml`

- [ ] **Step 1: Write `backend/modules/shared/pom.xml`**

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

    <artifactId>shared</artifactId>
    <name>shared</name>
    <description>Base entity, exceptions, common utils</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Verify**

```bash
cd backend && ./mvnw -pl modules/shared validate
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add backend/modules/shared/pom.xml
git commit -m "chore(shared): add pom.xml for shared module"
```

---

## TASK 4: `BaseEntity` (TDD)

**Files:**
- Create: `backend/modules/shared/src/main/java/com/shop/delivery/shared/domain/BaseEntity.java`
- Test: `backend/modules/shared/src/test/java/com/shop/delivery/shared/domain/BaseEntityTest.java`

- [ ] **Step 1: Write failing test**

Create `backend/modules/shared/src/test/java/com/shop/delivery/shared/domain/BaseEntityTest.java`:

```java
package com.shop.delivery.shared.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    static class TestEntity extends BaseEntity {
    }

    @Test
    void shouldExposeCreatedAtAndUpdatedAtFields() {
        TestEntity e = new TestEntity();
        Instant now = Instant.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);

        assertThat(e.getCreatedAt()).isEqualTo(now);
        assertThat(e.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void twoEntitiesWithNullIdsShouldNotBeEqual() {
        TestEntity a = new TestEntity();
        TestEntity b = new TestEntity();
        assertThat(a).isNotEqualTo(b);
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/shared test
```

Expected: `COMPILATION ERROR` (BaseEntity does not exist).

- [ ] **Step 3: Implement `BaseEntity`**

Create `backend/modules/shared/src/main/java/com/shop/delivery/shared/domain/BaseEntity.java`:

```java
package com.shop.delivery.shared.domain;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.Instant;
import java.util.Objects;

/**
 * Base class cho mọi JPA entity. Cung cấp createdAt, updatedAt tự động.
 * Identity equality (a.equals(b) iff a == b) khi id chưa được set.
 */
@MappedSuperclass
public abstract class BaseEntity {

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getClass().getName());
    }
}
```

- [ ] **Step 4: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/shared test
```

Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add backend/modules/shared/src/
git commit -m "feat(shared): add BaseEntity with createdAt/updatedAt"
```

---

## TASK 5: Domain Exception Hierarchy (TDD)

**Files:**
- Create: 6 file trong `backend/modules/shared/src/main/java/com/shop/delivery/shared/exception/`
- Test: `backend/modules/shared/src/test/java/com/shop/delivery/shared/exception/DomainExceptionTest.java`

- [ ] **Step 1: Write failing test**

Create `backend/modules/shared/src/test/java/com/shop/delivery/shared/exception/DomainExceptionTest.java`:

```java
package com.shop.delivery.shared.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionTest {

    @Test
    void notFoundShouldExposeCodeAndMessage() {
        NotFoundException ex = new NotFoundException("USER_NOT_FOUND", "User 123 không tồn tại");
        assertThat(ex.getCode()).isEqualTo("USER_NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("User 123 không tồn tại");
        assertThat(ex).isInstanceOf(DomainException.class);
    }

    @Test
    void validationException() {
        ValidationException ex = new ValidationException("INVALID_PHONE", "SĐT không đúng định dạng");
        assertThat(ex.getCode()).isEqualTo("INVALID_PHONE");
    }

    @Test
    void businessRuleException() {
        BusinessRuleException ex = new BusinessRuleException("ORDER_NOT_CANCELLABLE", "Đơn đã giao");
        assertThat(ex.getCode()).isEqualTo("ORDER_NOT_CANCELLABLE");
    }

    @Test
    void conflictException() {
        ConflictException ex = new ConflictException("VERSION_CONFLICT", "Optimistic lock");
        assertThat(ex.getCode()).isEqualTo("VERSION_CONFLICT");
    }

    @Test
    void externalServiceException() {
        Throwable cause = new RuntimeException("Connection refused");
        ExternalServiceException ex = new ExternalServiceException("VNPAY_DOWN", "VNPay không phản hồi", cause);
        assertThat(ex.getCode()).isEqualTo("VNPAY_DOWN");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/shared test
```

Expected: `COMPILATION ERROR`.

- [ ] **Step 3: Implement `DomainException` base**

Create `backend/modules/shared/src/main/java/com/shop/delivery/shared/exception/DomainException.java`:

```java
package com.shop.delivery.shared.exception;

public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected DomainException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
```

- [ ] **Step 4: Implement 5 concrete exceptions**

Create `NotFoundException.java`:
```java
package com.shop.delivery.shared.exception;

public class NotFoundException extends DomainException {
    public NotFoundException(String code, String message) {
        super(code, message);
    }
}
```

Create `ValidationException.java`:
```java
package com.shop.delivery.shared.exception;

public class ValidationException extends DomainException {
    public ValidationException(String code, String message) {
        super(code, message);
    }
}
```

Create `BusinessRuleException.java`:
```java
package com.shop.delivery.shared.exception;

public class BusinessRuleException extends DomainException {
    public BusinessRuleException(String code, String message) {
        super(code, message);
    }
}
```

Create `ConflictException.java`:
```java
package com.shop.delivery.shared.exception;

public class ConflictException extends DomainException {
    public ConflictException(String code, String message) {
        super(code, message);
    }
}
```

Create `ExternalServiceException.java`:
```java
package com.shop.delivery.shared.exception;

public class ExternalServiceException extends DomainException {
    public ExternalServiceException(String code, String message) {
        super(code, message);
    }
    public ExternalServiceException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
```

- [ ] **Step 5: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/shared test
```

Expected: `Tests run: 5, Failures: 0` — all pass.

- [ ] **Step 6: Commit**

```bash
git add backend/modules/shared/src/
git commit -m "feat(shared): add domain exception hierarchy with error codes"
```

---

## TASK 6: 8 Empty Module Shells

**Files:**
- Create: `backend/modules/{auth,order,delivery,payment,notification,bot,miniapp,webadmin}/pom.xml`
- Create: `backend/modules/{auth,...,webadmin}/src/main/java/com/shop/delivery/<module>/.gitkeep`

- [ ] **Step 1: Create POMs for 8 modules**

Template (`backend/modules/auth/pom.xml`):

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

    <artifactId>auth</artifactId>
    <name>auth</name>
    <description>Telegram user, role management, JWT</description>

    <dependencies>
        <dependency>
            <groupId>com.shop.delivery</groupId>
            <artifactId>shared</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Repeat for each of the 7 remaining modules (`order`, `delivery`, `payment`, `notification`, `bot`, `miniapp`, `webadmin`) — replace `<artifactId>` and `<description>`:

| Module | Description |
|---|---|
| `order` | Product, order, order_item, status_history |
| `delivery` | Delivery assignment, location_ping, shipper_profile |
| `payment` | VNPay integration, payment transactions |
| `notification` | Domain event listener — Telegram + WebSocket |
| `bot` | Telegram bot webhook, router, handlers, FSM |
| `miniapp` | REST + WebSocket for Mini App (Customer + Shipper) |
| `webadmin` | REST + WebSocket for Web Admin (Shop owner) |

- [ ] **Step 2: Create `.gitkeep` to commit empty package dirs**

```bash
cd backend
for m in auth order delivery payment notification bot miniapp webadmin; do
  mkdir -p "modules/$m/src/main/java/com/shop/delivery/$m"
  touch "modules/$m/src/main/java/com/shop/delivery/$m/.gitkeep"
done
cd ..
```

- [ ] **Step 3: Verify all modules compile**

```bash
cd backend && ./mvnw compile -DskipTests
```

Expected: `BUILD SUCCESS`, 9 modules built (shared + 8 empty).

- [ ] **Step 4: Commit**

```bash
git add backend/modules/
git commit -m "chore: add empty pom + package shells for 8 bounded contexts"
```

---

## TASK 7: `app` Boot Module + Application Class

**Files:**
- Create: `backend/app/pom.xml`
- Create: `backend/app/src/main/java/com/shop/delivery/Application.java`
- Test: `backend/app/src/test/java/com/shop/delivery/ApplicationTests.java`

- [ ] **Step 1: Write failing test**

Create `backend/app/src/test/java/com/shop/delivery/ApplicationTests.java`:

```java
package com.shop.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationTests {

    @Test
    void contextLoads() {
        // Verifies Spring context bootstraps with all configurations.
    }
}
```

- [ ] **Step 2: Write `backend/app/pom.xml`**

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
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>app</artifactId>
    <name>app</name>
    <description>Spring Boot application entry point — aggregates all modules</description>

    <dependencies>
        <!-- All internal modules -->
        <dependency><groupId>com.shop.delivery</groupId><artifactId>shared</artifactId></dependency>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>auth</artifactId></dependency>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>order</artifactId></dependency>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>delivery</artifactId></dependency>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>payment</artifactId></dependency>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>notification</artifactId></dependency>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>bot</artifactId></dependency>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>miniapp</artifactId></dependency>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>webadmin</artifactId></dependency>

        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- DB + Migration -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <!-- Logging -->
        <dependency>
            <groupId>net.logstash.logback</groupId>
            <artifactId>logstash-logback-encoder</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
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
    </dependencies>

    <build>
        <finalName>delivery-app</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.shop.delivery.Application</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Write `Application.java`**

Create `backend/app/src/main/java/com/shop/delivery/Application.java`:

```java
package com.shop.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.shop.delivery")
@EntityScan(basePackages = "com.shop.delivery")
@EnableJpaRepositories(basePackages = "com.shop.delivery")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

- [ ] **Step 4: Test will fail without config — that's expected (no DB)**

Don't run yet — Task 8 will add `application.yml`.

- [ ] **Step 5: Commit (without running tests yet)**

```bash
git add backend/app/pom.xml backend/app/src/main/java backend/app/src/test/java
git commit -m "feat(app): add Spring Boot Application class with module aggregation"
```

---

## TASK 8: Application Configuration (Profiles)

**Files:**
- Create: `backend/app/src/main/resources/application.yml`
- Create: `backend/app/src/main/resources/application-dev.yml`
- Create: `backend/app/src/main/resources/application-prod.yml`
- Create: `backend/app/src/main/resources/logback-spring.xml`

- [ ] **Step 1: Write `application.yml` (common)**

Create `backend/app/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: delivery-app
  profiles:
    default: dev
  jpa:
    open-in-view: false
    properties:
      hibernate:
        format_sql: false
        jdbc.time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false

server:
  port: 8080
  forward-headers-strategy: framework
  error:
    include-message: never
    include-binding-errors: never
    include-stacktrace: never
    include-exception: false

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  health:
    db:
      enabled: true
  info:
    env:
      enabled: true

info:
  app:
    name: ${spring.application.name}
    version: 0.1.0-SNAPSHOT
```

- [ ] **Step 2: Write `application-dev.yml`**

Create `backend/app/src/main/resources/application-dev.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/shop_delivery
    username: app
    password: app_dev_password
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    root: INFO
    com.shop.delivery: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.orm.jdbc.bind: TRACE

management:
  endpoint:
    health:
      show-details: always
```

- [ ] **Step 3: Write `application-prod.yml`**

Create `backend/app/src/main/resources/application-prod.yml`:

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

logging:
  level:
    root: INFO
    com.shop.delivery: INFO
  config: classpath:logback-spring.xml

server:
  compression:
    enabled: true
    mime-types: application/json,application/xml,text/html,text/plain
```

- [ ] **Step 4: Write `logback-spring.xml` (JSON log for prod)**

Create `backend/app/src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProfile name="dev,test">
        <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
        <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <springProfile name="prod">
        <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>traceId</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
                <includeMdcKeyName>chatId</includeMdcKeyName>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON_CONSOLE"/>
        </root>
    </springProfile>
</configuration>
```

- [ ] **Step 5: Commit**

```bash
git add backend/app/src/main/resources/
git commit -m "feat(app): add application config profiles (dev/prod) + logback"
```

---

## TASK 9: Docker Compose Dev + Flyway Baseline

**Files:**
- Create: `infra/docker-compose.dev.yml`
- Create: `infra/postgres/init.sql`
- Create: `backend/app/src/main/resources/db/migration/V1__init.sql`

- [ ] **Step 1: Write `infra/docker-compose.dev.yml`**

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: shop_delivery_postgres_dev
    restart: unless-stopped
    environment:
      POSTGRES_DB: shop_delivery
      POSTGRES_USER: app
      POSTGRES_PASSWORD: app_dev_password
      TZ: UTC
    volumes:
      - pgdata_dev:/var/lib/postgresql/data
      - ./postgres/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app -d shop_delivery"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  pgdata_dev:
```

- [ ] **Step 2: Write `infra/postgres/init.sql`**

```sql
-- Idempotent: PostgreSQL official image creates DB and USER from env vars,
-- this file is for additional setup (extensions, schemas).

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Set timezone defaults
ALTER DATABASE shop_delivery SET timezone TO 'UTC';
```

- [ ] **Step 3: Write Flyway baseline `V1__init.sql`**

Create `backend/app/src/main/resources/db/migration/V1__init.sql`:

```sql
-- Baseline migration for P0.
-- Future plans add tables: P1 (auth), P2 (order), P5 (delivery), P7 (payment).
-- This file establishes Flyway version tracking and creates a meta table.

CREATE TABLE app_meta (
    key VARCHAR(64) PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO app_meta (key, value) VALUES
    ('schema_version', 'p0-baseline'),
    ('initialized_at', NOW()::TEXT);
```

- [ ] **Step 4: Start Postgres**

```bash
docker compose -f infra/docker-compose.dev.yml up -d
```

- [ ] **Step 5: Verify Postgres is running**

```bash
docker compose -f infra/docker-compose.dev.yml ps
# Expected: shop_delivery_postgres_dev   Up (healthy)
```

```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "SELECT 1"
# Expected:  ?column?
#           ----------
#                   1
```

- [ ] **Step 6: Run backend to verify Flyway migrates**

```bash
cd backend
./mvnw -pl app -am spring-boot:run -Dspring-boot.run.profiles=dev
```

Wait until you see:
```
o.f.c.i.c.DbMigrate : Migrating schema "public" to version "1 - init"
o.f.c.i.c.DbMigrate : Successfully applied 1 migration to schema "public"
...
Started Application in X.XXX seconds
```

Then in another terminal:
```bash
curl -s http://localhost:8080/actuator/health | head
# Expected: {"status":"UP","components":{"db":{"status":"UP",...}}}
```

```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "SELECT * FROM app_meta"
# Expected: 2 rows (schema_version=p0-baseline, initialized_at=<timestamp>)
```

```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "SELECT version, description FROM flyway_schema_history"
# Expected: 1 | init
```

Stop the backend (`Ctrl+C`).

- [ ] **Step 7: Commit**

```bash
git add infra/ backend/app/src/main/resources/db/
git commit -m "feat(infra): add Docker Compose dev + Flyway baseline V1__init"
```

---

## TASK 10: Integration Test với Testcontainers

**Files:**
- Create: `backend/app/src/test/java/com/shop/delivery/support/PostgresTestContainer.java`
- Create: `backend/app/src/test/java/com/shop/delivery/HealthCheckIT.java`
- Create: `backend/app/src/test/resources/application-test.yml`

- [ ] **Step 1: Write Testcontainers base class**

Create `backend/app/src/test/java/com/shop/delivery/support/PostgresTestContainer.java`:

```java
package com.shop.delivery.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test base class providing a single shared Postgres container for all tests.
 * Extend this class to get a working DB without manual setup.
 */
@Testcontainers
public abstract class PostgresTestContainer {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("shop_delivery_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

- [ ] **Step 2: Write integration test for `/actuator/health`**

Create `backend/app/src/test/java/com/shop/delivery/HealthCheckIT.java`:

```java
package com.shop.delivery;

import com.shop.delivery.support.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthCheckIT extends PostgresTestContainer {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void healthEndpointShouldReturnUp() {
        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response = (ResponseEntity)
            restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");
    }

    @Test
    void healthEndpointShouldReportDbAsUp() {
        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response = (ResponseEntity)
            restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) response.getBody().get("components");
        assertThat(components).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) components.get("db");
        assertThat(db).isNotNull();
        assertThat(db.get("status")).isEqualTo("UP");
    }
}
```

- [ ] **Step 3: Write `application-test.yml`**

Create `backend/app/src/test/resources/application-test.yml`:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true

management:
  endpoint:
    health:
      show-details: always

logging:
  level:
    root: WARN
    com.shop.delivery: INFO
    org.testcontainers: INFO
```

- [ ] **Step 4: Run integration tests**

```bash
cd backend && ./mvnw -pl app verify
```

Expected output (look for):
```
...
[INFO] --- maven-failsafe-plugin:... (default) @ app ---
[INFO] Running com.shop.delivery.HealthCheckIT
...
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: X.XX s
[INFO] Running com.shop.delivery.ApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
...
[INFO] BUILD SUCCESS
```

First run downloads Postgres image (~150MB) — may take 1-2 minutes.

If you see `Could not find a valid Docker environment`: make sure Docker Desktop is running.

- [ ] **Step 5: Commit**

```bash
git add backend/app/src/test/
git commit -m "test(app): add HealthCheckIT with Testcontainers Postgres"
```

---

## TASK 11: GitHub Actions CI

**Files:**
- Create: `.github/workflows/backend.yml`

- [ ] **Step 1: Write CI workflow**

Create `.github/workflows/backend.yml`:

```yaml
name: Backend CI

on:
  push:
    branches: [main]
    paths:
      - 'backend/**'
      - '.github/workflows/backend.yml'
  pull_request:
    paths:
      - 'backend/**'
      - '.github/workflows/backend.yml'

jobs:
  test:
    name: Test (Java ${{ matrix.java }})
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [17]

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK ${{ matrix.java }}
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${{ matrix.java }}
          cache: maven

      - name: Build & test
        working-directory: backend
        run: ./mvnw -B verify

      - name: Upload Surefire reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: surefire-reports
          path: backend/**/target/surefire-reports/
```

- [ ] **Step 2: Verify YAML syntax locally (optional)**

```bash
# If you have actionlint installed:
actionlint .github/workflows/backend.yml
# Or just check it loads:
cat .github/workflows/backend.yml | head -5
```

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/backend.yml
git commit -m "ci: add GitHub Actions workflow for backend build/test"
```

CI sẽ chạy khi push lên `main` (sau khi push remote). Hiện chưa có remote — bước này chỉ chuẩn bị cho sau này.

---

## TASK 12: Smoke Test End-to-End (Manual Verification)

Không có file mới — đây là bước verify toàn bộ P0 hoạt động.

- [ ] **Step 1: Full clean build**

```bash
cd backend && ./mvnw clean verify
```

Expected: `BUILD SUCCESS`, tổng số test ≥ 8 (3 unit từ shared + 1 ApplicationTests + 2 HealthCheckIT, có thể thêm).

- [ ] **Step 2: Start dev environment**

```bash
docker compose -f infra/docker-compose.dev.yml up -d
cd backend && ./mvnw -pl app spring-boot:run -Dspring-boot.run.profiles=dev
```

Wait for `Started Application`.

- [ ] **Step 3: Manual health check**

In another terminal:
```bash
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
```

Expected:
```json
{
    "status": "UP",
    "components": {
        "db": {
            "status": "UP",
            "details": {
                "database": "PostgreSQL",
                "validationQuery": "isValid()"
            }
        },
        "diskSpace": { "status": "UP", ... },
        "ping": { "status": "UP" }
    }
}
```

- [ ] **Step 4: Verify Flyway state**

```bash
curl -s http://localhost:8080/actuator/info | python3 -m json.tool
# Expected: {"app":{"name":"delivery-app","version":"0.1.0-SNAPSHOT"}}
```

```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT version, description, success FROM flyway_schema_history"
# Expected: 1 | init | t
```

- [ ] **Step 5: Stop & cleanup**

```bash
# Stop backend with Ctrl+C
docker compose -f infra/docker-compose.dev.yml down
```

- [ ] **Step 6: Verify `git status` is clean**

```bash
git status
# Expected: nothing to commit, working tree clean
```

- [ ] **Step 7: View commit log for P0**

```bash
git log --oneline
```

Expected ~12 commits in P0:
1. Initial spec commit (already there)
2. `chore: bootstrap monorepo structure with placeholder READMEs`
3. `chore(backend): add Maven parent POM and wrapper`
4. `chore(shared): add pom.xml for shared module`
5. `feat(shared): add BaseEntity with createdAt/updatedAt`
6. `feat(shared): add domain exception hierarchy with error codes`
7. `chore: add empty pom + package shells for 8 bounded contexts`
8. `feat(app): add Spring Boot Application class with module aggregation`
9. `feat(app): add application config profiles (dev/prod) + logback`
10. `feat(infra): add Docker Compose dev + Flyway baseline V1__init`
11. `test(app): add HealthCheckIT with Testcontainers Postgres`
12. `ci: add GitHub Actions workflow for backend build/test`

---

## Acceptance Criteria (P0 done when ALL true)

- [x] `cd backend && ./mvnw clean verify` exits with `BUILD SUCCESS`
- [x] All unit + integration tests pass (≥ 8 tests)
- [x] `docker compose -f infra/docker-compose.dev.yml up -d` brings up Postgres healthy
- [x] `./mvnw -pl app spring-boot:run -Dspring-boot.run.profiles=dev` starts without error
- [x] `curl http://localhost:8080/actuator/health` returns `{"status":"UP",...,"components":{"db":{"status":"UP",...}}}`
- [x] `flyway_schema_history` table has row `(1, init, success=true)`
- [x] 9 Maven modules under `backend/modules/` + 1 `app` module — all compile
- [x] 11+ atomic commits, mỗi commit có thể compile được riêng (no broken intermediate states)
- [x] Git status clean — no untracked / uncommitted files

---

## Known Limitations of P0 (Sẽ giải quyết ở plan sau)

| Limitation | Plan giải quyết |
|---|---|
| Chưa có entity nào ngoài `BaseEntity` | P1 (auth) — TelegramUser, UserRole, AdminUser |
| Chưa có REST endpoint nào | P1 (auth) — /api/me skeleton; P2 (order) — full CRUD |
| Chưa có Telegram bot tích hợp | P1 (auth + bot) |
| Chưa có WebSocket | P5 (delivery flow) — chuẩn bị; P6 (Live Location) — đầy đủ |
| Chưa có Spring Security | P1 (auth — initData filter); P4 (webadmin — JWT) |
| Chưa có frontend | P3 (Mini App skeleton) |
| `docker-compose.yml` prod chưa hoàn thiện | P9 (deploy) |

---

## Troubleshooting

**`Cannot find module 'shared'` khi build:**
- Đảm bảo `./mvnw -am` được dùng (build dependent modules first) hoặc chạy full build từ `backend/` root: `./mvnw clean install`

**Testcontainers fail với `Could not find a valid Docker environment`:**
- Mac: Khởi động Docker Desktop
- Linux: `sudo systemctl start docker` + đảm bảo user trong group `docker`

**Flyway fail `Validate failed: Detected resolved migration not applied to database`:**
- Migration đã apply nhưng file đã đổi. Reset DB: `docker compose -f infra/docker-compose.dev.yml down -v` (xóa volume) → up lại.

**`Address already in use: bind` port 8080:**
- Đổi `server.port` trong `application-dev.yml` hoặc kill process: `lsof -ti:8080 | xargs kill`

**`Address already in use: bind` port 5432:**
- Có Postgres local đang chạy. Dừng nó hoặc đổi mapping port trong `docker-compose.dev.yml` (vd: `"5433:5432"`) và update `application-dev.yml` (`jdbc:postgresql://localhost:5433/...`).

---

**END OF P0 PLAN**
